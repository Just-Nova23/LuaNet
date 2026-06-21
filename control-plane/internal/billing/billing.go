package billing

import (
	"context"
	"fmt"
	"time"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/model"
	"google.golang.org/api/androidpublisher/v3"
)

type Verifier interface {
	Verify(context.Context, string) (model.PurchaseResult, error)
}

type GooglePlay struct {
	service     *androidpublisher.Service
	packageName string
	products    map[string]struct{}
}

func NewGooglePlay(ctx context.Context, packageName string) (*GooglePlay, error) {
	service, err := androidpublisher.NewService(ctx)
	if err != nil {
		return nil, err
	}
	return &GooglePlay{
		service: service, packageName: packageName,
		products: map[string]struct{}{"luanet_premium_monthly": {}, "luanet_premium_yearly": {}},
	}, nil
}

func (g *GooglePlay) Verify(ctx context.Context, token string) (model.PurchaseResult, error) {
	sub, err := g.service.Purchases.Subscriptionsv2.Get(g.packageName, token).Context(ctx).Do()
	if err != nil {
		return model.PurchaseResult{}, fmt.Errorf("verify subscription: %w", err)
	}
	var result model.PurchaseResult
	for _, item := range sub.LineItems {
		if _, allowed := g.products[item.ProductId]; !allowed {
			continue
		}
		expiry, err := time.Parse(time.RFC3339Nano, item.ExpiryTime)
		if err != nil {
			continue
		}
		if expiry.After(result.ExpiresAt) {
			result = model.PurchaseResult{ProductID: item.ProductId, ExpiresAt: expiry}
		}
	}
	if result.ProductID == "" || !result.ExpiresAt.After(time.Now()) {
		return model.PurchaseResult{}, model.ErrInvalidPurchase
	}
	return result, nil
}

type Disabled struct{}

func (Disabled) Verify(context.Context, string) (model.PurchaseResult, error) {
	return model.PurchaseResult{}, model.ErrBillingDisabled
}
