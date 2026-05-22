package service

import (
	"context"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/model"
	"go-media-service/internal/repository"
)

type RuntimeConfigService struct {
	manager       *config.Manager
	repo          *repository.SQLiteRepository
	lastUpdatedAt *time.Time
}

func NewRuntimeConfigService(manager *config.Manager, repo *repository.SQLiteRepository) *RuntimeConfigService {
	return &RuntimeConfigService{
		manager: manager,
		repo:    repo,
	}
}

func (s *RuntimeConfigService) View() model.RuntimeConfigView {
	base := s.manager.Base()
	return model.RuntimeConfigView{
		Static: map[string]any{
			"server":  base.Server,
			"storage": base.Storage,
			"database": map[string]any{
				"driver": base.Database.Driver,
				"dsn":    base.Database.DSN,
			},
			"auth": map[string]any{
				"upload_enabled":          base.Auth.Upload.Enabled,
				"upload_issuer":           base.Auth.Upload.Issuer,
				"upload_audience":         base.Auth.Upload.Audience,
				"allow_debug_user_header": base.Auth.Upload.AllowDebugUserHeader,
				"panel_enabled":           base.Panel.Enabled,
				"panel_base_path":         base.Panel.BasePath,
			},
		},
		Runtime: config.RuntimeValueMap(s.manager.Runtime()),
	}
}

func (s *RuntimeConfigService) Update(ctx context.Context, updates map[string]string, actor string) (model.RuntimeConfigView, error) {
	if err := s.manager.UpdateRuntime(updates); err != nil {
		return model.RuntimeConfigView{}, err
	}
	if err := s.repo.SaveRuntimeConfigValues(ctx, updates, actor); err != nil {
		return model.RuntimeConfigView{}, err
	}
	now := time.Now()
	s.lastUpdatedAt = &now
	return s.View(), nil
}

func (s *RuntimeConfigService) LastUpdatedAt() *string {
	if s.lastUpdatedAt == nil {
		return nil
	}
	formatted := s.lastUpdatedAt.Format(time.RFC3339)
	return &formatted
}
