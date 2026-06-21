package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Environment       string
	HTTPAddr          string
	DatabaseURL       string
	PublicHost        string
	FRPServerHost     string
	FRPServerPort     int
	PortMin           int
	PortMax           int
	Capacity          int
	FirebaseProjectID string
	AndroidPackage    string
	HoldTTL           time.Duration
	FreeLeaseTTL      time.Duration
	PremiumLeaseTTL   time.Duration
	GraceTTL          time.Duration
	FreeLimit         int
	PremiumLimit      int
}

func Load() (Config, error) {
	c := Config{
		Environment:       env("ENVIRONMENT", "development"),
		HTTPAddr:          env("HTTP_ADDR", ":8080"),
		DatabaseURL:       env("DATABASE_URL", "postgres://luanet:luanet@localhost:5432/luanet?sslmode=disable"),
		PublicHost:        env("PUBLIC_HOST", "play.luanet.novaxhosting.com"),
		FRPServerHost:     env("FRP_SERVER_HOST", "tunnel.luanet.novaxhosting.com"),
		FirebaseProjectID: os.Getenv("FIREBASE_PROJECT_ID"),
		AndroidPackage:    env("ANDROID_PACKAGE_NAME", "net.novax.luanet"),
		HoldTTL:           2 * time.Minute,
		FreeLeaseTTL:      4 * time.Hour,
		PremiumLeaseTTL:   24 * time.Hour,
		GraceTTL:          10 * time.Minute,
		FreeLimit:         1,
		PremiumLimit:      5,
	}
	var err error
	if c.FRPServerPort, err = envInt("FRP_SERVER_PORT", 7443); err != nil {
		return Config{}, err
	}
	if c.PortMin, err = envInt("FRP_PORT_MIN", 30000); err != nil {
		return Config{}, err
	}
	if c.PortMax, err = envInt("FRP_PORT_MAX", 30127); err != nil {
		return Config{}, err
	}
	if c.Capacity, err = envInt("FRP_CAPACITY", 100); err != nil {
		return Config{}, err
	}
	if c.PortMin < 1024 || c.PortMax < c.PortMin || c.Capacity < 1 || c.Capacity > c.PortMax-c.PortMin+1 {
		return Config{}, fmt.Errorf("invalid FRP port range or capacity")
	}
	if c.Environment == "production" && c.FirebaseProjectID == "" {
		return Config{}, fmt.Errorf("FIREBASE_PROJECT_ID is required in production")
	}
	return c, nil
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func envInt(name string, fallback int) (int, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return parsed, nil
}
