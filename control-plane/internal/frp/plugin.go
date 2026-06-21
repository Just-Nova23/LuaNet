package frp

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/Just-Nova23/LuaNet/control-plane/internal/service"
)

type Plugin struct{ service *service.Service }

func New(s *service.Service) *Plugin { return &Plugin{service: s} }

type request struct {
	Content map[string]any `json:"content"`
}

type response struct {
	Reject       bool           `json:"reject"`
	RejectReason string         `json:"reject_reason,omitempty"`
	Unchange     bool           `json:"unchange"`
	Content      map[string]any `json:"content,omitempty"`
}

func (p *Plugin) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	var req request
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)).Decode(&req); err != nil {
		write(w, response{Reject: true, RejectReason: "invalid request", Unchange: true})
		return
	}
	op := r.URL.Query().Get("op")
	if op == "" {
		write(w, response{Reject: true, RejectReason: "missing operation", Unchange: true})
		return
	}
	id, token := credentials(req.Content)
	lease, err := p.service.ValidateFRPSession(r.Context(), id, token)
	if err != nil {
		write(w, response{Reject: true, RejectReason: "invalid or expired session", Unchange: true})
		return
	}
	if op == "NewProxy" {
		proxy := nestedMap(req.Content, "proxy_conf")
		if proxy == nil {
			proxy = nestedMap(req.Content, "proxyConf")
		}
		if proxy == nil {
			proxy = req.Content
		}
		proxyType, _ := proxy["proxy_type"].(string)
		if proxyType == "" {
			proxyType, _ = proxy["type"].(string)
		}
		remotePort := intValue(proxy, "remote_port", "remotePort")
		if strings.ToLower(proxyType) != "udp" || remotePort != lease.Port {
			write(w, response{Reject: true, RejectReason: fmt.Sprintf("only assigned UDP port %d is allowed", lease.Port), Unchange: true})
			return
		}
		// FRPS applies this limit, even if a modified client omits or raises it.
		req.Content["bandwidth_limit"] = "10MB"
		req.Content["bandwidth_limit_mode"] = "server"
		write(w, response{Reject: false, Unchange: false, Content: req.Content})
		return
	}
	switch op {
	case "Login", "NewProxy", "Ping", "NewWorkConn", "CloseProxy":
		write(w, response{Reject: false, Unchange: true})
	default:
		write(w, response{Reject: true, RejectReason: "operation not allowed", Unchange: true})
	}
}

func credentials(content map[string]any) (string, string) {
	var id string
	user := nestedMap(content, "user")
	if user != nil {
		id, _ = user["user"].(string)
	}
	if id == "" {
		id, _ = content["user"].(string)
	}
	metas := nestedMap(user, "metas")
	if metas == nil {
		metas = nestedMap(content, "metas")
	}
	token, _ := metas["session_token"].(string)
	if token == "" {
		token, _ = metas["sessionToken"].(string)
	}
	return id, token
}

func nestedMap(m map[string]any, key string) map[string]any {
	value, _ := m[key].(map[string]any)
	return value
}

func intValue(m map[string]any, keys ...string) int {
	for _, key := range keys {
		switch value := m[key].(type) {
		case float64:
			return int(value)
		case json.Number:
			v, _ := value.Int64()
			return int(v)
		}
	}
	return 0
}

func write(w http.ResponseWriter, value response) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(value)
}
