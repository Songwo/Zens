package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"embed"
	"encoding/base64"
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
	Draws []Draw          `json:"draws"`
	Users map[string]User `json:"users"`
	Audit []AuditEvent    `json:"audit"`
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
	Token     string    `json:"token"`
	UserID    string    `json:"userId"`
	ExpiresAt time.Time `json:"expiresAt"`
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
	Total   int                `json:"total"`
	Pages   int                `json:"pages"`
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

func main() {
	cfg := loadConfig()
	store, err := OpenStore(cfg.DataPath)
	if err != nil {
		log.Fatalf("open store: %v", err)
	}

	app := &App{
		cfg:      cfg,
		store:    store,
		sessions: map[string]Session{},
		lottery:  NewLotteryRuntime(cfg),
	}

	mux := http.NewServeMux()
	app.routes(mux)

	log.Printf("Campus Lottery Station listening on %s", cfg.Addr)
	log.Printf("Public URL: %s", cfg.PublicURL)
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
	app.issueSession(w, user.ID)
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
	cookie, err := r.Cookie(sessionCookie)
	if err == nil {
		app.mu.Lock()
		delete(app.sessions, cookie.Value)
		app.mu.Unlock()
	}
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookie,
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})
	writeJSON(w, http.StatusOK, APIResponse{OK: true})
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
			http.Redirect(w, r, "/?sso=invalid-token", http.StatusFound)
			return
		}
		if err := app.persistLoginUser(user, "auth.sso_login", user.DisplayName+" 通过社区 SSO 登录抽奖站"); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		app.issueSession(w, user.ID)
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
	app.issueSession(w, user.ID)
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
	claims, err := verifyHS256JWT(token, app.cfg.CommunityJWTSecret)
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

func verifyHS256JWT(token, secret string) (map[string]any, error) {
	secret = strings.TrimSpace(secret)
	if secret == "" {
		return nil, errors.New("未配置 COMMUNITY_JWT_SECRET，无法验证主站 SSO token")
	}
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, errors.New("JWT 格式不正确")
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return nil, errors.New("JWT 签名格式不正确")
	}
	expected := hmac.New(sha256.New, []byte(secret))
	_, _ = expected.Write([]byte(parts[0] + "." + parts[1]))
	if !hmac.Equal(sig, expected.Sum(nil)) {
		derived := sha256.Sum256([]byte(secret))
		expected = hmac.New(sha256.New, derived[:])
		_, _ = expected.Write([]byte(parts[0] + "." + parts[1]))
		if !hmac.Equal(sig, expected.Sum(nil)) {
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
	cookie, err := r.Cookie(sessionCookie)
	if err != nil || cookie.Value == "" {
		return nil, false
	}
	app.mu.Lock()
	session, ok := app.sessions[cookie.Value]
	app.mu.Unlock()
	if !ok || time.Now().UTC().After(session.ExpiresAt) {
		return nil, false
	}
	state := app.store.snapshot()
	user, ok := state.Users[session.UserID]
	if !ok {
		return nil, false
	}
	return &user, true
}

func (app *App) issueSession(w http.ResponseWriter, userID string) {
	token := randomID(32)
	session := Session{
		Token:     token,
		UserID:    userID,
		ExpiresAt: time.Now().UTC().Add(7 * 24 * time.Hour),
	}
	app.mu.Lock()
	app.sessions[token] = session
	app.mu.Unlock()
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
	if _, ok := app.currentUser(r); !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号后再同步帖子评论")
		return
	}
	var req lotteryCriteria
	if err := decodeJSON(r, &req); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	preview, _, err := app.buildTopicPreview(req)
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
	if _, ok := app.currentUser(r); !ok {
		writeError(w, http.StatusUnauthorized, "请先登录社区账号后再开奖")
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
	preview, postID, err := app.buildTopicPreview(req.lotteryCriteria)
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
	picks, seed, err := securePickLottery(preview.Participants, req.WinnerCount)
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
		ParticipantCount: len(preview.Participants),
		Winners:          picks,
		CreatedAt:        now.Format(time.RFC3339),
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
	if strings.TrimSpace(req.DrawID) == "" || len(req.Winners) == 0 {
		writeError(w, http.StatusBadRequest, "缺少抽奖结果")
		return
	}
	postID, err := postIDFromTopicURL(req.TopicURL)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	content := buildTopicLotteryComment(req)
	token, err := app.botAccessToken()
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := app.createCommunityComment(postID, content, token); err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	comments, err := app.fetchCommunityComments(postID)
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

func canOperateOfficialLottery(user *User) bool {
	if user == nil {
		return false
	}
	role := strings.ToLower(strings.TrimSpace(user.Role))
	return role == "admin" || role == "super_admin" || role == "moderator"
}

func (app *App) buildTopicPreview(req lotteryCriteria) (TopicPreview, string, error) {
	postID, err := postIDFromTopicURL(req.TopicURL)
	if err != nil {
		return TopicPreview{}, "", err
	}
	post, err := app.fetchCommunityPost(postID)
	if err != nil {
		return TopicPreview{}, "", err
	}
	comments, err := app.fetchCommunityComments(postID)
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

func (app *App) fetchCommunityPost(postID string) (communityPost, error) {
	var resp communityResult[communityPost]
	if err := app.communityGet("/post/"+url.PathEscape(postID), &resp); err != nil {
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

func (app *App) fetchCommunityComments(postID string) ([]communityComment, error) {
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
		if err := app.communityGet(path, &resp); err != nil {
			return nil, err
		}
		if resp.Code != 2000 {
			return nil, fmt.Errorf("主站读取评论失败：%s", fallback(resp.Message, "未知错误"))
		}
		all = append(all, flattenCommunityComments(resp.Data.Records)...)
		total := resp.Data.Total
		pages := resp.Data.Pages
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

func (app *App) communityGet(path string, dst any) error {
	req, err := http.NewRequest(http.MethodGet, app.communityURL(path), nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	client := http.Client{Timeout: 10 * time.Second}
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
		return fmt.Errorf("主社区返回错误：%s", resp.Status)
	}
	if err := json.Unmarshal(body, dst); err != nil {
		return fmt.Errorf("主社区响应解析失败：%w", err)
	}
	return nil
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

func (app *App) communityURL(path string) string {
	base := strings.TrimRight(app.cfg.CommunityAPI, "/")
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return base + path
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

func securePickLottery(participants []LotteryParticipant, count int) ([]LotteryWinner, string, error) {
	pool := append([]LotteryParticipant(nil), participants...)
	seedBytes := make([]byte, 16)
	if _, err := rand.Read(seedBytes); err != nil {
		return nil, "", err
	}
	seed := "zens-" + hex.EncodeToString(seedBytes)
	for i := len(pool) - 1; i > 0; i-- {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(i+1)))
		if err != nil {
			return nil, "", err
		}
		j := int(n.Int64())
		pool[i], pool[j] = pool[j], pool[i]
	}
	winners := make([]LotteryWinner, 0, count)
	for i := 0; i < count; i++ {
		winners = append(winners, LotteryWinner{
			LotteryParticipant: pool[i],
			Rank:               i + 1,
		})
	}
	return winners, seed, nil
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

func buildTopicLotteryComment(req publishResultRequest) string {
	var b strings.Builder
	b.WriteString("## Zens 社区抽奖结果\n\n")
	b.WriteString(fmt.Sprintf("本次抽奖基于帖子回复数据生成，共 %d 名有效参与者，抽取 %d 名中奖者。\n\n", req.ParticipantCount, len(req.Winners)))
	b.WriteString("- 随机种子：")
	b.WriteString(req.Seed)
	b.WriteString("\n")
	b.WriteString("- 规则：排除发帖人、同一用户只计入一次，并按设置的截止楼层统计。\n\n")
	b.WriteString("### 中奖名单\n\n")
	for _, winner := range req.Winners {
		b.WriteString(fmt.Sprintf("%d. %s（%s 楼，回复时间 %s）\n", winner.Rank, winner.DisplayName, strconv.Itoa(winner.Floor), formatCommunityDisplayTime(winner.RepliedAt)))
	}
	b.WriteString("\n请中奖用户留意社区站内通知或管理员私信。")
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
