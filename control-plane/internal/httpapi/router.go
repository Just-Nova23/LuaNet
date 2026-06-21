package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/auth"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/billing"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/frp"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/service"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/store"
)

type API struct {
	service *service.Service
	auth    auth.Authenticator
	billing billing.Verifier
	store   store.Store
}

func New(s *service.Service, a auth.Authenticator, b billing.Verifier, st store.Store) http.Handler {
	api := &API{service: s, auth: a, billing: b, store: st}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	mux.Handle("POST /internal/frp-plugin", frp.New(s))
	mux.Handle("POST /v1/tunnel-holds", auth.Middleware(a, http.HandlerFunc(api.createHold)))
	mux.Handle("POST /v1/tunnel-leases", auth.Middleware(a, http.HandlerFunc(api.activateLease)))
	mux.Handle("DELETE /v1/tunnel-leases/{id}", auth.Middleware(a, http.HandlerFunc(api.releaseLease)))
	mux.Handle("GET /v1/entitlement", auth.Middleware(a, http.HandlerFunc(api.entitlement)))
	mux.Handle("POST /v1/billing/google/verify", auth.Middleware(a, http.HandlerFunc(api.verifyBilling)))
	mux.Handle("DELETE /v1/account", auth.Middleware(a, http.HandlerFunc(api.deleteAccount)))
	return securityHeaders(requestLog(mux))
}

func (a *API) createHold(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	var body struct {
		DeviceID  string `json:"device_id"`
		ProfileID string `json:"profile_id"`
	}
	if !decode(w, r, &body) {
		return
	}
	hold, err := a.service.CreateHold(r.Context(), principal.UserID, body.DeviceID, body.ProfileID)
	respond(w, hold, err)
}

func (a *API) activateLease(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	var body struct {
		HoldID string `json:"hold_id"`
	}
	if !decode(w, r, &body) {
		return
	}
	lease, err := a.service.ActivateHold(r.Context(), principal.UserID, body.HoldID)
	respond(w, lease, err)
}

func (a *API) releaseLease(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, "missing lease id", http.StatusBadRequest)
		return
	}
	if err := a.service.Release(r.Context(), principal.UserID, id); err != nil {
		respond(w, nil, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (a *API) entitlement(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	ent, err := a.service.Entitlement(r.Context(), principal.UserID)
	respond(w, ent, err)
}

func (a *API) verifyBilling(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	var body struct {
		PurchaseToken string `json:"purchase_token"`
	}
	if !decode(w, r, &body) {
		return
	}
	result, err := a.billing.Verify(r.Context(), body.PurchaseToken)
	if err != nil {
		respond(w, nil, err)
		return
	}
	ent := model.Entitlement{UserID: principal.UserID, Tier: model.TierPremium, ExpiresAt: result.ExpiresAt, UpdatedAt: time.Now().UTC()}
	if err = a.store.UpsertEntitlement(r.Context(), ent); err != nil {
		respond(w, nil, err)
		return
	}
	respond(w, ent, nil)
}

func (a *API) deleteAccount(w http.ResponseWriter, r *http.Request) {
	principal, _ := auth.Principal(r.Context())
	if err := a.service.DeleteAccount(r.Context(), principal.UserID); err != nil {
		respond(w, nil, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func decode(w http.ResponseWriter, r *http.Request, target any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(target); err != nil {
		http.Error(w, "invalid JSON body", http.StatusBadRequest)
		return false
	}
	return true
}

func respond(w http.ResponseWriter, value any, err error) {
	if err != nil {
		status := http.StatusInternalServerError
		switch {
		case errors.Is(err, model.ErrNotFound):
			status = http.StatusNotFound
		case errors.Is(err, model.ErrForbidden), errors.Is(err, model.ErrEmailUnverified):
			status = http.StatusForbidden
		case errors.Is(err, model.ErrExpired), errors.Is(err, model.ErrLimitReached), errors.Is(err, model.ErrCapacity):
			status = http.StatusConflict
		case errors.Is(err, model.ErrBillingDisabled):
			status = http.StatusServiceUnavailable
		case errors.Is(err, model.ErrInvalidPurchase):
			status = http.StatusUnprocessableEntity
		}
		http.Error(w, err.Error(), status)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(value)
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

func requestLog(next http.Handler) http.Handler { return next }
