package store

import (
	"context"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
)

type HoldInput struct {
	ID        string
	UserID    string
	DeviceID  string
	ProfileID string
	Now       time.Time
	ExpiresAt time.Time
}

type ActivateInput struct {
	HoldID      string
	UserID      string
	SessionHash string
	Now         time.Time
}

type Store interface {
	CreateHold(context.Context, HoldInput) (model.Hold, error)
	ActivateHold(context.Context, ActivateInput) (model.Lease, error)
	ReleaseLease(context.Context, string, string, time.Time) error
	GetEntitlement(context.Context, string, time.Time) (model.Entitlement, error)
	UpsertEntitlement(context.Context, model.Entitlement) error
	ValidateSession(context.Context, string, string, time.Time) (model.Lease, error)
	DeleteAccount(context.Context, string) error
	Close() error
}
