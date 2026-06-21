package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/config"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/store"
)

type Clock func() time.Time

type Service struct {
	store store.Store
	cfg   config.Config
	now   Clock
}

func New(s store.Store, cfg config.Config) *Service {
	return &Service{store: s, cfg: cfg, now: time.Now}
}

func NewWithClock(s store.Store, cfg config.Config, now Clock) *Service {
	return &Service{store: s, cfg: cfg, now: now}
}

func (s *Service) CreateHold(ctx context.Context, userID, deviceID, profileID string) (model.Hold, error) {
	if userID == "" || deviceID == "" || profileID == "" {
		return model.Hold{}, fmt.Errorf("user_id, device_id and profile_id are required")
	}
	now := s.now().UTC()
	id, err := randomID(16)
	if err != nil {
		return model.Hold{}, err
	}
	return s.store.CreateHold(ctx, store.HoldInput{
		ID: id, UserID: userID, DeviceID: deviceID, ProfileID: profileID,
		Now: now, ExpiresAt: now.Add(s.cfg.HoldTTL),
	})
}

type ActivatedLease struct {
	model.Lease
	PublicHost    string `json:"public_host"`
	FRPServerHost string `json:"frp_server_host"`
	FRPServerPort int    `json:"frp_server_port"`
	SessionToken  string `json:"session_token"`
}

func (s *Service) ActivateHold(ctx context.Context, userID, holdID string) (ActivatedLease, error) {
	token, err := randomID(32)
	if err != nil {
		return ActivatedLease{}, err
	}
	digest := sha256.Sum256([]byte(token))
	lease, err := s.store.ActivateHold(ctx, store.ActivateInput{
		HoldID: holdID, UserID: userID, SessionHash: hex.EncodeToString(digest[:]), Now: s.now().UTC(),
	})
	if err != nil {
		return ActivatedLease{}, err
	}
	return ActivatedLease{
		Lease: lease, PublicHost: s.cfg.PublicHost, FRPServerHost: s.cfg.FRPServerHost,
		FRPServerPort: s.cfg.FRPServerPort, SessionToken: token,
	}, nil
}

func (s *Service) Release(ctx context.Context, userID, id string) error {
	return s.store.ReleaseLease(ctx, id, userID, s.now().UTC())
}

func (s *Service) Entitlement(ctx context.Context, userID string) (model.Entitlement, error) {
	return s.store.GetEntitlement(ctx, userID, s.now().UTC())
}

func (s *Service) DeleteAccount(ctx context.Context, userID string) error {
	return s.store.DeleteAccount(ctx, userID)
}

func HashSessionToken(token string) string {
	digest := sha256.Sum256([]byte(token))
	return hex.EncodeToString(digest[:])
}

func (s *Service) ValidateFRPSession(ctx context.Context, id, token string) (model.Lease, error) {
	if id == "" || token == "" {
		return model.Lease{}, model.ErrInvalidSession
	}
	return s.store.ValidateSession(ctx, id, HashSessionToken(token), s.now().UTC())
}

func randomID(size int) (string, error) {
	b := make([]byte, size)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}
