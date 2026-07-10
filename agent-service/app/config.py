from __future__ import annotations

from functools import lru_cache
from typing import Any, List
from urllib.parse import parse_qs, urlparse

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env.local", ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    service_host: str = Field(default="0.0.0.0", alias="AGENT_SERVICE_HOST")
    service_port: int = Field(default=7810, alias="AGENT_SERVICE_PORT")
    log_level: str = Field(default="INFO", alias="AGENT_LOG_LEVEL")

    search_backend: str = Field(default="auto", alias="AGENT_SEARCH_BACKEND")

    postgres_dsn: str = Field(default="", alias="AGENT_POSTGRES_DSN")
    postgres_min_pool_size: int = Field(default=1, alias="AGENT_POSTGRES_MIN_POOL_SIZE")
    postgres_max_pool_size: int = Field(default=10, alias="AGENT_POSTGRES_MAX_POOL_SIZE")
    postgres_connect_timeout_seconds: int = Field(default=5, alias="AGENT_POSTGRES_CONNECT_TIMEOUT_SECONDS")

    mysql_url: str = Field(
        default="",
        validation_alias=AliasChoices(
            "AGENT_MYSQL_REPLICA_DSN",
            "AGENT_MYSQL_REPLICA_JDBC_URL",
            "AGENT_MYSQL_DSN",
            "AGENT_MYSQL_JDBC_URL",
            "DB_URL",
        ),
    )
    mysql_host: str = Field(
        default="",
        validation_alias=AliasChoices("AGENT_MYSQL_REPLICA_HOST", "AGENT_MYSQL_HOST"),
    )
    mysql_port: int = Field(
        default=3306,
        ge=1,
        le=65535,
        validation_alias=AliasChoices("AGENT_MYSQL_REPLICA_PORT", "AGENT_MYSQL_PORT"),
    )
    mysql_database: str = Field(
        default="",
        validation_alias=AliasChoices("AGENT_MYSQL_REPLICA_DATABASE", "AGENT_MYSQL_DATABASE"),
    )
    mysql_username: str = Field(
        default="",
        validation_alias=AliasChoices(
            "AGENT_MYSQL_REPLICA_USERNAME",
            "AGENT_MYSQL_USERNAME",
            "DB_USERNAME",
        ),
    )
    mysql_password: str = Field(
        default="",
        validation_alias=AliasChoices(
            "AGENT_MYSQL_REPLICA_PASSWORD",
            "AGENT_MYSQL_PASSWORD",
            "DB_PASSWORD",
        ),
    )
    mysql_charset: str = Field(default="utf8mb4", alias="AGENT_MYSQL_CHARSET")
    mysql_connect_timeout_seconds: int = Field(
        default=5,
        ge=1,
        le=60,
        alias="AGENT_MYSQL_CONNECT_TIMEOUT_SECONDS",
    )
    mysql_require_read_only: bool = Field(default=False, alias="AGENT_MYSQL_REQUIRE_READ_ONLY")

    default_search_limit: int = Field(default=6, alias="AGENT_DEFAULT_SEARCH_LIMIT")
    max_search_limit: int = Field(default=12, alias="AGENT_MAX_SEARCH_LIMIT")
    default_comments_per_post: int = Field(default=2, alias="AGENT_DEFAULT_COMMENTS_PER_POST")
    max_comments_per_post: int = Field(default=4, alias="AGENT_MAX_COMMENTS_PER_POST")
    min_question_length: int = Field(default=2, alias="AGENT_MIN_QUESTION_LENGTH")

    llm_enabled: bool = Field(default=False, alias="AGENT_LLM_ENABLED")
    llm_base_url: str = Field(default="https://api.openai.com/v1", alias="AGENT_LLM_BASE_URL")
    llm_api_key: str = Field(default="", alias="AGENT_LLM_API_KEY")
    llm_model: str = Field(default="gpt-4.1-mini", alias="AGENT_LLM_MODEL")
    llm_timeout_seconds: int = Field(default=18, alias="AGENT_LLM_TIMEOUT_SECONDS")
    llm_temperature: float = Field(default=0.1, alias="AGENT_LLM_TEMPERATURE")

    allowed_origins_raw: str = Field(default="", alias="AGENT_ALLOWED_ORIGINS")

    @property
    def allowed_origins(self) -> List[str]:
        return [item.strip() for item in self.allowed_origins_raw.split(",") if item.strip()]

    @property
    def normalized_search_backend(self) -> str:
        backend = self.search_backend.strip().lower() or "auto"
        if backend not in {"auto", "mysql", "postgres"}:
            raise ValueError("AGENT_SEARCH_BACKEND 仅支持 auto / mysql / postgres")
        return backend

    @property
    def has_postgres_configured(self) -> bool:
        return bool(self.postgres_dsn.strip())

    @property
    def has_mysql_configured(self) -> bool:
        try:
            options = self.mysql_replica_connection_options
        except ValueError:
            return False
        return bool(options.get("host") and options.get("database") and options.get("user"))

    @property
    def selected_backend(self) -> str:
        backend = self.normalized_search_backend
        if backend == "postgres":
            if not self.has_postgres_configured:
                raise ValueError("已指定 PostgreSQL 后端，但未配置 AGENT_POSTGRES_DSN")
            return "postgres"
        if backend == "mysql":
            if not self.has_mysql_configured:
                raise ValueError("已指定 MySQL 后端，但未配置可用的 AGENT_MYSQL_REPLICA_* 连接信息")
            return "mysql"
        if self.has_postgres_configured:
            return "postgres"
        if self.has_mysql_configured:
            return "mysql"
        raise ValueError("未配置任何搜索后端，请至少提供 PostgreSQL DSN 或 MySQL 连接信息")

    @property
    def mysql_replica_connection_options(self) -> dict[str, Any]:
        base: dict[str, Any] = {
            "host": self.mysql_host.strip(),
            "port": self.mysql_port,
            "user": self.mysql_username.strip(),
            "password": self.mysql_password,
            "database": self.mysql_database.strip(),
            "charset": self.mysql_charset.strip() or "utf8mb4",
            "connect_timeout": self.mysql_connect_timeout_seconds,
            "read_timeout": self.mysql_connect_timeout_seconds + 5,
            "write_timeout": self.mysql_connect_timeout_seconds + 5,
            "autocommit": True,
        }
        raw_url = self.mysql_url.strip()
        if not raw_url:
            return base

        normalized_url = raw_url[5:] if raw_url.startswith("jdbc:") else raw_url
        parsed = urlparse(normalized_url)
        if parsed.scheme not in {"mysql", "mysql+pymysql"}:
            raise ValueError("MySQL 连接串必须是 mysql:// 或 jdbc:mysql:// 格式")

        params = parse_qs(parsed.query)
        charset = params.get("characterEncoding", [base["charset"]])[0] or base["charset"]
        return {
            **base,
            "host": parsed.hostname or base["host"],
            "port": parsed.port or base["port"],
            "user": parsed.username or base["user"],
            "password": parsed.password or base["password"],
            "database": parsed.path.lstrip("/") or base["database"],
            "charset": charset,
        }

    @property
    def mysql_connection_options(self) -> dict[str, Any]:
        """Backward-compatible alias for callers using the old property name."""
        return self.mysql_replica_connection_options


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
