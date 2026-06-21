package frp

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/config"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/service"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/store"
)

type sessionStore struct{ lease model.Lease }

func (s *sessionStore) CreateHold(context.Context, store.HoldInput) (model.Hold, error) {
	return model.Hold{}, nil
}
func (s *sessionStore) ActivateHold(context.Context, store.ActivateInput) (model.Lease, error) {
	return model.Lease{}, nil
}
func (s *sessionStore) ReleaseLease(context.Context, string, string, time.Time) error { return nil }
func (s *sessionStore) GetEntitlement(context.Context, string, time.Time) (model.Entitlement, error) {
	return model.Entitlement{}, nil
}
func (s *sessionStore) UpsertEntitlement(context.Context, model.Entitlement) error { return nil }
func (s *sessionStore) ValidateSession(_ context.Context, id, hash string, _ time.Time) (model.Lease, error) {
	if id != s.lease.ID || hash != s.lease.SessionHash {
		return model.Lease{}, model.ErrInvalidSession
	}
	return s.lease, nil
}
func (s *sessionStore) DeleteAccount(context.Context, string) error { return nil }
func (s *sessionStore) Close() error                                { return nil }

func TestPluginAcceptsAssignedUDPProxy(t *testing.T) {
	token := "secret"
	st := &sessionStore{lease: model.Lease{ID: "lease", Port: 30042, SessionHash: service.HashSessionToken(token)}}
	svc := service.New(st, config.Config{})
	body := map[string]any{"content": map[string]any{
		"user":        map[string]any{"user": "lease", "metas": map[string]any{"session_token": token}},
		"proxy_type":  "udp",
		"remote_port": 30042,
	}}
	result := callPlugin(t, New(svc), "NewProxy", body)
	if result.Reject {
		t.Fatalf("valid proxy rejected: %+v", result)
	}
	if result.Unchange || result.Content["bandwidth_limit"] != "10MB" || result.Content["bandwidth_limit_mode"] != "server" {
		t.Fatalf("server-side bandwidth limit not enforced: %+v", result)
	}
}

func TestPluginRejectsDifferentPort(t *testing.T) {
	token := "secret"
	st := &sessionStore{lease: model.Lease{ID: "lease", Port: 30042, SessionHash: service.HashSessionToken(token)}}
	svc := service.New(st, config.Config{})
	body := map[string]any{"content": map[string]any{
		"user":        map[string]any{"user": "lease", "metas": map[string]any{"session_token": token}},
		"proxy_type":  "udp",
		"remote_port": 30043,
	}}
	result := callPlugin(t, New(svc), "NewProxy", body)
	if !result.Reject {
		t.Fatal("proxy using an unassigned port was accepted")
	}
}

func TestPluginUnderstandsLoginWireShape(t *testing.T) {
	token := "secret"
	st := &sessionStore{lease: model.Lease{ID: "lease", Port: 30042, SessionHash: service.HashSessionToken(token)}}
	svc := service.New(st, config.Config{})
	body := map[string]any{"content": map[string]any{
		"user": "lease", "metas": map[string]any{"session_token": token},
	}}
	result := callPlugin(t, New(svc), "Login", body)
	if result.Reject {
		t.Fatalf("valid login rejected: %+v", result)
	}
}

func callPlugin(t *testing.T, handler http.Handler, op string, body any) response {
	t.Helper()
	encoded, err := json.Marshal(body)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/handler?op="+op, bytes.NewReader(encoded))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	var result response
	if err := json.Unmarshal(rec.Body.Bytes(), &result); err != nil {
		t.Fatal(err)
	}
	return result
}
