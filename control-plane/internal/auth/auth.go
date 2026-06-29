package auth

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"

	firebase "firebase.google.com/go/v4"
	firebaseauth "firebase.google.com/go/v4/auth"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
)

type Authenticator interface {
	Authenticate(context.Context, string) (model.Principal, error)
	DeleteUser(context.Context, string) error
}

func (f *Firebase) DeleteUser(ctx context.Context, userID string) error {
	return f.client.DeleteUser(ctx, userID)
}

type Firebase struct{ client *firebaseauth.Client }

func NewFirebase(ctx context.Context, projectID string) (*Firebase, error) {
	app, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: projectID})
	if err != nil {
		return nil, err
	}
	client, err := app.Auth(ctx)
	if err != nil {
		return nil, err
	}
	return &Firebase{client: client}, nil
}

func (f *Firebase) Authenticate(ctx context.Context, bearer string) (model.Principal, error) {
	token, err := f.client.VerifyIDTokenAndCheckRevoked(ctx, bearer)
	if err != nil {
		return model.Principal{}, err
	}
	p := model.Principal{UserID: token.UID}
	if email, ok := token.Claims["email"].(string); ok {
		p.Email = email
	}
	if verified, ok := token.Claims["email_verified"].(bool); ok {
		p.EmailVerified = verified
	}
	if token.Firebase.SignInProvider == "github.com" || token.Firebase.SignInProvider == "google.com" {
		p.EmailVerified = true
	}
	return p, nil
}

type Development struct{}

func (Development) Authenticate(_ context.Context, bearer string) (model.Principal, error) {
	if !strings.HasPrefix(bearer, "dev:") || len(bearer) <= 4 {
		return model.Principal{}, errors.New("expected development token dev:<uid>")
	}
	return model.Principal{UserID: strings.TrimPrefix(bearer, "dev:"), EmailVerified: true}, nil
}

func (Development) DeleteUser(context.Context, string) error { return nil }

func Middleware(a Authenticator, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header := r.Header.Get("Authorization")
		if !strings.HasPrefix(header, "Bearer ") {
			http.Error(w, "missing bearer token", http.StatusUnauthorized)
			return
		}
		principal, err := a.Authenticate(r.Context(), strings.TrimPrefix(header, "Bearer "))
		if err != nil {
			http.Error(w, "invalid bearer token", http.StatusUnauthorized)
			return
		}
		if !principal.EmailVerified {
			http.Error(w, model.ErrEmailUnverified.Error(), http.StatusForbidden)
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), principalKey{}, principal)))
	})
}

type principalKey struct{}

func Principal(ctx context.Context) (model.Principal, error) {
	p, ok := ctx.Value(principalKey{}).(model.Principal)
	if !ok || p.UserID == "" {
		return model.Principal{}, fmt.Errorf("principal missing from context")
	}
	return p, nil
}
