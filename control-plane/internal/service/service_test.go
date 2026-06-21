package service

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/config"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/store"
)

type memoryStore struct {
	mu       sync.Mutex
	holds    map[string]model.Hold
	leases   map[string]model.Lease
	ent      map[string]model.Entitlement
	nextPort int
}

func newMemoryStore() *memoryStore {
	return &memoryStore{holds: map[string]model.Hold{}, leases: map[string]model.Lease{}, ent: map[string]model.Entitlement{}, nextPort: 30000}
}

func (m *memoryStore) CreateHold(_ context.Context, in store.HoldInput) (model.Hold, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	limit := 1
	if e := m.ent[in.UserID]; e.EffectiveTier(in.Now) == model.TierPremium {
		limit = 5
	}
	count := 0
	for _, lease := range m.leases {
		if lease.UserID == in.UserID {
			count++
		}
	}
	for _, hold := range m.holds {
		if hold.UserID == in.UserID {
			count++
		}
	}
	if count >= limit {
		return model.Hold{}, model.ErrLimitReached
	}
	h := model.Hold{ID: in.ID, UserID: in.UserID, DeviceID: in.DeviceID, ProfileID: in.ProfileID, Port: m.nextPort, ExpiresAt: in.ExpiresAt}
	m.nextPort++
	m.holds[h.ID] = h
	return h, nil
}

func (m *memoryStore) ActivateHold(_ context.Context, in store.ActivateInput) (model.Lease, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	h, ok := m.holds[in.HoldID]
	if !ok {
		return model.Lease{}, model.ErrNotFound
	}
	if h.UserID != in.UserID {
		return model.Lease{}, model.ErrForbidden
	}
	if !h.ExpiresAt.After(in.Now) {
		return model.Lease{}, model.ErrExpired
	}
	tier := m.ent[in.UserID].EffectiveTier(in.Now)
	ttl := 4 * time.Hour
	if tier == model.TierPremium {
		ttl = 24 * time.Hour
	}
	l := model.Lease{ID: h.ID, UserID: h.UserID, DeviceID: h.DeviceID, ProfileID: h.ProfileID, Port: h.Port, Tier: tier, ExpiresAt: in.Now.Add(ttl), SessionHash: in.SessionHash}
	delete(m.holds, h.ID)
	m.leases[l.ID] = l
	return l, nil
}

func (m *memoryStore) ReleaseLease(_ context.Context, id, uid string, _ time.Time) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	l, ok := m.leases[id]
	if !ok || l.UserID != uid {
		return model.ErrNotFound
	}
	delete(m.leases, id)
	return nil
}
func (m *memoryStore) GetEntitlement(_ context.Context, uid string, now time.Time) (model.Entitlement, error) {
	e := m.ent[uid]
	if e.UserID == "" {
		e = model.Entitlement{UserID: uid, Tier: model.TierFree, UpdatedAt: now}
	}
	return e, nil
}
func (m *memoryStore) UpsertEntitlement(_ context.Context, e model.Entitlement) error {
	m.ent[e.UserID] = e
	return nil
}
func (m *memoryStore) ValidateSession(_ context.Context, id, hash string, now time.Time) (model.Lease, error) {
	l, ok := m.leases[id]
	if !ok || l.SessionHash != hash {
		return model.Lease{}, model.ErrInvalidSession
	}
	if !l.ExpiresAt.After(now) {
		return model.Lease{}, model.ErrExpired
	}
	return l, nil
}
func (m *memoryStore) DeleteAccount(_ context.Context, uid string) error {
	delete(m.ent, uid)
	for id, l := range m.leases {
		if l.UserID == uid {
			delete(m.leases, id)
		}
	}
	return nil
}
func (m *memoryStore) Close() error { return nil }

func testConfig() config.Config {
	return config.Config{PublicHost: "play.test", FRPServerHost: "tunnel.test", FRPServerPort: 7443, HoldTTL: 2 * time.Minute, FreeLeaseTTL: 4 * time.Hour, PremiumLeaseTTL: 24 * time.Hour, GraceTTL: 10 * time.Minute, FreeLimit: 1, PremiumLimit: 5}
}

func TestFreeUserCanOnlyHoldOneTunnel(t *testing.T) {
	now := time.Date(2026, 6, 21, 12, 0, 0, 0, time.UTC)
	s := NewWithClock(newMemoryStore(), testConfig(), func() time.Time { return now })
	if _, err := s.CreateHold(context.Background(), "user", "phone", "one"); err != nil {
		t.Fatal(err)
	}
	if _, err := s.CreateHold(context.Background(), "user", "phone", "two"); err != model.ErrLimitReached {
		t.Fatalf("expected limit, got %v", err)
	}
}

func TestPremiumLeaseAndSessionToken(t *testing.T) {
	now := time.Date(2026, 6, 21, 12, 0, 0, 0, time.UTC)
	st := newMemoryStore()
	st.ent["user"] = model.Entitlement{UserID: "user", Tier: model.TierPremium, ExpiresAt: now.Add(30 * 24 * time.Hour)}
	s := NewWithClock(st, testConfig(), func() time.Time { return now })
	h, err := s.CreateHold(context.Background(), "user", "phone", "profile")
	if err != nil {
		t.Fatal(err)
	}
	l, err := s.ActivateHold(context.Background(), "user", h.ID)
	if err != nil {
		t.Fatal(err)
	}
	if l.Tier != model.TierPremium || l.ExpiresAt.Sub(now) != 24*time.Hour {
		t.Fatalf("unexpected lease: %+v", l)
	}
	if _, err = s.ValidateFRPSession(context.Background(), l.ID, l.SessionToken); err != nil {
		t.Fatalf("token rejected: %v", err)
	}
	if _, err = s.ValidateFRPSession(context.Background(), l.ID, "wrong"); err != model.ErrInvalidSession {
		t.Fatalf("wrong token accepted: %v", err)
	}
}
