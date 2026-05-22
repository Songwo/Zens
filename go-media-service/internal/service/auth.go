package service

import (
	"crypto/sha256"
	"errors"
	"slices"
	"strings"
	"time"

	"go-media-service/internal/config"

	"github.com/golang-jwt/jwt/v5"
)

type UploadPrincipal struct {
	UserID  string
	BizType string
	Scope   string
}

type AdminPrincipal struct {
	Username string
}

type uploadClaims struct {
	UserID  string `json:"uid"`
	BizType string `json:"biz,omitempty"`
	Scope   string `json:"scope"`
	jwt.RegisteredClaims
}

type adminClaims struct {
	Username string `json:"username"`
	Role     string `json:"role"`
	jwt.RegisteredClaims
}

type AuthService struct {
	cfg *config.Config
}

func NewAuthService(cfg *config.Config) *AuthService {
	return &AuthService{cfg: cfg}
}

func (s *AuthService) ValidateUploadToken(tokenString string) (*UploadPrincipal, error) {
	claims := &uploadClaims{}
	token, err := jwt.ParseWithClaims(tokenString, claims, func(_ *jwt.Token) (any, error) {
		return resolveJWTSecret(s.cfg.Auth.Upload.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return nil, errors.New("invalid upload token")
	}
	if claims.Issuer != s.cfg.Auth.Upload.Issuer {
		return nil, errors.New("invalid upload token issuer")
	}
	if len(claims.Audience) > 0 && !slices.Contains([]string(claims.Audience), s.cfg.Auth.Upload.Audience) {
		return nil, errors.New("invalid upload token audience")
	}
	if claims.UserID == "" {
		return nil, errors.New("upload token missing uid")
	}
	return &UploadPrincipal{
		UserID:  claims.UserID,
		BizType: claims.BizType,
		Scope:   claims.Scope,
	}, nil
}

func (s *AuthService) IssueAdminToken(username string) (string, error) {
	claims := adminClaims{
		Username: username,
		Role:     "admin",
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    s.cfg.Auth.Admin.Issuer,
			Subject:   username,
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Duration(s.cfg.Auth.Admin.TokenTTLMinute) * time.Minute)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(resolveJWTSecret(s.cfg.Auth.Admin.JWTSecret))
}

func (s *AuthService) ValidateAdminToken(tokenString string) (*AdminPrincipal, error) {
	claims := &adminClaims{}
	token, err := jwt.ParseWithClaims(tokenString, claims, func(_ *jwt.Token) (any, error) {
		return resolveJWTSecret(s.cfg.Auth.Admin.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return nil, errors.New("invalid admin token")
	}
	if claims.Issuer != s.cfg.Auth.Admin.Issuer || claims.Username == "" {
		return nil, errors.New("invalid admin token issuer")
	}
	return &AdminPrincipal{Username: claims.Username}, nil
}

func (s *AuthService) ValidateAdminCredential(username string, password string) bool {
	return strings.TrimSpace(username) == s.cfg.Auth.Admin.Username &&
		password == s.cfg.Auth.Admin.Password
}

func (s *AuthService) IsServiceToken(token string) bool {
	trimmed := strings.TrimSpace(token)
	if trimmed == "" {
		return false
	}
	for _, item := range s.cfg.Auth.Admin.ServiceTokens {
		if item == trimmed {
			return true
		}
	}
	return false
}

func resolveJWTSecret(secret string) []byte {
	raw := []byte(secret)
	if len(raw) >= 32 {
		return raw
	}
	sum := sha256.Sum256(raw)
	return sum[:]
}
