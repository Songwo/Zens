from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from app.config import Settings


class SettingsTest(unittest.TestCase):
    def build_settings(self, values: dict[str, str]) -> Settings:
        with patch.dict(os.environ, values, clear=True):
            return Settings(_env_file=None)  # type: ignore[call-arg]

    def test_replica_variables_take_precedence_over_legacy_mysql_variables(self) -> None:
        settings = self.build_settings(
            {
                "AGENT_SEARCH_BACKEND": "mysql",
                "AGENT_MYSQL_REPLICA_DSN": "jdbc:mysql://replica.internal:3308/campus_pulse?characterEncoding=utf8mb4",
                "AGENT_MYSQL_REPLICA_USERNAME": "agent_reader",
                "AGENT_MYSQL_REPLICA_PASSWORD": "replica-password",
                "AGENT_MYSQL_DSN": "jdbc:mysql://primary.internal:3306/campus_pulse",
                "AGENT_MYSQL_USERNAME": "legacy_writer",
                "AGENT_MYSQL_PASSWORD": "legacy-password",
                "AGENT_MYSQL_REQUIRE_READ_ONLY": "true",
            }
        )

        options = settings.mysql_replica_connection_options

        self.assertEqual("replica.internal", options["host"])
        self.assertEqual(3308, options["port"])
        self.assertEqual("campus_pulse", options["database"])
        self.assertEqual("agent_reader", options["user"])
        self.assertEqual("replica-password", options["password"])
        self.assertTrue(settings.mysql_require_read_only)
        self.assertEqual("mysql", settings.selected_backend)

    def test_legacy_variables_remain_compatible(self) -> None:
        settings = self.build_settings(
            {
                "AGENT_SEARCH_BACKEND": "mysql",
                "AGENT_MYSQL_HOST": "legacy-replica.internal",
                "AGENT_MYSQL_PORT": "3308",
                "AGENT_MYSQL_DATABASE": "campus_pulse",
                "AGENT_MYSQL_USERNAME": "legacy_reader",
                "AGENT_MYSQL_PASSWORD": "legacy-password",
            }
        )

        options = settings.mysql_connection_options

        self.assertEqual("legacy-replica.internal", options["host"])
        self.assertEqual("legacy_reader", options["user"])
        self.assertFalse(settings.mysql_require_read_only)

    def test_mysql_backend_fails_clearly_without_replica_configuration(self) -> None:
        settings = self.build_settings({"AGENT_SEARCH_BACKEND": "mysql"})

        with self.assertRaisesRegex(ValueError, "AGENT_MYSQL_REPLICA"):
            _ = settings.selected_backend


if __name__ == "__main__":
    unittest.main()
