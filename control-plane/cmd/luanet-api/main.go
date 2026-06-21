package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/auth"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/billing"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/config"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/httpapi"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/service"
	"github.com/Just-Nova23/LuaNet/control-plane/internal/store"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer stop()

	st, err := store.Open(ctx, cfg)
	if err != nil {
		slog.Error("database unavailable", "error", err)
		os.Exit(1)
	}
	defer st.Close()

	var authenticator auth.Authenticator = auth.Development{}
	if cfg.FirebaseProjectID != "" {
		a, authErr := auth.NewFirebase(ctx, cfg.FirebaseProjectID)
		if authErr != nil {
			slog.Error("firebase initialization failed", "error", authErr)
			os.Exit(1)
		}
		authenticator = a
	}

	var verifier billing.Verifier = billing.Disabled{}
	if os.Getenv("GOOGLE_APPLICATION_CREDENTIALS") != "" {
		v, billingErr := billing.NewGooglePlay(ctx, cfg.AndroidPackage)
		if billingErr != nil {
			slog.Error("billing initialization failed", "error", billingErr)
			os.Exit(1)
		}
		verifier = v
	}

	svc := service.New(st, cfg)
	server := &http.Server{
		Addr: cfg.HTTPAddr, Handler: httpapi.New(svc, authenticator, verifier, st),
		ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second,
		WriteTimeout: 15 * time.Second, IdleTimeout: 60 * time.Second,
	}
	go func() {
		slog.Info("control-plane listening", "address", cfg.HTTPAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("HTTP server failed", "error", err)
			stop()
		}
	}()
	<-ctx.Done()
	shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdown)
}
