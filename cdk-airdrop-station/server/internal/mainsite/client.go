// Package mainsite 提供 cdk 空投站调用主站(campus-pulse)内部 s2s API 的客户端。
//
// 与主站 InternalServiceFilter 对应,签名串逐字节对齐 zdc-shop/src/lib/main-site/hmac.ts
// 与 campus-lottery-station/server/main.go signedInternalHeaders:
//
//	payload   = METHOD + "\n" + PATH + "\n" + TIMESTAMP(ms) + "\n" + NONCE + "\n" + sha256Hex(BODY)
//	signature = hex(HMAC_SHA256(CDK_SERVICE_SECRET, payload))  // 全小写
//
// 主站需在 internal.service.clients 白名单中登记 id=cdk-airdrop 并共享同一密钥。
package mainsite

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const subsiteEventsPath = "/api/internal/subsite/events"

var eventIDInvalidChars = regexp.MustCompile(`[^a-z0-9._:-]+`)

// Client 主站内部 API 客户端。零值不可用,经 New 构造;未配置密钥时 Enabled() 为 false。
type Client struct {
	baseURL   string
	serviceID string
	secret    string
	logger    *slog.Logger
	http      *http.Client
}

func New(baseURL, serviceID, secret string, logger *slog.Logger) *Client {
	return &Client{
		baseURL:   strings.TrimRight(strings.TrimSpace(baseURL), "/"),
		serviceID: strings.TrimSpace(serviceID),
		secret:    strings.TrimSpace(secret),
		logger:    logger,
		http:      &http.Client{Timeout: 12 * time.Second},
	}
}

// Enabled 是否已接入主站内部 API(密钥与地址齐备)。
// 未接入时调用方应跳过回流并记日志,绝不阻断本站业务。
func (c *Client) Enabled() bool {
	return c != nil && c.secret != "" && c.baseURL != ""
}

// SubsiteEvent 与主站 SubsiteEventCreateReq 对应。
type SubsiteEvent struct {
	EventID    string         `json:"eventId"`
	Source     string         `json:"source"`
	EventType  string         `json:"eventType"`
	UserID     string         `json:"userId,omitempty"`
	Title      string         `json:"title"`
	Content    string         `json:"content"`
	RelatedID  string         `json:"relatedId,omitempty"`
	Severity   string         `json:"severity,omitempty"`
	Status     string         `json:"status,omitempty"`
	NotifyUser bool           `json:"notifyUser"`
	Payload    map[string]any `json:"payload,omitempty"`
}

// ReportEvent 上报子站事件(写主站账本 + 可选站内通知)。
// 指数退避重试×3:网络错/5xx 重试,4xx 不重试(幂等 eventId 保证重复安全)。
// 每次重试重新签名——timestamp(±60s 窗口)与 nonce(单次)都不能复用。
func (c *Client) ReportEvent(ev SubsiteEvent) error {
	if !c.Enabled() {
		return fmt.Errorf("mainsite client disabled: missing CDK_SERVICE_SECRET or MAIN_SITE_API_URL")
	}
	if ev.Source == "" {
		ev.Source = c.serviceID
	}
	ev.EventID = SanitizeEventID(ev.EventID)
	raw, err := json.Marshal(ev)
	if err != nil {
		return err
	}
	body := string(raw)

	const maxAttempts = 3
	var lastErr error
	for attempt := 1; attempt <= maxAttempts; attempt++ {
		retryable, err := c.postSigned(subsiteEventsPath, body)
		if err == nil {
			if attempt > 1 && c.logger != nil {
				c.logger.Info("主站事件回流重试成功", "eventId", ev.EventID, "attempt", attempt)
			}
			return nil
		}
		lastErr = err
		if !retryable || attempt == maxAttempts {
			break
		}
		backoff := time.Duration(200*(1<<(attempt-1))) * time.Millisecond
		time.Sleep(backoff)
	}
	return lastErr
}

// postSigned 发一次签名 POST。返回 retryable:网络错/5xx 为 true,4xx 为 false。
func (c *Client) postSigned(path, body string) (retryable bool, err error) {
	req, err := http.NewRequest(http.MethodPost, c.baseURL+path, strings.NewReader(body))
	if err != nil {
		return false, err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")
	for k, v := range c.signedHeaders(http.MethodPost, path, body) {
		req.Header.Set(k, v)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return true, fmt.Errorf("无法连接主站内部接口: %w", err)
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return false, nil
	}
	return resp.StatusCode >= 500, fmt.Errorf("主站内部接口返回错误: %s %s", resp.Status, strings.TrimSpace(string(respBody)))
}

func (c *Client) signedHeaders(method, path, body string) map[string]string {
	ts := strconv.FormatInt(time.Now().UnixMilli(), 10)
	nonceBytes := make([]byte, 16)
	_, _ = rand.Read(nonceBytes)
	nonce := hex.EncodeToString(nonceBytes) // 32 字符,命中主站 ^[A-Za-z0-9_-]{16,128}$
	bodyHash := sha256.Sum256([]byte(body))
	payload := strings.Join([]string{
		strings.ToUpper(method),
		path,
		ts,
		nonce,
		hex.EncodeToString(bodyHash[:]),
	}, "\n")
	mac := hmac.New(sha256.New, []byte(c.secret))
	mac.Write([]byte(payload))
	return map[string]string{
		"X-Service-Id":        c.serviceID,
		"X-Service-Timestamp": ts,
		"X-Service-Nonce":     nonce,
		"X-Service-Signature": hex.EncodeToString(mac.Sum(nil)),
	}
}

// SanitizeEventID 把任意串规整成主站要求的 eventId(8–120 字符, ^[a-z0-9._:-]+$)。
func SanitizeEventID(raw string) string {
	s := strings.ToLower(strings.TrimSpace(raw))
	s = eventIDInvalidChars.ReplaceAllString(s, "-")
	if len(s) > 120 {
		s = s[:120]
	}
	for len(s) < 8 {
		s += "0"
	}
	return s
}
