from functools import lru_cache
import ipaddress
from typing import Annotated

from pydantic import AnyHttpUrl, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OPS_", env_file=".env.local", extra="ignore")

    environment: str = "production"
    dry_run: bool = True
    draft_only: bool = True
    service_id: str = "zens-ops"
    service_secret: SecretStr | None = None
    main_site_base_url: AnyHttpUrl = "http://127.0.0.1:7800"
    agent_base_url: AnyHttpUrl = "http://127.0.0.1:7810"
    timezone: str = "Asia/Shanghai"
    http_timeout_seconds: Annotated[float, Field(gt=0, le=60)] = 10
    http_retries: Annotated[int, Field(ge=0, le=5)] = 3
    max_posts_per_day: Annotated[int, Field(ge=0, le=5)] = 1
    max_replies_per_day: Annotated[int, Field(ge=0, le=50)] = 10
    first_approval_posts: Annotated[int, Field(ge=0, le=1000)] = 30
    default_section_id: Annotated[int, Field(ge=0)] = 1
    kill_switch: bool = False
    sensitive_topics: str = "politics,medical,legal,finance,minors,complaint,commercial,advertising,giveaway,paid,政治,医疗,法律,金融,未成年人,投诉,纠纷,商业承诺,广告,抽奖,付费"
    llm_base_url: AnyHttpUrl | None = None
    llm_api_key: SecretStr | None = None
    llm_model: str | None = None
    health_host: str = "127.0.0.1"
    health_port: Annotated[int, Field(ge=1024, le=65535)] = 7820
    log_level: str = "INFO"

    @model_validator(mode="after")
    def validate_security(self) -> "Settings":
        if not self.dry_run and (not self.service_secret or len(self.service_secret.get_secret_value()) < 32):
            raise ValueError("OPS_SERVICE_SECRET (>=32 chars) is required when OPS_DRY_RUN=false")
        if not self.dry_run and self.default_section_id <= 0:
            raise ValueError("OPS_DEFAULT_SECTION_ID must be >0 when OPS_DRY_RUN=false")
        llm_any = bool(self.llm_base_url or self.llm_api_key or self.llm_model)
        llm_all = bool(self.llm_base_url and self.llm_api_key and self.llm_model)
        if llm_any and not llm_all:
            raise ValueError("OPS_LLM_BASE_URL, OPS_LLM_API_KEY and OPS_LLM_MODEL must be configured together")
        if not self.service_id or any(ch not in "abcdefghijklmnopqrstuvwxyz0123456789-" for ch in self.service_id):
            raise ValueError("OPS_SERVICE_ID must contain only lowercase letters, digits and hyphens")
        if self.environment.lower() == "production" and not self.dry_run:
            for name, value in (("OPS_MAIN_SITE_BASE_URL", self.main_site_base_url), ("OPS_AGENT_BASE_URL", self.agent_base_url)):
                host = value.host
                try:
                    loopback = bool(host) and ipaddress.ip_address(host).is_loopback
                except ValueError:
                    loopback = host == "localhost"
                if not loopback:
                    raise ValueError(f"{name} must use a loopback host in production write mode")
                if value.username or value.password or value.query or value.fragment:
                    raise ValueError(f"{name} must not contain credentials, query or fragment")
        return self

    @property
    def sensitive_topic_set(self) -> set[str]:
        return {value.strip().lower() for value in self.sensitive_topics.split(",") if value.strip()}

    @property
    def llm_enabled(self) -> bool:
        return bool(self.llm_base_url and self.llm_api_key and self.llm_model)


@lru_cache
def get_settings() -> Settings:
    return Settings()
