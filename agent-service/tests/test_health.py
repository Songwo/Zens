from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from fastapi import Response

_IMPORT_ENV = {
    "AGENT_SEARCH_BACKEND": "mysql",
    "AGENT_MYSQL_REPLICA_HOST": "test-replica",
    "AGENT_MYSQL_REPLICA_DATABASE": "campus_pulse",
    "AGENT_MYSQL_REPLICA_USERNAME": "agent_reader",
    "AGENT_MYSQL_REPLICA_PASSWORD": "test-password",
    "AGENT_MYSQL_REQUIRE_READ_ONLY": "true",
    "AGENT_POSTGRES_DSN": "",
    "AGENT_LLM_API_KEY": "",
    "AGENT_ALLOWED_ORIGINS": "",
}

with patch.dict(os.environ, _IMPORT_ENV, clear=True):
    from app import main as main_module
    from app.config import Settings


class FakeRepository:
    backend_name = "mysql"
    replica_read_only = True
    replica_read_only_required = True

    def __init__(self, error: Exception | None = None) -> None:
        self.error = error

    def ping(self) -> bool:
        if self.error:
            raise self.error
        return True


class HealthResponseTest(unittest.TestCase):
    def setUp(self) -> None:
        with patch.dict(os.environ, _IMPORT_ENV, clear=True):
            self.settings = Settings(_env_file=None)  # type: ignore[call-arg]

    def test_health_reports_replica_read_only_evidence(self) -> None:
        main_module.app.state.repository = FakeRepository()

        with patch.object(main_module, "get_settings", return_value=self.settings):
            health = main_module.build_health_response()

        self.assertEqual("ok", health.status)
        self.assertEqual("ok", health.mysql_replica)
        self.assertTrue(health.replica_read_only)
        self.assertTrue(health.replica_read_only_required)

    def test_ready_returns_503_and_hides_database_error_details(self) -> None:
        main_module.app.state.repository = FakeRepository(
            RuntimeError("Access denied for agent_reader at secret-replica.internal")
        )
        response = Response()

        with patch.object(main_module, "get_settings", return_value=self.settings):
            health = main_module.ready(response)

        self.assertEqual(503, response.status_code)
        self.assertEqual("degraded", health.status)
        self.assertEqual("down", health.mysql_replica)
        self.assertEqual("database health check failed", health.error)
        self.assertNotIn("agent_reader", health.error or "")
        self.assertNotIn("secret-replica.internal", health.error or "")


if __name__ == "__main__":
    unittest.main()
