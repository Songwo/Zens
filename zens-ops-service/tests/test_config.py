import pytest
from pydantic import ValidationError

from zens_ops.config import Settings


def test_write_mode_requires_long_secret():
    with pytest.raises(ValidationError):
        Settings(dry_run=False, service_secret="short")


def test_llm_configuration_is_atomic():
    with pytest.raises(ValidationError):
        Settings(llm_model="gpt-test")


def test_safe_defaults():
    settings = Settings(_env_file=None)
    assert settings.dry_run is True
    assert settings.draft_only is True
    assert settings.max_posts_per_day == 1
    assert settings.max_replies_per_day == 10


def test_production_write_mode_requires_loopback_dependencies():
    with pytest.raises(ValidationError, match="loopback"):
        Settings(
            _env_file=None,
            dry_run=False,
            service_secret="x" * 32,
            main_site_base_url="https://example.com",
        )
