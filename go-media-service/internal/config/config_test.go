package config

import "testing"

func TestValidateSecretsAcceptsInjectedValues(t *testing.T) {
	cfg := &Config{
		Panel: PanelConfig{Enabled: true},
		Auth: AuthConfig{
			Upload: UploadAuthConfig{
				Enabled:   true,
				JWTSecret: "upload-secret-with-at-least-thirty-two-characters",
			},
			Admin: AdminAuthConfig{
				Username:      "admin",
				Password:      "admin-password-at-least-sixteen",
				JWTSecret:     "admin-secret-with-at-least-thirty-two-characters",
				ServiceTokens: []string{"service-token-with-at-least-thirty-two-characters"},
			},
		},
	}

	if err := validateSecrets(cfg); err != nil {
		t.Fatalf("expected valid injected secrets, got %v", err)
	}
}

func TestValidateSecretsRejectsRepositoryPlaceholder(t *testing.T) {
	cfg := &Config{
		Panel: PanelConfig{Enabled: true},
		Auth: AuthConfig{
			Upload: UploadAuthConfig{
				Enabled:   true,
				JWTSecret: "REPLACE_WITH_RANDOM_UPLOAD_JWT_SECRET_AT_LEAST_32_CHARS",
			},
			Admin: AdminAuthConfig{
				Username:      "admin",
				Password:      "REPLACE_WITH_RANDOM_ADMIN_PASSWORD_AT_LEAST_16_CHARS",
				JWTSecret:     "REPLACE_WITH_RANDOM_ADMIN_JWT_SECRET_AT_LEAST_32_CHARS",
				ServiceTokens: []string{"REPLACE_WITH_RANDOM_SERVICE_TOKEN_AT_LEAST_32_CHARS"},
			},
		},
	}

	if err := validateSecrets(cfg); err == nil {
		t.Fatal("expected repository placeholders to fail validation")
	}
}

func TestApplyEnvOverridesLoadsServiceTokensWithoutLowercasing(t *testing.T) {
	t.Setenv("MEDIA_ADMIN_SERVICE_TOKENS", "TokenABC123456789012345678901234567, TokenXYZ123456789012345678901234567")
	cfg := &Config{}

	applyEnvOverrides(cfg)

	if len(cfg.Auth.Admin.ServiceTokens) != 2 {
		t.Fatalf("expected two service tokens, got %d", len(cfg.Auth.Admin.ServiceTokens))
	}
	if cfg.Auth.Admin.ServiceTokens[0] != "TokenABC123456789012345678901234567" {
		t.Fatalf("service token case changed: %q", cfg.Auth.Admin.ServiceTokens[0])
	}
}
