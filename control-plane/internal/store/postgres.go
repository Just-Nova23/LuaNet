package store

import (
	"context"
	"crypto/subtle"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/config"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	_ "github.com/jackc/pgx/v5/stdlib"
)

const allocationLock int64 = 0x4c55414e4554

type Postgres struct {
	db  *sql.DB
	cfg config.Config
}

func Open(ctx context.Context, cfg config.Config) (*Postgres, error) {
	db, err := sql.Open("pgx", cfg.DatabaseURL)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(20)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("connect postgres: %w", err)
	}
	return &Postgres{db: db, cfg: cfg}, nil
}

func (p *Postgres) Close() error { return p.db.Close() }

func (p *Postgres) CreateHold(ctx context.Context, in HoldInput) (model.Hold, error) {
	tx, err := p.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return model.Hold{}, err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, `SELECT pg_advisory_xact_lock($1)`, allocationLock); err != nil {
		return model.Hold{}, err
	}
	if err = p.cleanup(ctx, tx, in.Now); err != nil {
		return model.Hold{}, err
	}

	tier, err := effectiveTier(ctx, tx, in.UserID, in.Now)
	if err != nil {
		return model.Hold{}, err
	}
	limit := p.cfg.FreeLimit
	if tier == model.TierPremium {
		limit = p.cfg.PremiumLimit
	}
	var active int
	if err = tx.QueryRowContext(ctx, `
		SELECT count(*) FROM tunnel_allocations
		WHERE user_id=$1 AND state IN ('hold','active')`, in.UserID).Scan(&active); err != nil {
		return model.Hold{}, err
	}
	if active >= limit {
		return model.Hold{}, model.ErrLimitReached
	}
	var total int
	if err = tx.QueryRowContext(ctx, `SELECT count(*) FROM tunnel_allocations WHERE state IN ('hold','active')`).Scan(&total); err != nil {
		return model.Hold{}, err
	}
	if total >= p.cfg.Capacity {
		return model.Hold{}, model.ErrCapacity
	}

	var port int
	var reusedID string
	err = tx.QueryRowContext(ctx, `
		SELECT id, port FROM tunnel_allocations
		WHERE user_id=$1 AND device_id=$2 AND profile_id=$3 AND state='grace' AND grace_expires_at>$4
		ORDER BY grace_expires_at DESC LIMIT 1`, in.UserID, in.DeviceID, in.ProfileID, in.Now).Scan(&reusedID, &port)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return model.Hold{}, err
	}
	if reusedID != "" {
		_, err = tx.ExecContext(ctx, `
			UPDATE tunnel_allocations SET id=$1, state='hold', hold_expires_at=$2,
			lease_expires_at=NULL, grace_expires_at=NULL, session_hash=NULL, updated_at=$3
			WHERE id=$4`, in.ID, in.ExpiresAt, in.Now, reusedID)
	} else {
		err = tx.QueryRowContext(ctx, `
			SELECT candidate.port FROM generate_series($1::int,$2::int) AS candidate(port)
			WHERE NOT EXISTS (SELECT 1 FROM tunnel_allocations a WHERE a.port=candidate.port)
			ORDER BY candidate.port LIMIT 1`, p.cfg.PortMin, p.cfg.PortMax).Scan(&port)
		if errors.Is(err, sql.ErrNoRows) {
			return model.Hold{}, model.ErrCapacity
		}
		if err == nil {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO tunnel_allocations
				(id,user_id,device_id,profile_id,port,state,hold_expires_at,created_at,updated_at)
				VALUES($1,$2,$3,$4,$5,'hold',$6,$7,$7)`,
				in.ID, in.UserID, in.DeviceID, in.ProfileID, port, in.ExpiresAt, in.Now)
		}
	}
	if err != nil {
		return model.Hold{}, err
	}
	if err = tx.Commit(); err != nil {
		return model.Hold{}, err
	}
	return model.Hold{ID: in.ID, UserID: in.UserID, DeviceID: in.DeviceID, ProfileID: in.ProfileID, Port: port, ExpiresAt: in.ExpiresAt}, nil
}

func (p *Postgres) ActivateHold(ctx context.Context, in ActivateInput) (model.Lease, error) {
	tx, err := p.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return model.Lease{}, err
	}
	defer tx.Rollback()
	var lease model.Lease
	var state model.AllocationState
	var holdExpiry time.Time
	err = tx.QueryRowContext(ctx, `
		SELECT id,user_id,device_id,profile_id,port,state,hold_expires_at
		FROM tunnel_allocations WHERE id=$1 FOR UPDATE`, in.HoldID).
		Scan(&lease.ID, &lease.UserID, &lease.DeviceID, &lease.ProfileID, &lease.Port, &state, &holdExpiry)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Lease{}, model.ErrNotFound
	}
	if err != nil {
		return model.Lease{}, err
	}
	if lease.UserID != in.UserID {
		return model.Lease{}, model.ErrForbidden
	}
	if state != model.StateHold || !holdExpiry.After(in.Now) {
		return model.Lease{}, model.ErrExpired
	}
	lease.Tier, err = effectiveTier(ctx, tx, in.UserID, in.Now)
	if err != nil {
		return model.Lease{}, err
	}
	ttl := p.cfg.FreeLeaseTTL
	if lease.Tier == model.TierPremium {
		ttl = p.cfg.PremiumLeaseTTL
	}
	lease.ExpiresAt = in.Now.Add(ttl)
	lease.SessionHash = in.SessionHash
	_, err = tx.ExecContext(ctx, `
		UPDATE tunnel_allocations SET state='active', hold_expires_at=NULL,
		lease_expires_at=$1, session_hash=$2, updated_at=$3 WHERE id=$4`,
		lease.ExpiresAt, in.SessionHash, in.Now, lease.ID)
	if err != nil {
		return model.Lease{}, err
	}
	if err = tx.Commit(); err != nil {
		return model.Lease{}, err
	}
	return lease, nil
}

func (p *Postgres) ReleaseLease(ctx context.Context, id, userID string, now time.Time) error {
	result, err := p.db.ExecContext(ctx, `
		UPDATE tunnel_allocations SET state='grace', session_hash=NULL,
		lease_expires_at=NULL, grace_expires_at=$1, updated_at=$2
		WHERE id=$3 AND user_id=$4 AND state='active'`, now.Add(p.cfg.GraceTTL), now, id, userID)
	if err != nil {
		return err
	}
	rows, _ := result.RowsAffected()
	if rows == 0 {
		return model.ErrNotFound
	}
	return nil
}

func (p *Postgres) GetEntitlement(ctx context.Context, userID string, now time.Time) (model.Entitlement, error) {
	var ent model.Entitlement
	err := p.db.QueryRowContext(ctx, `SELECT user_id,tier,expires_at,updated_at FROM entitlements WHERE user_id=$1`, userID).
		Scan(&ent.UserID, &ent.Tier, &ent.ExpiresAt, &ent.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Entitlement{UserID: userID, Tier: model.TierFree, UpdatedAt: now}, nil
	}
	if err != nil {
		return model.Entitlement{}, err
	}
	if ent.EffectiveTier(now) == model.TierFree {
		ent.Tier = model.TierFree
	}
	return ent, nil
}

func (p *Postgres) UpsertEntitlement(ctx context.Context, ent model.Entitlement) error {
	_, err := p.db.ExecContext(ctx, `
		INSERT INTO entitlements(user_id,tier,expires_at,updated_at) VALUES($1,$2,$3,$4)
		ON CONFLICT(user_id) DO UPDATE SET tier=excluded.tier, expires_at=excluded.expires_at,
		updated_at=excluded.updated_at`, ent.UserID, ent.Tier, ent.ExpiresAt, ent.UpdatedAt)
	return err
}

func (p *Postgres) ValidateSession(ctx context.Context, id, suppliedHash string, now time.Time) (model.Lease, error) {
	tx, err := p.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return model.Lease{}, err
	}
	defer tx.Rollback()
	var lease model.Lease
	var storedHash string
	err = tx.QueryRowContext(ctx, `
		SELECT id,user_id,device_id,profile_id,port,lease_expires_at,session_hash
		FROM tunnel_allocations WHERE id=$1 AND state='active' FOR UPDATE`, id).
		Scan(&lease.ID, &lease.UserID, &lease.DeviceID, &lease.ProfileID, &lease.Port, &lease.ExpiresAt, &storedHash)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Lease{}, model.ErrInvalidSession
	}
	if err != nil {
		return model.Lease{}, err
	}
	if subtle.ConstantTimeCompare([]byte(storedHash), []byte(suppliedHash)) != 1 {
		return model.Lease{}, model.ErrInvalidSession
	}
	lease.Tier, err = effectiveTier(ctx, tx, lease.UserID, now)
	if err != nil {
		return model.Lease{}, err
	}
	if lease.Tier == model.TierFree {
		var retainedID string
		err = tx.QueryRowContext(ctx, `
			SELECT id FROM tunnel_allocations
			WHERE user_id=$1 AND state='active'
			ORDER BY created_at,id LIMIT 1`, lease.UserID).Scan(&retainedID)
		if err != nil {
			return model.Lease{}, err
		}
		if retainedID != lease.ID {
			return model.Lease{}, model.ErrLimitReached
		}
	}
	if !lease.ExpiresAt.After(now) {
		return model.Lease{}, model.ErrExpired
	}
	if lease.Tier == model.TierPremium && lease.ExpiresAt.Before(now.Add(12*time.Hour)) {
		lease.ExpiresAt = now.Add(p.cfg.PremiumLeaseTTL)
		if _, err = tx.ExecContext(ctx, `UPDATE tunnel_allocations SET lease_expires_at=$1,updated_at=$2 WHERE id=$3`, lease.ExpiresAt, now, lease.ID); err != nil {
			return model.Lease{}, err
		}
	}
	if err = tx.Commit(); err != nil {
		return model.Lease{}, err
	}
	return lease, nil
}

func (p *Postgres) DeleteAccount(ctx context.Context, userID string) error {
	tx, err := p.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, `DELETE FROM tunnel_allocations WHERE user_id=$1`, userID); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM entitlements WHERE user_id=$1`, userID); err != nil {
		return err
	}
	return tx.Commit()
}

func (p *Postgres) cleanup(ctx context.Context, tx *sql.Tx, now time.Time) error {
	_, err := tx.ExecContext(ctx, `
		UPDATE tunnel_allocations SET state='grace',session_hash=NULL,lease_expires_at=NULL,
		grace_expires_at=$1,updated_at=$2 WHERE state='active' AND lease_expires_at<=$2`, now.Add(p.cfg.GraceTTL), now)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, `
		DELETE FROM tunnel_allocations
		WHERE (state='hold' AND hold_expires_at<=$1) OR (state='grace' AND grace_expires_at<=$1)`, now)
	return err
}

func effectiveTier(ctx context.Context, q interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}, userID string, now time.Time) (model.Tier, error) {
	var tier model.Tier
	var expiry time.Time
	err := q.QueryRowContext(ctx, `SELECT tier,expires_at FROM entitlements WHERE user_id=$1`, userID).Scan(&tier, &expiry)
	if errors.Is(err, sql.ErrNoRows) {
		return model.TierFree, nil
	}
	if err != nil {
		return "", err
	}
	if tier == model.TierPremium && expiry.After(now) {
		return tier, nil
	}
	return model.TierFree, nil
}
