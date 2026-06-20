package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/sha512"
	"embed"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed web/*
var webFiles embed.FS

const sessionCookie = "campus_lottery_session"
const lotteryAlgorithm = "zens-csprng-hmac-sha256-fisher-yates-v1"
const lotterySeedSize = 32

type Config struct {
	Addr                 string
	DataPath             string
	PublicURL            string
	LogoURL              string
	CommunityBase        string
	CommunityAPI         string
	SSOAuthorizeURL      string
	SSOTokenURL          string
	SSOClientID          string
	SSOClientSecret      string
	CommunityJWTSecret   string
	SessionSecret        string
	BotAccessToken       string
	BotUsername          string
	BotPassword          string
	ServiceID            string
	ServiceSecret        string
	AllowDemoSSOFallback bool
	CommentMaxPages      int
}

type App struct {
	cfg      Config
	store    *Store
	sessions map[string]Session
	lottery  *LotteryRuntime
	mu       sync.Mutex
}

type LotteryRuntime struct {
	mu      sync.Mutex
	topics  map[string]TopicPreview
	results map[string]LotteryDrawResult
	bot     BotAccount
}

type Store struct {
	path string
	mu   sync.Mutex
	data State
}

type State struct {
	Draws      []Draw                       `json:"draws"`
	Users      map[string]User              `json:"users"`
	Sessions   map[string]Session           `json:"sessions,omitempty"`
	TopicDraws map[string]LotteryDrawResult `json:"topicDraws,omitempty"`
	Audit      []AuditEvent                 `json:"audit"`
}

type User struct {
	ID          string    `json:"id"`
	Username    string    `json:"username"`
	DisplayName string    `json:"displayName"`
	Avatar      string    `json:"avatar"`
	Level       int       `json:"level"`
	Points      int       `json:"points"`
	Role        string    `json:"role"`
	Provider    string    `json:"provider"`
	LastLoginAt time.Time `json:"lastLoginAt"`
}

type Session struct {
	Token string `json:"token"`
	UserID string `json:"userId"`
	// CommunityAccessToken 存的是主站颁发的 5 分钟 SSO token,仅在登录回调时用于
	// 本地验签确认身份(见 userFromSSOToken)。它【不会、也无法】通过主站用户态鉴权:
	// 主站 JwtAuthenticationFilter 要求 token 必须命中 Redis 里的 access_token,而 SSO
	// token 从不入 Redis。社区读取走公开端点(/post/**、/comment/post/**),机器人写入
	// 走 LOTTERY_BOT_* 账号登录拿的真实 access token。此字段过期后即失效,属正常。
	CommunityAccessToken string    `json:"-"`
	ExpiresAt            time.Time `json:"expiresAt"`
}

type Draw struct {
	ID               string          `json:"id"`
	Title            string          `json:"title"`
	Summary          string          `json:"summary"`
	PrizeName        string          `json:"prizeName"`
	PrizeType        string          `json:"prizeType"`
	PrizeCount       int             `json:"prizeCount"`
	Sponsor          string          `json:"sponsor"`
	Status           string          `json:"status"`
	StartsAt         time.Time       `json:"startsAt"`
	EndsAt           time.Time       `json:"endsAt"`
	MinLevel         int             `json:"minLevel"`
	CostPoints       int             `json:"costPoints"`
	ParticipantLimit int             `json:"participantLimit"`
	DiscussionURL    string          `json:"discussionUrl"`
	Rules            []string        `json:"rules"`
	Participants     []Participation `json:"participants"`
	Winners          []Winner        `json:"winners"`
	CreatedAt        time.Time       `json:"createdAt"`
	UpdatedAt        time.Time       `json:"updatedAt"`
}

type Participation struct {
	ID          string    `json:"id"`
	UserID      string    `json:"userId"`
	Username    string    `json:"username"`
	DisplayName string    `json:"displayName"`
	Level       int       `json:"level"`
	Note        string    `json:"note"`
	JoinedAt    time.Time `json:"joinedAt"`
}

type Winner struct {
	Rank        int       `json:"rank"`
	UserID      string    `json:"userId"`
	Username    string    `json:"username"`
	DisplayName string    `json:"displayName"`
	PrizeName   string    `json:"prizeName"`
	DrawnAt     time.Time `json:"drawnAt"`
}

type AuditEvent struct {
	ID        string    `json:"id"`
	Type      string    `json:"type"`
	DrawID    string    `json:"drawId,omitempty"`
	UserID    string    `json:"userId,omitempty"`
	Message   string    `json:"message"`
	CreatedAt time.Time `json:"createdAt"`
}

type APIResponse struct {
	OK    bool   `json:"ok"`
	Data  any    `json:"data,omitempty"`
	Error string `json:"error,omitempty"`
}

type LotteryParticipant struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"displayName"`
	Avatar      string `json:"avatar,omitempty"`
	Floor       int    `json:"floor"`
	RepliedAt   string `json:"repliedAt"`
	Excerpt     string `json:"excerpt,omitempty"`
}

type TopicPreview struct {
	TopicTitle       string               `json:"topicTitle"`
	Author           string               `json:"author"`
	ReplyCount       int                  `json:"replyCount"`
	ParticipantCount int                  `json:"participantCount"`
	LastFloor        int                  `json:"lastFloor"`
	SyncedAt         string               `json:"syncedAt,omitempty"`
	CommentSource    string               `json:"commentSource,omitempty"`
	Participants     []LotteryParticipant `json:"participants"`
}

type LotteryWinner struct {
	LotteryParticipant
	Rank int `json:"rank"`
}

type LotteryDrawResult struct {
	DrawID           string          `json:"drawId"`
	TopicURL         string          `json:"topicUrl,omitempty"`
	PostID           string          `json:"postId,omitempty"`
	Seed             string          `json:"seed"`
	Algorithm        string          `json:"algorithm,omitempty"`
	ParticipantHash  string          `json:"participantHash,omitempty"`
	Proof            string          `json:"proof,omitempty"`
	ParticipantCount int             `json:"participantCount"`
	Winners          []LotteryWinner `json:"winners"`
	CreatedAt        string          `json:"createdAt,omitempty"`
}

type BotAccount struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"displayName"`
	Avatar      string `json:"avatar"`
	Status      string `json:"status"`
}

type PublishedComment struct {
	CommentID    string `json:"commentId"`
	CommentURL   string `json:"commentUrl"`
	CommentFloor int    `json:"commentFloor"`
	BotName      string `json:"botName"`
	PostedAt     string `json:"postedAt"`
}

type lotteryCriteria struct {
	TopicURL      string `json:"topicUrl"`
	MaxFloor      *int   `json:"maxFloor"`
	ExcludeAuthor bool   `json:"excludeAuthor"`
	DedupeUser    bool   `json:"dedupeUser"`
	ReplyOnly     bool   `json:"replyOnly"`
}

type lotteryDrawRequest struct {
	lotteryCriteria
	WinnerCount int `json:"winnerCount"`
}

type publishResultRequest struct {
	TopicURL         string          `json:"topicUrl"`
	DrawID           string          `json:"drawId"`
	Seed             string          `json:"seed"`
	Algorithm        string          `json:"algorithm,omitempty"`
	ParticipantHash  string          `json:"participantHash,omitempty"`
	Proof            string          `json:"proof,omitempty"`
	ParticipantCount int             `json:"participantCount"`
	Winners          []LotteryWinner `json:"winners"`
	BotAccountID     string          `json:"botAccountId"`
}

type communityResult[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data"`
}

type communityPost struct {
	ID           string `json:"id"`
	UserID       string `json:"userId"`
	Title        string `json:"title"`
	AuthorName   string `json:"authorName"`
	AuthorAvatar string `json:"authorAvatar"`
	CommentCount int    `json:"commentCount"`
}

type communityCommentPage struct {
	Records []communityComment `json:"records"`
	Total   looseInt           `json:"total"`
	Pages   looseInt           `json:"pages"`
}

type communitySimpleProfile struct {
	ID       string `json:"id"`
	Username string `json:"username"`
	Nickname string `json:"nickname"`
	Avatar   string `json:"avatar"`
	Level    int    `json:"level"`
	Points   int    `json:"points"`
}

type communityComment struct {
	ID          string             `json:"id"`
	PostID      string             `json:"postId"`
	UserID      string             `json:"userId"`
	Nickname    string             `json:"nickname"`
	UserAvatar  string             `json:"userAvatar"`
	Content     string             `json:"content"`
	ParentID    string             `json:"parentId"`
	IsAnonymous int                `json:"isAnonymous"`
	AuditStatus string             `json:"auditStatus"`
	CreateTime  string             `json:"createTime"`
	Children    []communityComment `json:"children"`
}

type looseInt int

func (n *looseInt) UnmarshalJSON(raw []byte) error {
	raw = bytes.TrimSpace(raw)
	if len(raw) == 0 || bytes.Equal(raw, []byte("null")) {
		*n = 0
		return nil
	}
	var asInt int
	if err := json.Unmarshal(raw, &asInt); err == nil {
		*n = looseInt(asInt)
		return nil
	}
	var asString string
	if err := json.Unmarshal(raw, &asString); err != nil {
		return err
	}
	asString = strings.TrimSpace(asString)
	if asString == "" {
		*n = 0
		return nil
	}
	parsed, err := strconv.Atoi(asString)
	if err != nil {
		return err
	}
	*n = looseInt(parsed)
	return nil
}

func main() {
	cfg := loadConfig()
	store, err := OpenStore(cfg.DataPath)
	if err != nil {
		log.Fatalf("open store: %v", err)
	}

	app := &App{
		cfg:      cfg,
		store:    store,
		sessions: activeSessions(store.snapshot()),
		lottery:  NewLotteryRuntime(cfg),
	}
	if err := store.update(func(state *State) error {
		state.Sessions = activeSessions(*state)
		return nil
	}); err != nil {
		log.Printf("prune sessions: %v", err)
	}

	mux := http.NewServeMux()
	app.routes(mux)

	log.Printf("Campus Lottery Station listening on %s", cfg.Addr)
	log.Printf("Public URL: %s", cfg.PublicURL)
	log.Printf("Community API: %s", cfg.CommunityAPI)
	log.Fatal(http.ListenAndServe(cfg.Addr, withSecurityHeaders(mux)))
}

func loadConfig() Config {
	addr := env("LOTTERY_ADDR", ":8093")
	publicURL := strings.TrimRight(env("LOTTERY_PUBLIC_URL", "http://localhost"+addr), "/")
	communityBase := strings.TrimRight(env("COMMUNITY_BASE_URL", "http://localhost:8080"), "/")
	communityAPI := strings.TrimRight(env("COMMUNITY_API_BASE_URL", "http://localhost:7800"), "/")
	return Config{
		Addr:                 addr,
		DataPath:             env("LOTTERY_DATA", filepath.Join("data", "state.json")),
		PublicURL:            publicURL,
		LogoURL:              env("LOTTERY_LOGO_URL", "/logo.png"),
		CommunityBase:        communityBase,
		CommunityAPI:         communityAPI,
		SSOAuthorizeURL:      env("COMMUNITY_SSO_AUTHORIZE_URL", communityBase+"/sso/authorize"),
		SSOTokenURL:          env("COMMUNITY_SSO_TOKEN_URL", communityBase+"/api/sso/token"),
		SSOClientID:          env("SSO_CLIENT_ID", "campus-lottery-station"),
		SSOClientSecret:      env("SSO_CLIENT_SECRET", "change-me"),
		CommunityJWTSecret:   env("COMMUNITY_JWT_SECRET", ""),
		SessionSecret:        env("LOTTERY_SESSION_SECRET", "campus-lottery-dev-secret"),
		BotAccessToken:       env("LOTTERY_BOT_ACCESS_TOKEN", ""),
		BotUsername:          env("LOTTERY_BOT_USERNAME", "zens-lottery-bot"),
		BotPassword:          env("LOTTERY_BOT_PASSWORD", ""),
		ServiceID:            env("LOTTERY_SERVICE_ID", "campus-lottery-station"),
		ServiceSecret:        env("LOTTERY_SERVICE_SECRET", ""),
		AllowDemoSSOFallback: envBool("LOTTERY_ALLOW_DEMO_SSO_FALLBACK", false),
		CommentMaxPages:      envInt("LOTTERY_COMMENT_MAX_PAGES", 50),
	}
}

func env(key, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func envBool(key string, fallback bool) bool {
	value := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	if value == "" {
		return fallback
	}
	return value == "1" || value == "true" || value == "yes" || value == "on"
}

func envInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}

func (app *App) routes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/health", app.health)
	mux.HandleFunc("GET /api/bootstrap", app.bootstrap)
	mux.HandleFunc("GET /api/me", app.me)
	mux.HandleFunc("POST /api/auth/dev-login", app.devLogin)
	mux.HandleFunc("POST /api/auth/logout", app.logout)
	mux.HandleFunc("GET /api/auth/sso/start", app.ssoStart)
	mux.HandleFunc("GET /api/auth/sso/logout", app.ssoLogout)
	mux.HandleFunc("GET /api/auth/sso/callback", app.ssoCallback)
	mux.HandleFunc("GET /api/lotteries", app.listDraws)
	mux.HandleFunc("GET /api/lotteries/{id}", app.getDraw)
	mux.HandleFunc("POST /api/lotteries/{id}/join", app.joinDraw)
	mux.HandleFunc("POST /api/lotteries/{id}/draw", app.drawWinners)
	mux.HandleFunc("POST /api/lottery/preview", app.lotteryPreview)
	mux.HandleFunc("POST /api/lottery/comments/sync", app.syncLotteryComments)
	mux.HandleFunc("POST /api/lottery/draw", app.drawTopicLottery)
	mux.HandleFunc("GET /api/lottery/bot-account", app.lotteryBotAccount)
	mux.HandleFunc("POST /api/lottery/bot-account/apply", app.lotteryBotAccountApply)
	mux.HandleFunc("POST /api/lottery/results/publish", app.publishLotteryResult)
	mux.HandleFunc("POST /api/admin/lotteries", app.createDraw)
	mux.HandleFunc("GET /api/admin/lotteries/{id}/announcement", app.announcement)
	mux.HandleFunc("GET /api/admin/audit", app.audit)
	mux.HandleFunc("/", app.static)
}

func withSecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "SAMEORIGIN")
		w.Header().Set("Referrer-Policy", "same-origin")
		next.ServeHTTP(w, r)
	})
}

func OpenStore(path string) (*Store, error) {
	store := &Store{path: path}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return nil, err
	}

	raw, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		store.data = seedState()
		return store, store.saveLocked()
	}
	if err != nil {
		return nil, err
	}
	if len(strings.TrimSpace(string(raw))) == 0 {
		store.data = seedState()
		return store, store.saveLocked()
	}
	if err := json.Unmarshal(raw, &store.data); err != nil {
		return nil, err
	}
	if store.data.Users == nil {
		store.data.Users = map[string]User{}
	}
	if store.data.Sessions == nil {
		store.data.Sessions = map[string]Session{}
	}
	if store.data.TopicDraws == nil {
		store.data.TopicDraws = map[string]LotteryDrawResult{}
	}
	return store, nil
}

func seedState() State {
	now := time.Now().UTC()
	admin := User{
		ID:          "u-admin",
		Username:    "admin",
		DisplayName: "社区运营管理员",
		Avatar:      "A",
		Level:       6,
		Points:      860,
		Role:        "admin",
		Provider:    "seed",
		LastLoginAt: now,
	}
	demo := User{
		ID:          "u-demo",
		Username:    "demo",
		DisplayName: "校园体验用户",
		Avatar:      "D",
		Level:       3,
		Points:      320,
		Role:        "user",
		Provider:    "seed",
		LastLoginAt: now,
	}
	draws := []Draw{
		{
			ID:               "lot-202605-lab-pass",
			Title:            "校园实验室开放日优先名额",
			Summary:          "面向社区活跃成员开放 12 个参观名额，获奖后可在主站帖子中确认时间段。",
			PrizeName:        "实验室开放日名额",
			PrizeType:        "activity-pass",
			PrizeCount:       12,
			Sponsor:          "Campus Pulse 社区运营组",
			Status:           "open",
			StartsAt:         now.Add(-3 * time.Hour),
			EndsAt:           now.Add(48 * time.Hour),
			MinLevel:         1,
			CostPoints:       10,
			ParticipantLimit: 300,
			DiscussionURL:    "http://localhost:8080/posts/lab-open-day",
			Rules: []string{
				"每个主站账号只能参与一次。",
				"开奖前可在主站讨论帖补充报名理由。",
				"获奖名单会保存在本站，并可同步到社区公告帖。",
			},
			Participants: []Participation{},
			Winners:      []Winner{},
			CreatedAt:    now.Add(-4 * time.Hour),
			UpdatedAt:    now.Add(-4 * time.Hour),
		},
		{
			ID:               "lot-202605-cdk-pack",
			Title:            "开发工具 CDK 福利包",
			Summary:          "给近期参与问答、教程共建的同学准备的轻量福利，可和 CDK 空投台联动发码。",
			PrizeName:        "开发工具 CDK",
			PrizeType:        "cdk",
			PrizeCount:       20,
			Sponsor:          "Campus Pulse 福利站",
			Status:           "open",
			StartsAt:         now.Add(-24 * time.Hour),
			EndsAt:           now.Add(24 * time.Hour),
			MinLevel:         2,
			CostPoints:       30,
			ParticipantLimit: 200,
			DiscussionURL:    "http://localhost:8080/posts/cdk-pack",
			Rules: []string{
				"等级达到 2 级且积分足够即可参与。",
				"获奖后可跳转 CDK 空投台查看兑换码。",
				"异常账号、重复注册账号可由管理员取消资格。",
			},
			Participants: []Participation{
				{
					ID:          "p-seed-1",
					UserID:      demo.ID,
					Username:    demo.Username,
					DisplayName: demo.DisplayName,
					Level:       demo.Level,
					Note:        "想把福利用于课程项目环境。",
					JoinedAt:    now.Add(-2 * time.Hour),
				},
			},
			Winners:   []Winner{},
			CreatedAt: now.Add(-26 * time.Hour),
			UpdatedAt: now.Add(-2 * time.Hour),
		},
		{
			ID:               "lot-202605-book-club",
			Title:            "技术图书漂流计划",
			Summary:          "抽取 8 位同学加入图书漂流首批名单，阅读完成后在社区发布短评即可返还积分。",
			PrizeName:        "技术图书借阅资格",
			PrizeType:        "book",
			PrizeCount:       8,
			Sponsor:          "学习资料共建小组",
			Status:           "scheduled",
			StartsAt:         now.Add(12 * time.Hour),
			EndsAt:           now.Add(96 * time.Hour),
			MinLevel:         1,
			CostPoints:       0,
			ParticipantLimit: 120,
			DiscussionURL:    "http://localhost:8080/posts/book-club",
			Rules: []string{
				"无需消耗积分。",
				"获奖后 7 天内完成领取确认。",
				"逾期未确认会释放名额给候补用户。",
			},
			Participants: []Participation{},
			Winners:      []Winner{},
			CreatedAt:    now.Add(-1 * time.Hour),
			UpdatedAt:    now.Add(-1 * time.Hour),
		},
	}
	return State{
		Draws: draws,
		Users: map[string]User{
			admin.ID: admin,
			demo.ID:  demo,
		},
		Sessions:   map[string]Session{},
		TopicDraws: map[string]LotteryDrawResult{},
		Audit: []AuditEvent{
			{
				ID:        "audit-seed",
				Type:      "system.seed",
				Message:   "初始化校园抽奖福利站演示数据",
				CreatedAt: now,
			},
		},
	}
}

func (s *Store) saveLocked() error {
	tmp := s.path + ".tmp"
	raw, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	if err := os.WriteFile(tmp, raw, 0644); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

func (s *Store) snapshot() State {
	s.mu.Lock()
	defer s.mu.Unlock()
	raw, _ := json.Marshal(s.data)
	var copy State
	_ = json.Unmarshal(raw, &copy)
	return copy
}

func (s *Store) update(fn func(*State) error) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := fn(&s.data); err != nil {
		return err
	}
	return s.saveLocked()
}

func activeSessions(state State) map[string]Session {
	now := time.Now().UTC()
	sessions := map[string]Session{}
	for token, session := range state.Sessions {
		token = strings.TrimSpace(token)
		if token == "" || session.UserID == "" || now.After(session.ExpiresAt) {
			continue
		}
		if session.Token == "" {
			session.Token = token
		}
		sessions[token] = session
	}
	return sessions
}

func (app *App) health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, APIResponse{
		OK: true,
		Data: map[string]any{
			"service": "campus-lottery-station",
			"time":    time.Now().UTC(),
		},
	})
}

func (app *App) bootstrap(w http.ResponseWriter, r *http.Request) {
	state := app.store.snapshot()
	user, _ := app.currentUser(r)
	writeJSON(w, http.StatusOK, APIResponse{
		OK: true,
		Data: map[string]any{
			"user":    user,
			"draws":   decorateDraws(state.Draws, user),
			"stats":   buildStats(state),
			"config":  app.publicConfig(),
			"version": "0.1.0",
		},
	})
}

func (app *App) publicConfig() map[string]any {
	return map[string]any{
		"communityBaseUrl": app.cfg.CommunityBase,
		"logoUrl":          app.cfg.LogoURL,
		"ssoEnabled":       app.cfg.SSOClientID != "" && app.cfg.SSOAuthorizeURL != "",
		"ssoClientId":      app.cfg.SSOClientID,
		"ssoStartUrl":      "/api/auth/sso/start",
	}
}

func buildStats(state State) map[string]any {
	totalParticipants := 0
	openDraws := 0
	winnerCount := 0
	for _, draw := range state.Draws {
		totalParticipants += len(draw.Participants)
		winnerCount += len(draw.Winners)
		if draw.Status == "open" {
			openDraws++
		}
	}
	return map[string]any{
		"drawCount":         len(state.Draws),
		"openDraws":         openDraws,
		"totalParticipants": totalParticipants,
		"winnerCount":       winnerCount,
	}
}

func decorateDraws(draws []Draw, user *User) []map[string]any {
	out := make([]map[string]any, 0, len(draws))
	for _, draw := range draws {
		joined := false
		if user != nil {
			for _, p := range draw.Participants {
				if p.UserID == user.ID {
					joined = true
					break
				}
			}
		}
		remaining := draw.ParticipantLimit - len(draw.Participants)
		if draw.ParticipantLimit == 0 {
			remaining = -1
		}
		out = append(out, map[string]any{
			"id":               draw.ID,
			"title":            draw.Title,
			"summary":          draw.Summary,
			"prizeName":        draw.PrizeName,
			"prizeType":        draw.PrizeType,
			"prizeCount":       draw.PrizeCount,
			"sponsor":          draw.Sponsor,
			"status":           draw.Status,
			"startsAt":         draw.StartsAt,
			"endsAt":           draw.EndsAt,
			"minLevel":         draw.MinLevel,
			"costPoints":       draw.CostPoints,
			"participantLimit": draw.ParticipantLimit,
			"remainingSlots":   remaining,
			"discussionUrl":    draw.DiscussionURL,
			"rules":            draw.Rules,
			"participantCount": len(draw.Participants),
			"participants":     draw.Participants,
			"winners":          draw.Winners,
			"joined":           joined,
			"canJoin":          canJoin(draw, user) == nil,
			"joinReason":       joinReason(draw, user),
			"createdAt":        draw.CreatedAt,
			"updatedAt":        draw.UpdatedAt,
		})
	}
	return out
}

func joinReason(draw Draw, user *User) string {
	if err := canJoin(draw, user); err != nil {
		return err.Error()
	}
	return "可以参与"
}

func canJoin(draw Draw, user *User) error {
	now := time.Now().UTC()
	if user == nil {
		return errors.New("请先登录社区账号")
	}
	if draw.Status != "open" {
		if draw.Status == "scheduled" {
			return errors.New("抽奖尚未开始")
		}
		return errors.New("抽奖已结束")
	}
	if now.Before(draw.StartsAt) {
		return errors.New("抽奖尚未开始")
	}
	if now.After(draw.EndsAt) {
		return errors.New("抽奖已过期")
	}
	if user.Level < draw.MinLevel {
		return fmt.Errorf("社区等级需要达到 %d 级", draw.MinLevel)
	}
	if user.Points < draw.CostPoints {
		return fmt.Errorf("积分不足，需要 %d 积分", draw.CostPoints)
	}
	if draw.ParticipantLimit > 0 && len(draw.Participants) >= draw.ParticipantLimit {
		return errors.New("参与名额已满")
	}
	for _, p := range draw.Participants {
		if p.UserID == user.ID {
			return errors.New("你已经参与过本次抽奖")
		}
	}
	return nil
}

func (app *App) me(w http.ResponseWriter, r *http.Request) {
	user, _ := app.currentUser(r)
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: user})
}

func (app *App) devLogin(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username    string `json:"username"`
		DisplayName string `json:"displayName"`
		Level       int    `json:"level"`
		Points      int    `json:"points"`
	}
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	req.Username = normalizeUsername(req.Username)
	if req.Username == "" {
		writeError(w, http.StatusBadRequest, "用户名不能为空")
		return
	}
	if req.DisplayName == "" {
		req.DisplayName = req.Username
	}
	if req.Level <= 0 {
		req.Level = 2
	}
	if req.Points < 0 {
		req.Points = 0
	}
	if req.Points == 0 {
		req.Points = 180
	}

	var user User
	err := app.store.update(func(state *State) error {
		existing, ok := findUserByUsername(state.Users, req.Username)
		role := "user"
		if req.Username == "admin" {
			role = "admin"
			if req.Level < 6 {
				req.Level = 6
			}
		}
		if ok {
			existing.DisplayName = req.DisplayName
			existing.Level = req.Level
			existing.Points = req.Points
			existing.Role = role
			existing.Provider = "dev"
			existing.LastLoginAt = time.Now().UTC()
			state.Users[existing.ID] = existing
			user = existing
			return nil
		}
		user = User{
			ID:          "u-" + randomID(10),
			Username:    req.Username,
			DisplayName: req.DisplayName,
			Avatar:      avatarLetter(req.DisplayName),
			Level:       req.Level,
			Points:      req.Points,
			Role:        role,
			Provider:    "dev",
			LastLoginAt: time.Now().UTC(),
		}
		state.Users[user.ID] = user
		state.Audit = append(state.Audit, AuditEvent{
			ID:        "audit-" + randomID(12),
			Type:      "auth.dev_login",
			UserID:    user.ID,
			Message:   user.DisplayName + " 使用演示登录进入抽奖站",
			CreatedAt: time.Now().UTC(),
		})
		return nil
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	app.issueSession(w, user.ID, "")
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: user})
}

func findUserByUsername(users map[string]User, username string) (User, bool) {
	for _, user := range users {
		if user.Username == username {
			return user, true
		}
	}
	return User{}, false
}

func normalizeUsername(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	value = strings.ReplaceAll(value, " ", "-")
	return value
}

func avatarLetter(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return "U"
	}
	return strings.ToUpper(string([]rune(value)[0]))
}

func (app *App) logout(w http.ResponseWriter, r *http.Request) {
	app.clearSession(w, r)
	writeJSON(w, http.StatusOK, APIResponse{OK: true})
}

// ssoLogout 前端通道(front-channel)单点登出端点:主站登出时用隐藏 iframe GET 加载,
// 清掉本站会话 cookie。返回 204,不重定向(供 iframe 静默调用)。
func (app *App) ssoLogout(w http.ResponseWriter, r *http.Request) {
	app.clearSession(w, r)
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusNoContent)
}

// clearSession 删除会话记录并过期会话 cookie。
func (app *App) clearSession(w http.ResponseWriter, r *http.Request) {
	cookie, err := r.Cookie(sessionCookie)
	if err == nil {
		app.mu.Lock()
		delete(app.sessions, cookie.Value)
		app.mu.Unlock()
		if err := app.store.update(func(state *State) error {
			delete(state.Sessions, cookie.Value)
			return nil
		}); err != nil {
			log.Printf("delete session: %v", err)
		}
	}
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookie,
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
}

func (app *App) ssoStart(w http.ResponseWriter, r *http.Request) {
	state := randomID(18)
	signedState := state + "." + signValue(app.cfg.SessionSecret, state)
	redirectURI := app.cfg.PublicURL + "/api/auth/sso/callback"

	u, err := url.Parse(app.cfg.SSOAuthorizeURL)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "SSO 授权地址配置错误")
		return
	}
	q := u.Query()
	q.Set("response_type", "code")
	q.Set("client_id", app.cfg.SSOClientID)
	q.Set("redirect_uri", redirectURI)
	q.Set("scope", "profile lottery")
	q.Set("state", signedState)
	u.RawQuery = q.Encode()
	http.Redirect(w, r, u.String(), http.StatusFound)
}

func (app *App) ssoCallback(w http.ResponseWriter, r *http.Request) {
	if errText := strings.TrimSpace(r.URL.Query().Get("error")); errText != "" {
		http.Redirect(w, r, "/?sso="+url.QueryEscape(errText), http.StatusFound)
		return
	}
	ssoToken := firstNonEmpty(
		r.URL.Query().Get("sso_token"),
		r.URL.Query().Get("ssoToken"),
		r.URL.Query().Get("token"),
	)
	if ssoToken != "" {
		user, err := app.userFromSSOToken(ssoToken)
		if err != nil {
			log.Printf("SSO token verify failed: %v", err)
			http.Redirect(w, r, "/?sso=invalid-token", http.StatusFound)
			return
		}
		if err := app.persistLoginUser(user, "auth.sso_login", user.DisplayName+" 通过社区 SSO 登录抽奖站"); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		app.issueSession(w, user.ID, ssoToken)
		http.Redirect(w, r, "/?sso=ok", http.StatusFound)
		return
	}

	code := strings.TrimSpace(r.URL.Query().Get("code"))
	state := strings.TrimSpace(r.URL.Query().Get("state"))
	if code == "" {
		http.Redirect(w, r, "/?sso=missing-code", http.StatusFound)
		return
	}
	if !validSignedState(app.cfg.SessionSecret, state) {
		http.Redirect(w, r, "/?sso=bad-state", http.StatusFound)
		return
	}

	user, err := app.exchangeSSOCode(code)
	if err != nil {
		if !app.cfg.AllowDemoSSOFallback {
			http.Redirect(w, r, "/?sso=exchange-failed", http.StatusFound)
			return
		}
		user = User{
			ID:          "sso-" + stableID(code),
			Username:    "sso-user-" + stableID(code)[:6],
			DisplayName: "社区 SSO 用户",
			Avatar:      "S",
			Level:       3,
			Points:      260,
			Role:        "user",
			Provider:    "sso-demo",
			LastLoginAt: time.Now().UTC(),
		}
	}

	if err := app.persistLoginUser(user, "auth.sso_login", user.DisplayName+" 通过社区 SSO 登录抽奖站"); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	app.issueSession(w, user.ID, "")
	http.Redirect(w, r, "/?sso=ok", http.StatusFound)
}

func (app *App) persistLoginUser(user User, eventType, message string) error {
	return app.store.update(func(state *State) error {
		user.LastLoginAt = time.Now().UTC()
		if user.Username == "" {
			user.Username = "user-" + stableID(user.ID)[:8]
		}
		if user.DisplayName == "" {
			user.DisplayName = user.Username
		}
		if user.Avatar == "" {
			user.Avatar = avatarLetter(user.DisplayName)
		}
		if user.Role == "" {
			user.Role = "user"
		}
		if user.Provider == "" {
			user.Provider = "sso"
		}
		if user.Level <= 0 {
			user.Level = 1
		}
		if user.Points < 0 {
			user.Points = 0
		}
		if state.Users == nil {
			state.Users = map[string]User{}
		}
		state.Users[user.ID] = user
		state.Audit = append(state.Audit, AuditEvent{
			ID:        "audit-" + randomID(12),
			Type:      eventType,
			UserID:    user.ID,
			Message:   message,
			CreatedAt: time.Now().UTC(),
		})
		return nil
	})
}

// exchangeSSOCode 走 authorization_code 换 token 流程。
// 注意:主站【未实现】/api/sso/token 端点,只支持 direct-token 流(回调直接带 sso_token)。
// 因此本函数在对接当前主站时不会成功;仅在 LOTTERY_ALLOW_DEMO_SSO_FALLBACK=true 的
// 演示场景或未来主站补齐 code 交换端点后才有意义。正常登录走 ssoCallback 的 sso_token 分支。
func (app *App) exchangeSSOCode(code string) (User, error) {
	if app.cfg.SSOTokenURL == "" || app.cfg.SSOClientSecret == "" {
		return User{}, errors.New("sso token endpoint disabled")
	}
	payload := map[string]string{
		"grant_type":    "authorization_code",
		"code":          code,
		"client_id":     app.cfg.SSOClientID,
		"client_secret": app.cfg.SSOClientSecret,
		"redirect_uri":  app.cfg.PublicURL + "/api/auth/sso/callback",
	}
	raw, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, app.cfg.SSOTokenURL, strings.NewReader(string(raw)))
	if err != nil {
		return User{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	client := http.Client{Timeout: 4 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return User{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return User{}, fmt.Errorf("sso token exchange failed: %s", resp.Status)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return User{}, err
	}
	var parsed struct {
		User struct {
			ID          string `json:"id"`
			Username    string `json:"username"`
			DisplayName string `json:"displayName"`
			Avatar      string `json:"avatar"`
			Level       int    `json:"level"`
			Points      int    `json:"points"`
			Role        string `json:"role"`
		} `json:"user"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return User{}, err
	}
	if parsed.User.ID == "" {
		return User{}, errors.New("sso response missing user")
	}
	return User{
		ID:          parsed.User.ID,
		Username:    parsed.User.Username,
		DisplayName: parsed.User.DisplayName,
		Avatar:      parsed.User.Avatar,
		Level:       parsed.User.Level,
		Points:      parsed.User.Points,
		Role:        parsed.User.Role,
		Provider:    "sso",
		LastLoginAt: time.Now().UTC(),
	}, nil
}

func (app *App) userFromSSOToken(token string) (User, error) {
	claims, err := verifyHMACJWT(token, app.cfg.CommunityJWTSecret)
	if err != nil {
		return User{}, err
	}
	if !claimBool(claims, "sso") {
		return User{}, errors.New("不是有效的社区 SSO token")
	}
	clientID := claimString(claims, "client_id")
	if clientID != "" && clientID != app.cfg.SSOClientID {
		return User{}, errors.New("SSO token client_id 不匹配")
	}
	userID := claimSubject(claims)
	if userID == "" {
		return User{}, errors.New("SSO token 缺少用户 ID")
	}
	username := fallback(claimString(claims, "username"), "user-"+stableID(userID)[:8])
	displayName := firstNonEmpty(
		claimString(claims, "displayName"),
		claimString(claims, "nickname"),
		username,
	)
	role := normalizeRole(claimRoles(claims))
	user := User{
		ID:          userID,
		Username:    username,
		DisplayName: displayName,
		Avatar:      claimString(claims, "avatar"),
		Level:       claimInt(claims, "level", 1),
		Points:      claimInt(claims, "points", 0),
		Role:        role,
		Provider:    "sso",
		LastLoginAt: time.Now().UTC(),
	}
	if user.Level <= 0 {
		user.Level = claimInt(claims, "trustLevel", 1)
	}
	if synced, err := app.fetchCommunitySimpleProfile(token); err == nil {
		user = mergeCommunityProfile(user, synced)
	}
	return user, nil
}

func verifyHMACJWT(token, secret string) (map[string]any, error) {
	secret = strings.TrimSpace(secret)
	if secret == "" {
		return nil, errors.New("未配置 COMMUNITY_JWT_SECRET，无法验证主站 SSO token")
	}
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, errors.New("JWT 格式不正确")
	}
	headerPayload, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return nil, errors.New("JWT 头部格式不正确")
	}
	var header struct {
		Alg string `json:"alg"`
	}
	if err := json.Unmarshal(headerPayload, &header); err != nil {
		return nil, errors.New("JWT 头部解析失败")
	}
	alg := strings.ToUpper(strings.TrimSpace(header.Alg))
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return nil, errors.New("JWT 签名格式不正确")
	}
	if !verifyJWTSig(alg, sig, []byte(secret), parts[0]+"."+parts[1]) {
		derived := sha256.Sum256([]byte(secret))
		if !verifyJWTSig(alg, sig, derived[:], parts[0]+"."+parts[1]) {
			return nil, errors.New("JWT 签名无效")
		}
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, errors.New("JWT 载荷格式不正确")
	}
	var claims map[string]any
	if err := json.Unmarshal(payload, &claims); err != nil {
		return nil, errors.New("JWT 载荷解析失败")
	}
	if exp, ok := claims["exp"].(float64); ok && exp > 0 && time.Now().Unix() >= int64(exp) {
		return nil, errors.New("SSO token 已过期")
	}
	return claims, nil
}

func verifyJWTSig(alg string, sig []byte, key []byte, signingInput string) bool {
	var mac hashWriter
	switch alg {
	case "HS256":
		mac = hmac.New(sha256.New, key)
	case "HS384":
		mac = hmac.New(sha512.New384, key)
	case "HS512":
		mac = hmac.New(sha512.New, key)
	default:
		return false
	}
	_, _ = mac.Write([]byte(signingInput))
	return hmac.Equal(sig, mac.Sum(nil))
}

type hashWriter interface {
	Write([]byte) (int, error)
	Sum([]byte) []byte
}

func claimSubject(claims map[string]any) string {
	return claimString(claims, "sub")
}

func claimString(claims map[string]any, key string) string {
	if value, ok := claims[key]; ok {
		switch typed := value.(type) {
		case string:
			return strings.TrimSpace(typed)
		case float64:
			return strconv.FormatInt(int64(typed), 10)
		}
	}
	return ""
}

func claimBool(claims map[string]any, key string) bool {
	if value, ok := claims[key]; ok {
		switch typed := value.(type) {
		case bool:
			return typed
		case string:
			return strings.EqualFold(typed, "true") || typed == "1"
		}
	}
	return false
}

func claimInt(claims map[string]any, key string, fallbackValue int) int {
	if value, ok := claims[key]; ok {
		switch typed := value.(type) {
		case float64:
			return int(typed)
		case string:
			if parsed, err := strconv.Atoi(typed); err == nil {
				return parsed
			}
		}
	}
	return fallbackValue
}

func claimRoles(claims map[string]any) []string {
	value, ok := claims["roles"]
	if !ok {
		if role := claimString(claims, "role"); role != "" {
			return []string{role}
		}
		return nil
	}
	switch typed := value.(type) {
	case []any:
		roles := make([]string, 0, len(typed))
		for _, item := range typed {
			if role, ok := item.(string); ok && strings.TrimSpace(role) != "" {
				roles = append(roles, role)
			}
		}
		return roles
	case []string:
		return typed
	case string:
		return []string{typed}
	default:
		return nil
	}
}

func normalizeRole(roles []string) string {
	for _, role := range roles {
		normalized := strings.ToLower(strings.TrimSpace(role))
		normalized = strings.TrimPrefix(normalized, "role_")
		if normalized == "super_admin" || normalized == "admin" {
			return "admin"
		}
		if normalized == "moderator" {
			return "moderator"
		}
	}
	return "user"
}

func (app *App) listDraws(w http.ResponseWriter, r *http.Request) {
	state := app.store.snapshot()
	user, _ := app.currentUser(r)
	sort.Slice(state.Draws, func(i, j int) bool {
		return state.Draws[i].CreatedAt.After(state.Draws[j].CreatedAt)
	})
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: decorateDraws(state.Draws, user)})
}

func (app *App) getDraw(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	state := app.store.snapshot()
	user, _ := app.currentUser(r)
	for _, draw := range decorateDraws(state.Draws, user) {
		if draw["id"] == id {
			writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: draw})
			return
		}
	}
	writeError(w, http.StatusNotFound, "抽奖不存在")
}

func (app *App) joinDraw(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号")
		return
	}
	var req struct {
		Note string `json:"note"`
	}
	_ = decodeJSON(r, &req)
	req.Note = strings.TrimSpace(req.Note)
	if len([]rune(req.Note)) > 80 {
		writeError(w, http.StatusBadRequest, "参与备注最多 80 个字")
		return
	}
	id := r.PathValue("id")
	var result Draw
	err := app.store.update(func(state *State) error {
		for i := range state.Draws {
			if state.Draws[i].ID != id {
				continue
			}
			freshUser := state.Users[user.ID]
			if err := canJoin(state.Draws[i], &freshUser); err != nil {
				return err
			}
			freshUser.Points -= state.Draws[i].CostPoints
			state.Users[freshUser.ID] = freshUser
			state.Draws[i].Participants = append(state.Draws[i].Participants, Participation{
				ID:          "p-" + randomID(12),
				UserID:      freshUser.ID,
				Username:    freshUser.Username,
				DisplayName: freshUser.DisplayName,
				Level:       freshUser.Level,
				Note:        req.Note,
				JoinedAt:    time.Now().UTC(),
			})
			state.Draws[i].UpdatedAt = time.Now().UTC()
			state.Audit = append(state.Audit, AuditEvent{
				ID:        "audit-" + randomID(12),
				Type:      "draw.join",
				DrawID:    id,
				UserID:    freshUser.ID,
				Message:   freshUser.DisplayName + " 参与了 " + state.Draws[i].Title,
				CreatedAt: time.Now().UTC(),
			})
			result = state.Draws[i]
			return nil
		}
		return errors.New("抽奖不存在")
	})
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: result})
}

func (app *App) drawWinners(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录")
		return
	}
	if user.Role != "admin" {
		writeError(w, http.StatusForbidden, "只有管理员可以开奖")
		return
	}
	id := r.PathValue("id")
	var result Draw
	err := app.store.update(func(state *State) error {
		for i := range state.Draws {
			if state.Draws[i].ID != id {
				continue
			}
			draw := &state.Draws[i]
			if len(draw.Winners) > 0 {
				return errors.New("本次抽奖已经开奖")
			}
			if len(draw.Participants) == 0 {
				return errors.New("暂无参与用户，不能开奖")
			}
			winnerCount := draw.PrizeCount
			if winnerCount > len(draw.Participants) {
				winnerCount = len(draw.Participants)
			}
			picks, err := securePick(draw.Participants, winnerCount)
			if err != nil {
				return err
			}
			now := time.Now().UTC()
			draw.Winners = make([]Winner, 0, len(picks))
			for rank, p := range picks {
				draw.Winners = append(draw.Winners, Winner{
					Rank:        rank + 1,
					UserID:      p.UserID,
					Username:    p.Username,
					DisplayName: p.DisplayName,
					PrizeName:   draw.PrizeName,
					DrawnAt:     now,
				})
			}
			draw.Status = "closed"
			draw.UpdatedAt = now
			state.Audit = append(state.Audit, AuditEvent{
				ID:        "audit-" + randomID(12),
				Type:      "draw.closed",
				DrawID:    id,
				UserID:    user.ID,
				Message:   user.DisplayName + " 为 " + draw.Title + " 完成开奖",
				CreatedAt: now,
			})
			result = *draw
			return nil
		}
		return errors.New("抽奖不存在")
	})
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: result})
}

func securePick(participants []Participation, count int) ([]Participation, error) {
	pool := append([]Participation(nil), participants...)
	for i := len(pool) - 1; i > 0; i-- {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(i+1)))
		if err != nil {
			return nil, err
		}
		j := int(n.Int64())
		pool[i], pool[j] = pool[j], pool[i]
	}
	return pool[:count], nil
}

func (app *App) createDraw(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录")
		return
	}
	if user.Role != "admin" {
		writeError(w, http.StatusForbidden, "只有管理员可以创建抽奖")
		return
	}

	var req struct {
		Title            string   `json:"title"`
		Summary          string   `json:"summary"`
		PrizeName        string   `json:"prizeName"`
		PrizeType        string   `json:"prizeType"`
		PrizeCount       int      `json:"prizeCount"`
		Sponsor          string   `json:"sponsor"`
		StartsAt         string   `json:"startsAt"`
		EndsAt           string   `json:"endsAt"`
		MinLevel         int      `json:"minLevel"`
		CostPoints       int      `json:"costPoints"`
		ParticipantLimit int      `json:"participantLimit"`
		DiscussionURL    string   `json:"discussionUrl"`
		Rules            []string `json:"rules"`
	}
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	req.Title = strings.TrimSpace(req.Title)
	req.PrizeName = strings.TrimSpace(req.PrizeName)
	if req.Title == "" || req.PrizeName == "" {
		writeError(w, http.StatusBadRequest, "标题和奖品名称不能为空")
		return
	}
	if req.PrizeCount <= 0 {
		req.PrizeCount = 1
	}
	if req.Sponsor == "" {
		req.Sponsor = "Campus Pulse 社区"
	}
	startsAt := parseTimeOr(req.StartsAt, time.Now().UTC())
	endsAt := parseTimeOr(req.EndsAt, startsAt.Add(72*time.Hour))
	if !endsAt.After(startsAt) {
		writeError(w, http.StatusBadRequest, "结束时间必须晚于开始时间")
		return
	}
	if req.PrizeType == "" {
		req.PrizeType = "community-benefit"
	}
	rules := cleanRules(req.Rules)
	if len(rules) == 0 {
		rules = []string{"每个主站账号只能参与一次。", "开奖结果以本站记录为准。"}
	}
	status := "scheduled"
	now := time.Now().UTC()
	if !now.Before(startsAt) {
		status = "open"
	}

	draw := Draw{
		ID:               "lot-" + time.Now().Format("20060102") + "-" + randomID(6),
		Title:            req.Title,
		Summary:          strings.TrimSpace(req.Summary),
		PrizeName:        req.PrizeName,
		PrizeType:        req.PrizeType,
		PrizeCount:       req.PrizeCount,
		Sponsor:          req.Sponsor,
		Status:           status,
		StartsAt:         startsAt,
		EndsAt:           endsAt,
		MinLevel:         req.MinLevel,
		CostPoints:       req.CostPoints,
		ParticipantLimit: req.ParticipantLimit,
		DiscussionURL:    strings.TrimSpace(req.DiscussionURL),
		Rules:            rules,
		Participants:     []Participation{},
		Winners:          []Winner{},
		CreatedAt:        now,
		UpdatedAt:        now,
	}

	err := app.store.update(func(state *State) error {
		state.Draws = append(state.Draws, draw)
		state.Audit = append(state.Audit, AuditEvent{
			ID:        "audit-" + randomID(12),
			Type:      "draw.created",
			DrawID:    draw.ID,
			UserID:    user.ID,
			Message:   user.DisplayName + " 创建了抽奖 " + draw.Title,
			CreatedAt: now,
		})
		return nil
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, APIResponse{OK: true, Data: draw})
}

func cleanRules(rules []string) []string {
	out := make([]string, 0, len(rules))
	for _, rule := range rules {
		rule = strings.TrimSpace(rule)
		if rule != "" {
			out = append(out, rule)
		}
	}
	return out
}

func parseTimeOr(value string, fallback time.Time) time.Time {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	if t, err := time.Parse(time.RFC3339, value); err == nil {
		return t.UTC()
	}
	if t, err := time.Parse("2006-01-02 15:04", value); err == nil {
		return t.UTC()
	}
	return fallback
}

func (app *App) audit(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok || user.Role != "admin" {
		writeError(w, http.StatusForbidden, "只有管理员可以查看审计记录")
		return
	}
	state := app.store.snapshot()
	sort.Slice(state.Audit, func(i, j int) bool {
		return state.Audit[i].CreatedAt.After(state.Audit[j].CreatedAt)
	})
	if len(state.Audit) > 80 {
		state.Audit = state.Audit[:80]
	}
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: state.Audit})
}

func (app *App) announcement(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok || user.Role != "admin" {
		writeError(w, http.StatusForbidden, "只有管理员可以导出公告")
		return
	}
	id := r.PathValue("id")
	state := app.store.snapshot()
	for _, draw := range state.Draws {
		if draw.ID != id {
			continue
		}
		if len(draw.Winners) == 0 {
			writeError(w, http.StatusBadRequest, "该抽奖暂未开奖，不能导出公告")
			return
		}
		writeJSON(w, http.StatusOK, APIResponse{
			OK: true,
			Data: map[string]any{
				"drawId":       draw.ID,
				"title":        draw.Title,
				"announcement": buildAnnouncement(draw),
			},
		})
		return
	}
	writeError(w, http.StatusNotFound, "抽奖不存在")
}

func buildAnnouncement(draw Draw) string {
	var b strings.Builder
	b.WriteString("## ")
	b.WriteString(draw.Title)
	b.WriteString(" 开奖公告\n\n")
	b.WriteString("本次社区福利抽奖已完成开奖，感谢大家参与。\n\n")
	b.WriteString("- 奖品：")
	b.WriteString(draw.PrizeName)
	b.WriteString("\n")
	b.WriteString("- 名额：")
	b.WriteString(strconv.Itoa(len(draw.Winners)))
	b.WriteString("/")
	b.WriteString(strconv.Itoa(draw.PrizeCount))
	b.WriteString("\n")
	b.WriteString("- 参与人数：")
	b.WriteString(strconv.Itoa(len(draw.Participants)))
	b.WriteString("\n")
	b.WriteString("- 开奖时间：")
	b.WriteString(draw.Winners[0].DrawnAt.Local().Format("2006-01-02 15:04"))
	b.WriteString("\n\n")
	b.WriteString("### 中奖名单\n\n")
	for _, winner := range draw.Winners {
		b.WriteString(strconv.Itoa(winner.Rank))
		b.WriteString(". ")
		b.WriteString(winner.DisplayName)
		if winner.Username != "" {
			b.WriteString(" (@")
			b.WriteString(winner.Username)
			b.WriteString(")")
		}
		b.WriteString("\n")
	}
	b.WriteString("\n请中奖用户留意社区站内通知或管理员私信，按活动规则完成领取确认。")
	if draw.DiscussionURL != "" {
		b.WriteString("\n\n活动讨论帖：")
		b.WriteString(draw.DiscussionURL)
	}
	return b.String()
}

func (app *App) currentUser(r *http.Request) (*User, bool) {
	session, ok := app.currentSession(r)
	if !ok {
		return nil, false
	}
	return app.userByID(session.UserID)
}

func (app *App) userByID(userID string) (*User, bool) {
	state := app.store.snapshot()
	user, ok := state.Users[userID]
	if !ok {
		return nil, false
	}
	return &user, true
}

func (app *App) currentSession(r *http.Request) (Session, bool) {
	cookie, err := r.Cookie(sessionCookie)
	if err != nil || cookie.Value == "" {
		return Session{}, false
	}
	app.mu.Lock()
	session, ok := app.sessions[cookie.Value]
	app.mu.Unlock()
	if !ok {
		return Session{}, false
	}
	if time.Now().UTC().After(session.ExpiresAt) {
		app.mu.Lock()
		delete(app.sessions, cookie.Value)
		app.mu.Unlock()
		if err := app.store.update(func(state *State) error {
			delete(state.Sessions, cookie.Value)
			return nil
		}); err != nil {
			log.Printf("delete expired session: %v", err)
		}
		return Session{}, false
	}
	if session.Token == "" {
		session.Token = cookie.Value
	}
	return session, true
}

func (app *App) issueSession(w http.ResponseWriter, userID string, communityAccessToken string) {
	token := randomID(32)
	session := Session{
		Token:                token,
		UserID:               userID,
		CommunityAccessToken: strings.TrimSpace(communityAccessToken),
		ExpiresAt:            time.Now().UTC().Add(7 * 24 * time.Hour),
	}
	app.mu.Lock()
	app.sessions[token] = session
	app.mu.Unlock()
	if err := app.store.update(func(state *State) error {
		if state.Sessions == nil {
			state.Sessions = map[string]Session{}
		}
		state.Sessions[token] = session
		return nil
	}); err != nil {
		log.Printf("persist session: %v", err)
	}
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookie,
		Value:    token,
		Path:     "/",
		Expires:  session.ExpiresAt,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
}

func writeJSON(w http.ResponseWriter, status int, resp APIResponse) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(resp)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, APIResponse{OK: false, Error: message})
}

func decodeJSON(r *http.Request, dst any) error {
	if r.Body == nil {
		return nil
	}
	defer r.Body.Close()
	body, err := io.ReadAll(io.LimitReader(r.Body, 1<<20))
	if err != nil {
		return err
	}
	if len(strings.TrimSpace(string(body))) == 0 {
		return nil
	}
	return json.Unmarshal(body, dst)
}

func randomID(size int) string {
	buf := make([]byte, size)
	if _, err := rand.Read(buf); err != nil {
		return strconv.FormatInt(time.Now().UnixNano(), 36)
	}
	return strings.TrimRight(base64.RawURLEncoding.EncodeToString(buf), "=")
}

func stableID(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])[:16]
}

func signValue(secret, value string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(value))
	return hex.EncodeToString(mac.Sum(nil))
}

func validSignedState(secret, value string) bool {
	parts := strings.Split(value, ".")
	if len(parts) != 2 {
		return false
	}
	expected := signValue(secret, parts[0])
	return hmac.Equal([]byte(expected), []byte(parts[1]))
}

func NewLotteryRuntime(cfg Config) *LotteryRuntime {
	return &LotteryRuntime{
		topics:  map[string]TopicPreview{},
		results: map[string]LotteryDrawResult{},
		bot: BotAccount{
			ID:          "zens-lottery-bot",
			Username:    cfg.BotUsername,
			DisplayName: "Zens 抽奖机器人",
			Avatar:      "抽",
			Status:      "ready",
		},
	}
}

func (app *App) lotteryPreview(w http.ResponseWriter, r *http.Request) {
	app.syncLotteryComments(w, r)
}

func (app *App) syncLotteryComments(w http.ResponseWriter, r *http.Request) {
	session, ok := app.currentSession(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号后再同步帖子评论")
		return
	}
	if _, ok := app.userByID(session.UserID); !ok {
		writeError(w, http.StatusUnauthorized, "登录状态已失效，请重新连接社区账号")
		return
	}
	var req lotteryCriteria
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	preview, _, err := app.buildTopicPreview(req, session.CommunityAccessToken)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	app.lottery.mu.Lock()
	app.lottery.topics[cacheKeyForTopic(req.TopicURL)] = preview
	app.lottery.mu.Unlock()
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: preview})
}

func (app *App) drawTopicLottery(w http.ResponseWriter, r *http.Request) {
	session, ok := app.currentSession(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号后再开奖")
		return
	}
	if _, ok := app.userByID(session.UserID); !ok {
		writeError(w, http.StatusUnauthorized, "登录状态已失效，请重新连接社区账号")
		return
	}
	var req lotteryDrawRequest
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if req.WinnerCount <= 0 {
		writeError(w, http.StatusBadRequest, "中奖人数必须大于 0")
		return
	}
	preview, postID, err := app.buildTopicPreview(req.lotteryCriteria, session.CommunityAccessToken)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if len(preview.Participants) == 0 {
		writeError(w, http.StatusBadRequest, "当前帖子没有有效参与者")
		return
	}
	if req.WinnerCount > len(preview.Participants) {
		req.WinnerCount = len(preview.Participants)
	}
	participantHash := participantSnapshotHash(preview.Participants)
	resultKey := topicDrawKey(postID, participantHash, req.WinnerCount)
	state := app.store.snapshot()
	if existing, ok := state.TopicDraws[resultKey]; ok && len(existing.Winners) > 0 {
		app.lottery.mu.Lock()
		app.lottery.topics[cacheKeyForTopic(req.TopicURL)] = preview
		app.lottery.results[existing.DrawID] = existing
		app.lottery.mu.Unlock()
		writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: existing})
		return
	}
	picks, seed, proof, err := securePickLottery(preview.Participants, req.WinnerCount)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	now := time.Now().UTC()
	result := LotteryDrawResult{
		DrawID:           "zens-draw-" + randomID(10),
		TopicURL:         strings.TrimSpace(req.TopicURL),
		PostID:           postID,
		Seed:             seed,
		Algorithm:        lotteryAlgorithm,
		ParticipantHash:  participantHash,
		Proof:            proof,
		ParticipantCount: len(preview.Participants),
		Winners:          picks,
		CreatedAt:        now.Format(time.RFC3339),
	}
	if err := app.store.update(func(state *State) error {
		if state.TopicDraws == nil {
			state.TopicDraws = map[string]LotteryDrawResult{}
		}
		if existing, ok := state.TopicDraws[resultKey]; ok && len(existing.Winners) > 0 {
			result = existing
			return nil
		}
		state.TopicDraws[resultKey] = result
		return nil
	}); err != nil {
		writeError(w, http.StatusInternalServerError, "保存抽奖结果失败："+err.Error())
		return
	}
	app.lottery.mu.Lock()
	app.lottery.topics[cacheKeyForTopic(req.TopicURL)] = preview
	app.lottery.results[result.DrawID] = result
	app.lottery.mu.Unlock()
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: result})
}

func (app *App) lotteryBotAccount(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: app.lottery.bot})
}

func (app *App) lotteryBotAccountApply(w http.ResponseWriter, r *http.Request) {
	if user, ok := app.currentUser(r); !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号")
		return
	} else if !canOperateOfficialLottery(user) {
		writeError(w, http.StatusForbidden, "只有管理员或版主可以配置抽奖机器人")
		return
	}
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: app.lottery.bot})
}

func (app *App) publishLotteryResult(w http.ResponseWriter, r *http.Request) {
	user, ok := app.currentUser(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号后再发布中奖名单")
		return
	}
	if !canOperateOfficialLottery(user) {
		writeError(w, http.StatusForbidden, "只有管理员或版主可以发布官方中奖名单")
		return
	}
	var req publishResultRequest
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if strings.TrimSpace(req.DrawID) == "" {
		writeError(w, http.StatusBadRequest, "缺少抽奖结果")
		return
	}
	postID, err := postIDFromTopicURL(req.TopicURL)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	result, ok := app.findStoredLotteryResult(req.DrawID)
	if !ok {
		writeError(w, http.StatusBadRequest, "抽奖结果不存在或已失效，请重新开奖后再发布")
		return
	}
	if result.PostID != "" && result.PostID != postID {
		writeError(w, http.StatusBadRequest, "抽奖结果与当前帖子不匹配")
		return
	}
	content := buildTopicLotteryComment(publishRequestFromResult(result, req.BotAccountID))
	token, err := app.botAccessToken()
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := app.createCommunityComment(postID, content, token); err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	// 开奖事件回流主站:写 SubsiteEvent 账本 + 给中奖者发站内通知。
	// 优雅降级——失败只记日志,不影响已成功的评论发布与后续流程。
	app.reportLotteryWinEvents(result, postID)
	if err := app.closeCommunityLotteryComments(postID, token); err != nil {
		writeError(w, http.StatusBadGateway, "中奖名单已发布，但关闭原帖评论失败："+err.Error())
		return
	}
	comments, err := app.fetchCommunityComments(postID, token)
	if err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	commentID := ""
	commentFloor := len(comments)
	if len(comments) > 0 {
		last := comments[len(comments)-1]
		commentID = last.ID
	}
	postedAt := time.Now().UTC()
	resp := PublishedComment{
		CommentID:    commentID,
		CommentURL:   buildCommunityCommentURL(app.cfg.CommunityBase, postID, commentID),
		CommentFloor: commentFloor,
		BotName:      app.lottery.bot.DisplayName,
		PostedAt:     postedAt.Format(time.RFC3339),
	}
	writeJSON(w, http.StatusOK, APIResponse{OK: true, Data: resp})
}

func (app *App) findStoredLotteryResult(drawID string) (LotteryDrawResult, bool) {
	drawID = strings.TrimSpace(drawID)
	if drawID == "" {
		return LotteryDrawResult{}, false
	}
	app.lottery.mu.Lock()
	if result, ok := app.lottery.results[drawID]; ok {
		app.lottery.mu.Unlock()
		return result, true
	}
	app.lottery.mu.Unlock()
	state := app.store.snapshot()
	for _, result := range state.TopicDraws {
		if result.DrawID == drawID {
			return result, true
		}
	}
	return LotteryDrawResult{}, false
}

func publishRequestFromResult(result LotteryDrawResult, botAccountID string) publishResultRequest {
	return publishResultRequest{
		TopicURL:         result.TopicURL,
		DrawID:           result.DrawID,
		Seed:             result.Seed,
		Algorithm:        result.Algorithm,
		ParticipantHash:  result.ParticipantHash,
		Proof:            result.Proof,
		ParticipantCount: result.ParticipantCount,
		Winners:          result.Winners,
		BotAccountID:     botAccountID,
	}
}

func canOperateOfficialLottery(user *User) bool {
	if user == nil {
		return false
	}
	role := strings.ToLower(strings.TrimSpace(user.Role))
	return role == "admin" || role == "super_admin" || role == "moderator"
}

func (app *App) buildTopicPreview(req lotteryCriteria, token string) (TopicPreview, string, error) {
	postID, err := postIDFromTopicURL(req.TopicURL)
	if err != nil {
		return TopicPreview{}, "", err
	}
	post, err := app.fetchCommunityPost(postID, token)
	if err != nil {
		return TopicPreview{}, "", err
	}
	comments, err := app.fetchCommunityComments(postID, token)
	if err != nil {
		return TopicPreview{}, "", err
	}
	participants := commentsToParticipants(comments, post, req)
	lastFloor := len(comments)
	preview := TopicPreview{
		TopicTitle:       post.Title,
		Author:           fallback(post.AuthorName, "Zens 用户"),
		ReplyCount:       post.CommentCount,
		ParticipantCount: len(participants),
		LastFloor:        lastFloor,
		SyncedAt:         time.Now().UTC().Format(time.RFC3339),
		CommentSource:    "帖子回复用户",
		Participants:     participants,
	}
	if preview.ReplyCount == 0 {
		preview.ReplyCount = len(comments)
	}
	return preview, postID, nil
}

func (app *App) fetchCommunityPost(postID string, token string) (communityPost, error) {
	var resp communityResult[communityPost]
	if err := app.communityGet("/post/"+url.PathEscape(postID), token, &resp); err != nil {
		return communityPost{}, err
	}
	if resp.Code != 2000 {
		return communityPost{}, fmt.Errorf("主站读取帖子失败：%s", fallback(resp.Message, "未知错误"))
	}
	if resp.Data.ID == "" {
		resp.Data.ID = postID
	}
	return resp.Data, nil
}

func (app *App) fetchCommunityComments(postID string, token string) ([]communityComment, error) {
	all := []communityComment{}
	page := 1
	const pageSize = 200
	maxPages := app.cfg.CommentMaxPages
	if maxPages <= 0 {
		maxPages = 50
	}
	for {
		var resp communityResult[communityCommentPage]
		path := fmt.Sprintf("/comment/post/%s?page=%d&size=%d", url.PathEscape(postID), page, pageSize)
		if err := app.communityGet(path, token, &resp); err != nil {
			return nil, err
		}
		if resp.Code != 2000 {
			return nil, fmt.Errorf("主站读取评论失败：%s", fallback(resp.Message, "未知错误"))
		}
		all = append(all, flattenCommunityComments(resp.Data.Records)...)
		total := int(resp.Data.Total)
		pages := int(resp.Data.Pages)
		if pages > 0 {
			if page >= pages {
				break
			}
		} else if total > 0 {
			if len(all) >= total {
				break
			}
		} else if len(resp.Data.Records) < pageSize {
			break
		}
		if page >= maxPages {
			return nil, errors.New("帖子评论过多，请设置截止楼层或分批处理")
		}
		page++
	}
	sort.SliceStable(all, func(i, j int) bool {
		return compareCommunityTime(all[i].CreateTime, all[j].CreateTime) < 0
	})
	return all, nil
}

func flattenCommunityComments(records []communityComment) []communityComment {
	out := make([]communityComment, 0, len(records))
	var walk func(items []communityComment)
	walk = func(items []communityComment) {
		for _, item := range items {
			children := item.Children
			item.Children = nil
			out = append(out, item)
			if len(children) > 0 {
				walk(children)
			}
		}
	}
	walk(records)
	return out
}

func compareCommunityTime(a, b string) int {
	ta := parseCommunityTime(a)
	tb := parseCommunityTime(b)
	switch {
	case ta.IsZero() && tb.IsZero():
		return strings.Compare(a, b)
	case ta.IsZero():
		return 1
	case tb.IsZero():
		return -1
	case ta.Before(tb):
		return -1
	case ta.After(tb):
		return 1
	default:
		return 0
	}
}

func parseCommunityTime(value string) time.Time {
	value = strings.TrimSpace(value)
	if value == "" {
		return time.Time{}
	}
	layouts := []string{
		time.RFC3339,
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
		"2006-01-02 15:04",
	}
	for _, layout := range layouts {
		if parsed, err := time.ParseInLocation(layout, value, time.Local); err == nil {
			return parsed
		}
	}
	return time.Time{}
}

func (app *App) communityGet(path, token string, dst any) error {
	return app.communityGetFromURLs(app.communityURLCandidates(path), token, dst)
}

func (app *App) communityGetFromURLs(candidates []string, token string, dst any) error {
	client := http.Client{Timeout: 10 * time.Second}
	var lastErr error
	for _, target := range candidates {
		req, err := http.NewRequest(http.MethodGet, target, nil)
		if err != nil {
			lastErr = err
			continue
		}
		req.Header.Set("Accept", "application/json")
		req.Header.Set("X-Device-Id", "zens-lottery-station")
		if strings.TrimSpace(token) != "" {
			req.Header.Set("Authorization", "Bearer "+strings.TrimSpace(token))
		}
		resp, err := client.Do(req)
		if err != nil {
			lastErr = fmt.Errorf("无法连接主社区 %s：%w", target, err)
			continue
		}
		body, readErr := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
		_ = resp.Body.Close()
		if readErr != nil {
			lastErr = readErr
			continue
		}
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			lastErr = fmt.Errorf("主社区返回错误：%s（%s）%s", resp.Status, target, communityErrorSnippet(body))
			if resp.StatusCode == http.StatusNotFound {
				continue
			}
			if resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden {
				// 帖子/评论读取在主站是公开端点,正常无需用户 token。出现 401/403 多半是
				// 端点路径不对或主站白名单变动,而非"SSO token 过期"——别往 token 方向排查。
				log.Printf("社区读取被拒(%s):%s。注意社区读取走公开端点,不依赖用户 token", resp.Status, target)
			}
			return lastErr
		}
		if !json.Valid(body) {
			lastErr = fmt.Errorf("主社区响应不是 JSON：%s（%s）", http.DetectContentType(body), target)
			continue
		}
		if err := json.Unmarshal(body, dst); err != nil {
			return fmt.Errorf("主社区响应解析失败：%w（%s）", err, target)
		}
		return nil
	}
	if lastErr != nil {
		return lastErr
	}
	return errors.New("主社区 API 地址未配置")
}

func (app *App) communityURLCandidates(path string) []string {
	path = normalizeAPIPath(path)
	base := strings.TrimRight(app.cfg.CommunityAPI, "/")
	candidates := make([]string, 0, 2)
	if base != "" {
		candidates = append(candidates, base+path)
		if !strings.HasSuffix(base, "/api") {
			candidates = append(candidates, base+"/api"+path)
		}
	}
	return uniqueStrings(candidates)
}

func normalizeAPIPath(path string) string {
	path = strings.TrimSpace(path)
	if path == "" {
		return "/"
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return path
}

func uniqueStrings(values []string) []string {
	seen := map[string]bool{}
	out := make([]string, 0, len(values))
	for _, value := range values {
		if strings.TrimSpace(value) == "" || seen[value] {
			continue
		}
		seen[value] = true
		out = append(out, value)
	}
	return out
}

func communityErrorSnippet(body []byte) string {
	body = bytes.TrimSpace(body)
	if len(body) == 0 {
		return ""
	}
	var parsed struct {
		Message string `json:"message"`
		Error   string `json:"error"`
	}
	if json.Unmarshal(body, &parsed) == nil {
		msg := firstNonEmpty(parsed.Message, parsed.Error)
		if msg != "" {
			return "：" + clipPlainText(msg, 120)
		}
	}
	return "：" + clipPlainText(string(body), 120)
}

func (app *App) communityGetWithToken(path, token string, dst any) error {
	req, err := http.NewRequest(http.MethodGet, app.communityURL(path), nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("X-Device-Id", "zens-lottery-station")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	client := http.Client{Timeout: 8 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("无法连接主社区：%w", err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("主社区返回错误：%s", resp.Status)
	}
	if err := json.Unmarshal(body, dst); err != nil {
		return fmt.Errorf("主社区响应解析失败：%w", err)
	}
	return nil
}

func (app *App) fetchCommunitySimpleProfile(token string) (communitySimpleProfile, error) {
	var resp communityResult[communitySimpleProfile]
	if err := app.communityGetWithToken("/user/simple-profile", token, &resp); err != nil {
		return communitySimpleProfile{}, err
	}
	if resp.Code != 2000 {
		return communitySimpleProfile{}, fmt.Errorf("主站读取用户信息失败：%s", fallback(resp.Message, "未知错误"))
	}
	return resp.Data, nil
}

func mergeCommunityProfile(user User, profile communitySimpleProfile) User {
	if profile.ID != "" {
		user.ID = profile.ID
	}
	if profile.Username != "" {
		user.Username = profile.Username
	}
	if profile.Nickname != "" {
		user.DisplayName = profile.Nickname
	}
	if profile.Avatar != "" {
		user.Avatar = profile.Avatar
	}
	if profile.Level > 0 {
		user.Level = profile.Level
	}
	if profile.Points >= 0 {
		user.Points = profile.Points
	}
	return user
}

func (app *App) communityPost(path string, payload any, token string, dst any) error {
	raw, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, app.communityURL(path), bytes.NewReader(raw))
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Device-Id", "zens-lottery-station")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	client := http.Client{Timeout: 12 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("无法连接主社区：%w", err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("主社区返回错误：%s %s", resp.Status, strings.TrimSpace(string(body)))
	}
	if dst == nil {
		return nil
	}
	if err := json.Unmarshal(body, dst); err != nil {
		return fmt.Errorf("主社区响应解析失败：%w", err)
	}
	return nil
}

// ─── 内部 s2s API：开奖事件回流 ───────────────────────────────────────────
//
// 与主站 InternalServiceFilter 对应,签名串逐字节对齐 zdc-shop/src/lib/main-site/hmac.ts:
//   payload   = METHOD + "\n" + PATH + "\n" + TIMESTAMP(ms) + "\n" + NONCE + "\n" + sha256Hex(BODY)
//   signature = hex(HMAC_SHA256(LOTTERY_SERVICE_SECRET, payload))  // 全小写
// 主站需在 internal.service.lottery-secret 配置同一密钥,并把 campus-lottery-station 加入白名单。

const internalSubsiteEventsPath = "/api/internal/subsite/events"

var eventIDInvalidChars = regexp.MustCompile(`[^a-z0-9._:-]+`)

type subsiteEventReq struct {
	EventID    string `json:"eventId"`
	Source     string `json:"source"`
	EventType  string `json:"eventType"`
	UserID     string `json:"userId,omitempty"`
	Title      string `json:"title"`
	Content    string `json:"content"`
	RelatedID  string `json:"relatedId,omitempty"`
	Severity   string `json:"severity,omitempty"`
	NotifyUser bool   `json:"notifyUser"`
}

// signedInternalHeaders 计算调主站 /api/internal/** 所需的 HMAC 头。
func (app *App) signedInternalHeaders(method, path, body string) map[string]string {
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
	mac := hmac.New(sha256.New, []byte(app.cfg.ServiceSecret))
	mac.Write([]byte(payload))
	return map[string]string{
		"X-Service-Id":        app.cfg.ServiceID,
		"X-Service-Timestamp": ts,
		"X-Service-Nonce":     nonce,
		"X-Service-Signature": hex.EncodeToString(mac.Sum(nil)),
	}
}

// sanitizeEventID 把任意串规整成主站要求的 eventId(8–120 字符, ^[a-z0-9._:-]+$),用于幂等去重。
func sanitizeEventID(raw string) string {
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

// reportLotteryWinEvents 把一次开奖的全部中奖者回流主站(写账本 + 通知)。
// 优雅降级:未配置密钥则跳过;单个失败只记日志,绝不阻断已成功的评论发布。
func (app *App) reportLotteryWinEvents(result LotteryDrawResult, postID string) {
	if strings.TrimSpace(app.cfg.ServiceSecret) == "" {
		log.Printf("未配置 LOTTERY_SERVICE_SECRET,跳过开奖事件回流 drawId=%s", result.DrawID)
		return
	}
	for _, winner := range result.Winners {
		if err := app.reportLotteryWinEvent(winner, result, postID); err != nil {
			log.Printf("开奖事件回流失败 drawId=%s userId=%s: %v", result.DrawID, winner.ID, err)
		}
	}
}

func (app *App) reportLotteryWinEvent(winner LotteryWinner, result LotteryDrawResult, postID string) error {
	if strings.TrimSpace(winner.ID) == "" {
		return nil // 匿名或无主站用户 id 的中奖者无法精确通知,跳过
	}
	reqBody := subsiteEventReq{
		EventID:    sanitizeEventID(fmt.Sprintf("lottery:%s:win:%s", result.DrawID, winner.ID)),
		Source:     app.cfg.ServiceID,
		EventType:  "lottery.draw.win",
		UserID:     winner.ID,
		Title:      "🎉 抽奖中奖通知",
		Content:    fmt.Sprintf("恭喜你在社区抽奖活动中入选(第 %d 名)。开奖结果已公示在原帖,请留意站内通知。", winner.Rank),
		RelatedID:  postID,
		Severity:   "success",
		NotifyUser: true,
	}
	raw, _ := json.Marshal(reqBody)
	bodyStr := string(raw)

	// 指数退避重试:网络错/5xx 重试,4xx 不重试(幂等 eventId 保证重复安全)。
	// 每次重试重新签名——timestamp(±60s 窗口)与 nonce(单次)都不能复用。
	const maxAttempts = 3
	var lastErr error
	for attempt := 1; attempt <= maxAttempts; attempt++ {
		retryable, err := app.postSignedInternal(internalSubsiteEventsPath, bodyStr)
		if err == nil {
			if attempt > 1 {
				log.Printf("开奖事件回流第 %d 次重试成功 eventId=%s", attempt, reqBody.EventID)
			}
			return nil
		}
		lastErr = err
		if !retryable || attempt == maxAttempts {
			break
		}
		backoff := time.Duration(200*(1<<(attempt-1))) * time.Millisecond
		jitter := time.Duration(time.Now().UnixNano()%120) * time.Millisecond
		log.Printf("开奖事件回流第 %d 次失败,%v 后重试 eventId=%s: %v", attempt, backoff+jitter, reqBody.EventID, err)
		time.Sleep(backoff + jitter)
	}
	return lastErr
}

// postSignedInternal 向主站内部接口发一次签名 POST。
// 返回 retryable:网络错误/5xx 为 true(值得重试),4xx 为 false(请求本身有问题)。
func (app *App) postSignedInternal(path, bodyStr string) (retryable bool, err error) {
	req, err := http.NewRequest(http.MethodPost, app.communityURL(path), strings.NewReader(bodyStr))
	if err != nil {
		return false, err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")
	for k, v := range app.signedInternalHeaders(http.MethodPost, path, bodyStr) {
		req.Header.Set(k, v)
	}
	client := http.Client{Timeout: 12 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return true, fmt.Errorf("无法连接主站内部接口：%w", err) // 网络层错误可重试
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return false, nil
	}
	canRetry := resp.StatusCode >= 500 // 5xx 可重试,4xx 不重试
	return canRetry, fmt.Errorf("主站内部接口返回错误：%s %s", resp.Status, strings.TrimSpace(string(respBody)))
}

func (app *App) communityURL(path string) string {
	base := strings.TrimRight(app.cfg.CommunityAPI, "/")
	return base + normalizeAPIPath(path)
}

func commentsToParticipants(comments []communityComment, post communityPost, req lotteryCriteria) []LotteryParticipant {
	out := make([]LotteryParticipant, 0, len(comments))
	seen := map[string]bool{}
	maxFloor := 0
	if req.MaxFloor != nil {
		maxFloor = *req.MaxFloor
	}
	for index, comment := range comments {
		floor := index + 1
		if maxFloor > 0 && floor > maxFloor {
			continue
		}
		if !isValidLotteryComment(comment) {
			continue
		}
		if req.ExcludeAuthor && comment.UserID != "" && comment.UserID == post.UserID {
			continue
		}
		if req.DedupeUser && comment.UserID != "" {
			if seen[comment.UserID] {
				continue
			}
			seen[comment.UserID] = true
		}
		displayName := fallback(comment.Nickname, "Zens 用户")
		out = append(out, LotteryParticipant{
			ID:          comment.UserID,
			Username:    fallback(comment.Nickname, comment.UserID),
			DisplayName: displayName,
			Avatar:      fallback(comment.UserAvatar, avatarLetter(displayName)),
			Floor:       floor,
			RepliedAt:   normalizeCommunityTime(comment.CreateTime),
			Excerpt:     clipPlainText(comment.Content, 64),
		})
	}
	return out
}

func isValidLotteryComment(comment communityComment) bool {
	if comment.UserID == "" || comment.IsAnonymous == 1 {
		return false
	}
	if strings.EqualFold(comment.AuditStatus, "deleted") || strings.EqualFold(comment.AuditStatus, "rejected") {
		return false
	}
	return true
}

func securePickLottery(participants []LotteryParticipant, count int) ([]LotteryWinner, string, string, error) {
	pool := append([]LotteryParticipant(nil), participants...)
	seedBytes := make([]byte, lotterySeedSize)
	if _, err := rand.Read(seedBytes); err != nil {
		return nil, "", "", err
	}
	seed := "zens-" + hex.EncodeToString(seedBytes)
	randomStream := newDeterministicRandomStream(seedBytes, participantSnapshotHash(participants))
	for i := len(pool) - 1; i > 0; i-- {
		j, err := randomStream.Intn(i + 1)
		if err != nil {
			return nil, "", "", err
		}
		pool[i], pool[j] = pool[j], pool[i]
	}
	winners := make([]LotteryWinner, 0, count)
	for i := 0; i < count; i++ {
		winners = append(winners, LotteryWinner{
			LotteryParticipant: pool[i],
			Rank:               i + 1,
		})
	}
	proof := lotteryProof(seed, participants, winners)
	return winners, seed, proof, nil
}

type deterministicRandomStream struct {
	key     []byte
	context string
	counter uint64
}

func newDeterministicRandomStream(seed []byte, context string) *deterministicRandomStream {
	return &deterministicRandomStream{
		key:     append([]byte(nil), seed...),
		context: context,
	}
}

func (s *deterministicRandomStream) Intn(max int) (int, error) {
	if max <= 0 {
		return 0, errors.New("random max must be positive")
	}
	limit := uint64(max)
	threshold := ^uint64(0) - (^uint64(0) % limit)
	for {
		block := s.nextBlock()
		value := binary.BigEndian.Uint64(block[:8])
		if value < threshold {
			return int(value % limit), nil
		}
	}
}

func (s *deterministicRandomStream) nextBlock() []byte {
	var counter [8]byte
	binary.BigEndian.PutUint64(counter[:], s.counter)
	s.counter++

	mac := hmac.New(sha256.New, s.key)
	_, _ = mac.Write([]byte(lotteryAlgorithm))
	_, _ = mac.Write([]byte{0})
	_, _ = mac.Write([]byte(s.context))
	_, _ = mac.Write([]byte{0})
	_, _ = mac.Write(counter[:])
	return mac.Sum(nil)
}

func participantSnapshotHash(participants []LotteryParticipant) string {
	type participantDigest struct {
		ID          string `json:"id"`
		Username    string `json:"username"`
		DisplayName string `json:"displayName"`
		Floor       int    `json:"floor"`
		RepliedAt   string `json:"repliedAt"`
	}
	digest := make([]participantDigest, 0, len(participants))
	for _, participant := range participants {
		digest = append(digest, participantDigest{
			ID:          participant.ID,
			Username:    participant.Username,
			DisplayName: participant.DisplayName,
			Floor:       participant.Floor,
			RepliedAt:   participant.RepliedAt,
		})
	}
	raw, _ := json.Marshal(digest)
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func lotteryProof(seed string, participants []LotteryParticipant, winners []LotteryWinner) string {
	payload := struct {
		Algorithm       string          `json:"algorithm"`
		Seed            string          `json:"seed"`
		ParticipantHash string          `json:"participantHash"`
		Winners         []LotteryWinner `json:"winners"`
	}{
		Algorithm:       lotteryAlgorithm,
		Seed:            seed,
		ParticipantHash: participantSnapshotHash(participants),
		Winners:         winners,
	}
	raw, _ := json.Marshal(payload)
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func topicDrawKey(postID, participantHash string, winnerCount int) string {
	sum := sha256.Sum256([]byte(strings.Join([]string{
		strings.TrimSpace(postID),
		participantHash,
		strconv.Itoa(winnerCount),
		lotteryAlgorithm,
	}, "|")))
	return hex.EncodeToString(sum[:])
}

func (app *App) botAccessToken() (string, error) {
	if app.cfg.BotAccessToken != "" {
		return app.cfg.BotAccessToken, nil
	}
	if app.cfg.BotUsername == "" || app.cfg.BotPassword == "" {
		return "", errors.New("未配置机器人凭据：请设置 LOTTERY_BOT_ACCESS_TOKEN，或设置 LOTTERY_BOT_USERNAME 与 LOTTERY_BOT_PASSWORD")
	}
	var resp communityResult[struct {
		AccessToken string `json:"accessToken"`
	}]
	payload := map[string]any{
		"loginType":  "password",
		"account":    app.cfg.BotUsername,
		"password":   app.cfg.BotPassword,
		"rememberMe": true,
	}
	if err := app.communityPost("/auth/login", payload, "", &resp); err != nil {
		return "", err
	}
	if resp.Code != 2000 || resp.Data.AccessToken == "" {
		return "", fmt.Errorf("机器人登录失败：%s", fallback(resp.Message, "未返回 accessToken"))
	}
	return resp.Data.AccessToken, nil
}

func (app *App) createCommunityComment(postID, content, token string) error {
	var resp communityResult[any]
	payload := map[string]any{
		"postId":      postID,
		"content":     content,
		"parentId":    "0",
		"isAnonymous": 0,
	}
	if err := app.communityPost("/comment/create", payload, token, &resp); err != nil {
		return err
	}
	if resp.Code != 2000 {
		return fmt.Errorf("机器人发布评论失败：%s", fallback(resp.Message, "未知错误"))
	}
	return nil
}

func (app *App) closeCommunityLotteryComments(postID, token string) error {
	var resp communityResult[any]
	payload := map[string]any{
		"postId":             postID,
		"postType":           "LOTTERY",
		"commentDeadline":    time.Now().Add(-time.Second).Format("2006-01-02T15:04:05"),
		"commentOncePerUser": true,
	}
	if err := app.communityPost("/post/update-post", payload, token, &resp); err != nil {
		return err
	}
	if resp.Code != 2000 {
		return fmt.Errorf("主站关闭评论失败：%s", fallback(resp.Message, "未知错误"))
	}
	return nil
}

func buildTopicLotteryComment(req publishResultRequest) string {
	var b strings.Builder
	b.WriteString("## Zens 社区抽取结果\n\n")
	b.WriteString(fmt.Sprintf("本次活动基于帖子回复数据生成，共 %d 名有效参与者，抽取 %d 名入选用户。\n\n", req.ParticipantCount, len(req.Winners)))
	if req.Algorithm != "" {
		b.WriteString("- 随机算法：")
		b.WriteString(req.Algorithm)
		b.WriteString("\n")
	}
	b.WriteString("- 随机种子：")
	b.WriteString(req.Seed)
	b.WriteString("\n")
	if req.ParticipantHash != "" {
		b.WriteString("- 名单快照哈希：")
		b.WriteString(req.ParticipantHash)
		b.WriteString("\n")
	}
	if req.Proof != "" {
		b.WriteString("- 结果证明哈希：")
		b.WriteString(req.Proof)
		b.WriteString("\n")
	}
	b.WriteString("- 规则：排除发帖人、同一用户只计入一次，并按设置的截止楼层统计。\n\n")
	b.WriteString("### 入选名单\n\n")
	for _, winner := range req.Winners {
		b.WriteString(fmt.Sprintf("%d. %s（%s 楼，回复时间 %s）\n", winner.Rank, winner.DisplayName, strconv.Itoa(winner.Floor), formatCommunityDisplayTime(winner.RepliedAt)))
	}
	b.WriteString("\n请入选用户留意社区站内通知或管理员私信。")
	return b.String()
}

func postIDFromTopicURL(raw string) (string, error) {
	value := strings.TrimSpace(raw)
	if value == "" {
		return "", errors.New("帖子链接不能为空")
	}
	if regexp.MustCompile(`^[A-Za-z0-9_-]{3,}$`).MatchString(value) {
		return value, nil
	}
	u, err := url.Parse(value)
	if err != nil {
		return "", errors.New("帖子链接格式不正确")
	}
	segments := strings.Split(strings.Trim(u.Path, "/"), "/")
	for i, segment := range segments {
		switch strings.ToLower(segment) {
		case "t", "p", "post", "posts", "topic", "topics", "thread", "threads":
			if i+1 < len(segments) && segments[i+1] != "" {
				return segments[i+1], nil
			}
		}
	}
	if len(segments) > 0 && segments[len(segments)-1] != "" {
		return segments[len(segments)-1], nil
	}
	return "", errors.New("无法从链接中识别帖子 ID")
}

func cacheKeyForTopic(topicURL string) string {
	postID, err := postIDFromTopicURL(topicURL)
	if err == nil {
		return postID
	}
	return strings.TrimSpace(topicURL)
}

func normalizeCommunityTime(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return time.Now().UTC().Format(time.RFC3339)
	}
	layouts := []string{
		time.RFC3339,
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
		"2006-01-02 15:04",
	}
	for _, layout := range layouts {
		if parsed, err := time.ParseInLocation(layout, value, time.Local); err == nil {
			return parsed.Format(time.RFC3339)
		}
	}
	return value
}

func formatCommunityDisplayTime(value string) string {
	normalized := normalizeCommunityTime(value)
	if parsed, err := time.Parse(time.RFC3339, normalized); err == nil {
		return parsed.Local().Format("2006-01-02 15:04")
	}
	return value
}

func buildCommunityCommentURL(base, postID, commentID string) string {
	u := strings.TrimRight(base, "/") + "/t/" + url.PathEscape(postID)
	if commentID != "" {
		u += "#comment-" + url.PathEscape(commentID)
	}
	return u
}

func clipPlainText(value string, limit int) string {
	value = strings.TrimSpace(value)
	value = regexp.MustCompile(`<[^>]+>`).ReplaceAllString(value, "")
	value = strings.Join(strings.Fields(value), " ")
	runes := []rune(value)
	if len(runes) <= limit {
		return value
	}
	return string(runes[:limit]) + "..."
}

func fallback(value, fallback string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return fallback
	}
	return value
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func (app *App) static(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/")
	if path == "" {
		path = "index.html"
	}
	full := "web/" + path
	if _, err := webFiles.Open(full); err != nil {
		full = "web/index.html"
	}
	http.ServeFileFS(w, r, webFiles, full)
}
