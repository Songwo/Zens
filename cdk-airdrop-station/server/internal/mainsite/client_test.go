package mainsite

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
)

func newTestClient(baseURL, secret string) *Client {
	return New(baseURL, "cdk-airdrop", secret, slog.Default())
}

func TestClientDisabledWithoutSecret(t *testing.T) {
	c := newTestClient("http://127.0.0.1:1", "")
	if c.Enabled() {
		t.Fatal("未配置密钥时 Enabled() 应为 false")
	}
	if err := c.ReportEvent(SubsiteEvent{EventID: "cdk:claim:1", EventType: "cdk.claim.success"}); err == nil {
		t.Fatal("未启用时 ReportEvent 应返回错误,不应静默成功")
	}
}

func TestReportEventSendsSignedHeaders(t *testing.T) {
	var calls atomic.Int32
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		for _, h := range []string{"X-Service-Id", "X-Service-Timestamp", "X-Service-Nonce", "X-Service-Signature"} {
			if r.Header.Get(h) == "" {
				t.Errorf("缺少签名头 %s", h)
			}
		}
		if r.Header.Get("X-Service-Id") != "cdk-airdrop" {
			t.Errorf("X-Service-Id 应为 cdk-airdrop,got %s", r.Header.Get("X-Service-Id"))
		}
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"code":2000,"message":"success","data":null}`))
	}))
	defer srv.Close()

	c := newTestClient(srv.URL, "test-secret")
	if !c.Enabled() {
		t.Fatal("配置了密钥应 Enabled()==true")
	}
	err := c.ReportEvent(SubsiteEvent{
		EventID:    "cdk:claim:abc123",
		EventType:  "cdk.claim.success",
		UserID:     "u-1",
		Title:      "CDK 领取成功",
		Content:    "test",
		NotifyUser: true,
	})
	if err != nil {
		t.Fatalf("ReportEvent 应成功,got %v", err)
	}
	if calls.Load() != 1 {
		t.Fatalf("应恰好调一次,got %d", calls.Load())
	}
	if gotBody["source"] != "cdk-airdrop" {
		t.Fatalf("source 应自动填充为 serviceId,got %v", gotBody["source"])
	}
}

func TestReportEventRetriesOn5xxNotOn4xx(t *testing.T) {
	var calls atomic.Int32
	srv5xx := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv5xx.Close()
	c := newTestClient(srv5xx.URL, "test-secret")
	if err := c.ReportEvent(SubsiteEvent{EventID: "cdk:claim:x", EventType: "cdk.claim.success"}); err == nil {
		t.Fatal("5xx 应最终返回错误")
	}
	if calls.Load() != 3 {
		t.Fatalf("5xx 应重试到 3 次,got %d", calls.Load())
	}

	calls.Store(0)
	srv4xx := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusBadRequest)
	}))
	defer srv4xx.Close()
	c2 := newTestClient(srv4xx.URL, "test-secret")
	if err := c2.ReportEvent(SubsiteEvent{EventID: "cdk:claim:y", EventType: "cdk.claim.success"}); err == nil {
		t.Fatal("4xx 应返回错误")
	}
	if calls.Load() != 1 {
		t.Fatalf("4xx 不应重试,got %d 次", calls.Load())
	}
}

func TestSanitizeEventID(t *testing.T) {
	cases := map[string]string{
		"cdk:claim:ABC-123": "cdk:claim:abc-123",
		"short":              "short000",
		"":                   "00000000",
	}
	for in, want := range cases {
		got := SanitizeEventID(in)
		if got != want {
			t.Errorf("SanitizeEventID(%q) = %q, want %q", in, got, want)
		}
		if len(got) < 8 || len(got) > 120 {
			t.Errorf("SanitizeEventID(%q) length %d out of [8,120]", in, len(got))
		}
	}
	long := strings.Repeat("a", 200)
	if got := SanitizeEventID(long); len(got) != 120 {
		t.Errorf("过长 eventId 应截断到 120,got %d", len(got))
	}
}
