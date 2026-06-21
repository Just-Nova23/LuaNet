package model

import (
	"errors"
	"time"
)

type Tier string

const (
	TierFree    Tier = "free"
	TierPremium Tier = "premium"
)

type AllocationState string

const (
	StateHold   AllocationState = "hold"
	StateActive AllocationState = "active"
	StateGrace  AllocationState = "grace"
)

var (
	ErrCapacity        = errors.New("tunnel capacity reached")
	ErrLimitReached    = errors.New("account tunnel limit reached")
	ErrNotFound        = errors.New("resource not found")
	ErrForbidden       = errors.New("resource does not belong to user")
	ErrExpired         = errors.New("resource expired")
	ErrInvalidSession  = errors.New("invalid tunnel session")
	ErrBillingDisabled = errors.New("billing verifier is not configured")
	ErrInvalidPurchase = errors.New("invalid purchase")
	ErrEmailUnverified = errors.New("verified email required")
)

type Principal struct {
	UserID        string
	Email         string
	EmailVerified bool
}

type Hold struct {
	ID        string    `json:"id"`
	UserID    string    `json:"-"`
	DeviceID  string    `json:"-"`
	ProfileID string    `json:"-"`
	Port      int       `json:"port"`
	ExpiresAt time.Time `json:"expires_at"`
}

type Lease struct {
	ID          string    `json:"id"`
	UserID      string    `json:"-"`
	DeviceID    string    `json:"-"`
	ProfileID   string    `json:"-"`
	Tier        Tier      `json:"tier"`
	Port        int       `json:"public_port"`
	ExpiresAt   time.Time `json:"expires_at"`
	SessionHash string    `json:"-"`
}

type Entitlement struct {
	UserID    string    `json:"-"`
	Tier      Tier      `json:"tier"`
	ExpiresAt time.Time `json:"expires_at,omitempty"`
	UpdatedAt time.Time `json:"updated_at"`
}

func (e Entitlement) EffectiveTier(now time.Time) Tier {
	if e.Tier == TierPremium && e.ExpiresAt.After(now) {
		return TierPremium
	}
	return TierFree
}

type PurchaseResult struct {
	ProductID string
	ExpiresAt time.Time
}
