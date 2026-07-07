package main

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestDeterministicRandomStreamIsReproducible(t *testing.T) {
	seed, _ := hex.DecodeString("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff")
	left := newDeterministicRandomStream(seed, "participants-a")
	right := newDeterministicRandomStream(seed, "participants-a")

	for i := 0; i < 32; i++ {
		l, err := left.Intn(17)
		if err != nil {
			t.Fatalf("left.Intn failed: %v", err)
		}
		r, err := right.Intn(17)
		if err != nil {
			t.Fatalf("right.Intn failed: %v", err)
		}
		if l != r {
			t.Fatalf("stream mismatch at %d: %d != %d", i, l, r)
		}
	}
}

func TestParticipantSnapshotHashChangesWithEligibleList(t *testing.T) {
	base := []LotteryParticipant{
		{ID: "u-1", Username: "alice", DisplayName: "Alice", Floor: 2, RepliedAt: "2026-06-18T12:00:00Z"},
		{ID: "u-2", Username: "bob", DisplayName: "Bob", Floor: 3, RepliedAt: "2026-06-18T12:01:00Z"},
	}
	changed := append([]LotteryParticipant(nil), base...)
	changed[1].Floor = 4

	if participantSnapshotHash(base) == participantSnapshotHash(changed) {
		t.Fatal("participant snapshot hash did not change after eligible list changed")
	}
}

func TestSecurePickLotteryUsesStrongSeedAndNoDuplicateWinners(t *testing.T) {
	participants := []LotteryParticipant{
		{ID: "u-1", Username: "alice", DisplayName: "Alice", Floor: 2, RepliedAt: "2026-06-18T12:00:00Z"},
		{ID: "u-2", Username: "bob", DisplayName: "Bob", Floor: 3, RepliedAt: "2026-06-18T12:01:00Z"},
		{ID: "u-3", Username: "chen", DisplayName: "Chen", Floor: 4, RepliedAt: "2026-06-18T12:02:00Z"},
		{ID: "u-4", Username: "dana", DisplayName: "Dana", Floor: 5, RepliedAt: "2026-06-18T12:03:00Z"},
	}

	winners, seed, proof, err := securePickLottery(participants, 3)
	if err != nil {
		t.Fatalf("securePickLottery failed: %v", err)
	}
	if !strings.HasPrefix(seed, "zens-") || len(strings.TrimPrefix(seed, "zens-")) != lotterySeedSize*2 {
		t.Fatalf("seed is not a %d-byte hex value: %q", lotterySeedSize, seed)
	}
	if len(proof) != 64 {
		t.Fatalf("proof should be a sha256 hex digest, got %q", proof)
	}
	if len(winners) != 3 {
		t.Fatalf("expected 3 winners, got %d", len(winners))
	}
	seen := map[string]bool{}
	for _, winner := range winners {
		if seen[winner.ID] {
			t.Fatalf("duplicate winner selected: %s", winner.ID)
		}
		seen[winner.ID] = true
	}
}

// ─── joinDraw 真实扣减主站积分 ───────────────────────────────────────────

// newJoinTestApp 组装一个带内存 store + 单用户 + 单抽奖的 App,主站地址指向 mainSiteURL。
func newJoinTestApp(t *testing.T, mainSiteURL string, serviceSecret string, costPoints int) (*App, string) {
	t.Helper()
	store, err := OpenStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatalf("OpenStore failed: %v", err)
	}
	now := time.Now().UTC()
	user := User{
		ID: "u-main-1", Username: "alice", DisplayName: "Alice",
		Level: 5, Points: 100, Role: "user", Provider: "sso", LastLoginAt: now,
	}
	draw := Draw{
		ID: "d-1", Title: "测试抽奖", PrizeName: "键盘", PrizeType: "physical", PrizeCount: 1,
		Status: "open", StartsAt: now.Add(-time.Hour), EndsAt: now.Add(time.Hour),
		MinLevel: 1, CostPoints: costPoints,
	}
	if err := store.update(func(state *State) error {
		state.Users = map[string]User{user.ID: user}
		state.Draws = []Draw{draw}
		state.Sessions = map[string]Session{}
		return nil
	}); err != nil {
		t.Fatalf("seed store failed: %v", err)
	}
	app := &App{
		cfg: Config{
			CommunityAPI:  mainSiteURL,
			ServiceID:     "campus-lottery-station",
			ServiceSecret: serviceSecret,
		},
		store:    store,
		sessions: map[string]Session{},
	}
	token := "test-session-token-0123456789abcdef"
	session := Session{Token: token, UserID: user.ID, ExpiresAt: now.Add(time.Hour)}
	app.sessions[token] = session
	return app, token
}

func joinRequest(t *testing.T, app *App, token, drawID string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/api/lotteries/"+drawID+"/join", strings.NewReader(`{}`))
	req.SetPathValue("id", drawID)
	req.AddCookie(&http.Cookie{Name: sessionCookie, Value: token})
	rec := httptest.NewRecorder()
	app.joinDraw(rec, req)
	return rec
}

// mainSiteStub 模拟主站 internal points 端点。
func mainSiteStub(t *testing.T, consumeStatus int, consumeBody string, consumeCalls, creditCalls *atomic.Int32) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case strings.HasSuffix(r.URL.Path, "/points/consume"):
			consumeCalls.Add(1)
			// 契约冒烟:HMAC 头必须齐全(算法正确性由主站侧过滤器保证)
			for _, h := range []string{"X-Service-Id", "X-Service-Timestamp", "X-Service-Nonce", "X-Service-Signature"} {
				if r.Header.Get(h) == "" {
					t.Errorf("consume 缺少签名头 %s", h)
				}
			}
			w.WriteHeader(consumeStatus)
			fmt.Fprint(w, consumeBody)
		case strings.HasSuffix(r.URL.Path, "/points/credit"):
			creditCalls.Add(1)
			w.WriteHeader(http.StatusOK)
			fmt.Fprint(w, `{"code":2000,"message":"success","data":{"userId":"u-main-1","pointsAfter":100,"creditedAmount":30}}`)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestJoinDrawConsumesMainSitePoints(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK,
		`{"code":2000,"message":"success","data":{"userId":"u-main-1","pointsAfter":70,"consumedAmount":30}}`,
		&consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	rec := joinRequest(t, app, token, "d-1")

	if rec.Code != http.StatusOK {
		t.Fatalf("joinDraw 应成功,got %d: %s", rec.Code, rec.Body.String())
	}
	if consumeCalls.Load() != 1 {
		t.Fatalf("应恰好调一次主站 consume,got %d", consumeCalls.Load())
	}
	state := app.store.snapshot()
	if got := state.Users["u-main-1"].Points; got != 70 {
		t.Fatalf("本地显示缓存应回写为主站扣减后余额 70,got %d", got)
	}
	if n := len(state.Draws[0].Participants); n != 1 {
		t.Fatalf("应有 1 条参与记录,got %d", n)
	}
}

func TestJoinDrawInsufficientPoints(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK,
		`{"code":5000,"message":"INSUFFICIENT_POINTS: 积分不足 (当前 10 < 需要 30)","data":null}`,
		&consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	rec := joinRequest(t, app, token, "d-1")

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("积分不足应回 400,got %d: %s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), "积分不足") {
		t.Fatalf("应提示积分不足,got %s", rec.Body.String())
	}
	state := app.store.snapshot()
	if n := len(state.Draws[0].Participants); n != 0 {
		t.Fatalf("扣减失败不应落参与记录,got %d", n)
	}
	if creditCalls.Load() != 0 {
		t.Fatalf("业务失败不应触发补偿退款,got %d", creditCalls.Load())
	}
}

func TestJoinDrawMainSiteUnreachable(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusInternalServerError, `{"code":5000,"message":"boom"}`, &consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	rec := joinRequest(t, app, token, "d-1")

	if rec.Code != http.StatusBadGateway {
		t.Fatalf("主站 5xx 应回 502,got %d: %s", rec.Code, rec.Body.String())
	}
	state := app.store.snapshot()
	if n := len(state.Draws[0].Participants); n != 0 {
		t.Fatalf("主站不可达不应落参与记录,got %d", n)
	}
}

func TestJoinDrawWithoutSecretRejectsPaidDraw(t *testing.T) {
	app, token := newJoinTestApp(t, "http://127.0.0.1:1", "", 30) // 未配密钥
	rec := joinRequest(t, app, token, "d-1")

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("未接入主站积分应回 503(绝不本地假扣),got %d: %s", rec.Code, rec.Body.String())
	}
	state := app.store.snapshot()
	if got := state.Users["u-main-1"].Points; got != 100 {
		t.Fatalf("本地余额不得被假扣,expected 100 got %d", got)
	}
}

func TestJoinDrawFreeDrawSkipsMainSite(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK, `{}`, &consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 0) // 免费抽奖
	rec := joinRequest(t, app, token, "d-1")

	if rec.Code != http.StatusOK {
		t.Fatalf("免费抽奖应成功,got %d: %s", rec.Code, rec.Body.String())
	}
	if consumeCalls.Load() != 0 {
		t.Fatalf("免费抽奖不应调主站,got %d", consumeCalls.Load())
	}
}

func TestJoinDrawRepeatIsIdempotentNoDoubleCharge(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK,
		`{"code":2000,"message":"success","data":{"userId":"u-main-1","pointsAfter":70,"consumedAmount":30}}`,
		&consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	first := joinRequest(t, app, token, "d-1")
	second := joinRequest(t, app, token, "d-1")

	if first.Code != http.StatusOK || second.Code != http.StatusOK {
		t.Fatalf("重复参与应幂等成功,got %d/%d", first.Code, second.Code)
	}
	if consumeCalls.Load() != 1 {
		t.Fatalf("重复参与不应二次扣费,consume 调用 %d 次", consumeCalls.Load())
	}
	if creditCalls.Load() != 0 {
		t.Fatalf("幂等返回不应触发退款,credit 调用 %d 次", creditCalls.Load())
	}
	state := app.store.snapshot()
	if n := len(state.Draws[0].Participants); n != 1 {
		t.Fatalf("参与记录应只有 1 条,got %d", n)
	}
}

func TestJoinDrawPrecheckRejectsBeforeCharging(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK,
		`{"code":2000,"message":"success","data":{"userId":"u-main-1","pointsAfter":70,"consumedAmount":30}}`,
		&consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	// 名额上限 1 且已被他人占满:预检必须在扣费前拒绝(不收费的快速失败)
	if err := app.store.update(func(state *State) error {
		state.Draws[0].ParticipantLimit = 1
		state.Draws[0].Participants = []Participation{{
			ID: "p-other", UserID: "u-other", Username: "other",
			DisplayName: "Other", Level: 3, JoinedAt: time.Now().UTC(),
		}}
		return nil
	}); err != nil {
		t.Fatalf("prep failed: %v", err)
	}

	rec := joinRequest(t, app, token, "d-1")
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("名额已满应报错,got %d: %s", rec.Code, rec.Body.String())
	}
	if consumeCalls.Load() != 0 {
		t.Fatalf("预检拒绝不应产生扣费,consume 调用 %d 次", consumeCalls.Load())
	}
	if creditCalls.Load() != 0 {
		t.Fatalf("未扣费不应退款,credit 调用 %d 次", creditCalls.Load())
	}
}

func TestJoinDrawCompensatesWhenLocalJoinFails(t *testing.T) {
	var consumeCalls, creditCalls atomic.Int32
	stub := mainSiteStub(t, http.StatusOK,
		`{"code":2000,"message":"success","data":{"userId":"u-main-1","pointsAfter":70,"consumedAmount":30}}`,
		&consumeCalls, &creditCalls)
	defer stub.Close()

	app, token := newJoinTestApp(t, stub.URL, "test-secret", 30)
	// 模拟扣费成功后本地落盘失败(磁盘写错误):saveLocked 写不进不存在的目录
	app.store.path = filepath.Join(t.TempDir(), "missing-dir", "state.json")

	rec := joinRequest(t, app, token, "d-1")
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("本地落盘失败应报错,got %d: %s", rec.Code, rec.Body.String())
	}
	if consumeCalls.Load() != 1 {
		t.Fatalf("应调过一次 consume,got %d", consumeCalls.Load())
	}
	if creditCalls.Load() != 1 {
		t.Fatalf("本地失败应补偿退款一次,got %d", creditCalls.Load())
	}
}

func TestParseMainSiteResult(t *testing.T) {
	code, msg, data := parseMainSiteResult([]byte(
		`{"code":5000,"message":"INSUFFICIENT_POINTS: 积分不足 (当前 10 < 需要 30)","data":null}`))
	if code != "INSUFFICIENT_POINTS" {
		t.Fatalf("应解析出前缀码,got %q (%q)", code, msg)
	}
	code, _, data = parseMainSiteResult([]byte(
		`{"code":2000,"message":"success","data":{"pointsAfter":70}}`))
	if code != "" {
		t.Fatalf("成功响应不应有错误码,got %q", code)
	}
	if after, ok := data["pointsAfter"].(float64); !ok || int(after) != 70 {
		t.Fatalf("应解析出 pointsAfter=70,got %v", data)
	}
	var jsonCheck any
	if err := json.Unmarshal([]byte(`{}`), &jsonCheck); err != nil {
		t.Fatal(err)
	}
}
