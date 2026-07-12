from __future__ import annotations

import os
import unittest
from datetime import datetime
from unittest.mock import patch

import pymysql

from app.config import Settings
from app.repositories.mysql_search import MysqlSearchRepository, ReplicaSafetyError


class FakeCursor:
    def __init__(self, connection: "FakeConnection") -> None:
        self.connection = connection

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def execute(self, sql: str, params: object = None) -> None:
        self.connection.statements.append((" ".join(sql.split()), params))

    def fetchone(self) -> dict[str, int]:
        return self.connection.health_row

    def fetchall(self) -> list[dict]:
        return self.connection.rows


class FakeConnection:
    def __init__(self, *, read_only: int, super_read_only: int) -> None:
        self.health_row = {
            "healthy": 1,
            "read_only": read_only,
            "super_read_only": super_read_only,
        }
        self.statements: list[tuple[str, object]] = []
        self.closed = False
        self.rows: list[dict] = []

    def __enter__(self) -> "FakeConnection":
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()

    def cursor(self) -> FakeCursor:
        return FakeCursor(self)

    def close(self) -> None:
        self.closed = True


class MysqlSearchRepositoryTest(unittest.TestCase):
    def build_settings(self, *, require_read_only: bool) -> Settings:
        values = {
            "AGENT_SEARCH_BACKEND": "mysql",
            "AGENT_MYSQL_REPLICA_HOST": "replica.internal",
            "AGENT_MYSQL_REPLICA_PORT": "3306",
            "AGENT_MYSQL_REPLICA_DATABASE": "campus_pulse",
            "AGENT_MYSQL_REPLICA_USERNAME": "agent_reader",
            "AGENT_MYSQL_REPLICA_PASSWORD": "do-not-leak",
            "AGENT_MYSQL_REQUIRE_READ_ONLY": str(require_read_only).lower(),
        }
        with patch.dict(os.environ, values, clear=True):
            return Settings(_env_file=None)  # type: ignore[call-arg]

    def test_open_verifies_replica_and_makes_session_read_only(self) -> None:
        settings = self.build_settings(require_read_only=True)
        connection = FakeConnection(read_only=1, super_read_only=1)

        with patch("app.repositories.mysql_search.pymysql.connect", return_value=connection) as connect:
            repository = MysqlSearchRepository(settings)
            repository.open()

        self.assertTrue(repository.replica_read_only)
        self.assertTrue(repository.replica_read_only_required)
        self.assertEqual("replica.internal", connect.call_args.kwargs["host"])
        self.assertEqual("agent_reader", connect.call_args.kwargs["user"])
        self.assertEqual("SET SESSION TRANSACTION READ ONLY", connection.statements[0][0])
        self.assertIn("@@GLOBAL.read_only", connection.statements[1][0])

    def test_strict_mode_rejects_a_writable_target_without_leaking_credentials(self) -> None:
        settings = self.build_settings(require_read_only=True)
        connection = FakeConnection(read_only=0, super_read_only=0)
        repository = MysqlSearchRepository(settings)

        with patch("app.repositories.mysql_search.pymysql.connect", return_value=connection):
            with self.assertRaises(ReplicaSafetyError) as raised:
                repository.open()

        message = str(raised.exception)
        self.assertIn("AGENT_MYSQL_REQUIRE_READ_ONLY=true", message)
        self.assertNotIn("replica.internal", message)
        self.assertNotIn("do-not-leak", message)

    def test_strict_mode_requires_super_read_only_too(self) -> None:
        settings = self.build_settings(require_read_only=True)
        connection = FakeConnection(read_only=1, super_read_only=0)
        repository = MysqlSearchRepository(settings)

        with patch("app.repositories.mysql_search.pymysql.connect", return_value=connection):
            with self.assertRaises(ReplicaSafetyError):
                repository.open()

        self.assertFalse(repository.replica_read_only)

    def test_startup_connection_error_is_sanitized(self) -> None:
        settings = self.build_settings(require_read_only=True)
        repository = MysqlSearchRepository(settings)
        raw_error = pymysql.err.OperationalError(
            1045,
            "Access denied for user agent_reader at replica.internal using do-not-leak",
        )

        with patch("app.repositories.mysql_search.pymysql.connect", side_effect=raw_error):
            with self.assertRaises(ConnectionError) as raised:
                repository.open()

        message = str(raised.exception)
        self.assertIn("error 1045", message)
        self.assertIn("AGENT_MYSQL_REPLICA", message)
        self.assertNotIn("agent_reader", message)
        self.assertNotIn("replica.internal", message)
        self.assertNotIn("do-not-leak", message)

    def test_operations_candidates_use_read_only_select_with_approved_filter(self) -> None:
        settings = self.build_settings(require_read_only=True)
        connection = FakeConnection(read_only=1, super_read_only=1)
        now = datetime(2026, 7, 11, 9, 0)
        connection.rows = [{
            "post_id": "42", "title": "Help", "summary": "", "section_name": "Tech",
            "tags": "python,help", "comment_count": 0, "like_count": 1, "collect_count": 0,
            "view_count": 10, "heat_score": 2.0, "is_featured": 0,
            "created_at": now, "last_activity_at": now,
        }]
        repository = MysqlSearchRepository(settings)

        with patch("app.repositories.mysql_search.pymysql.connect", return_value=connection):
            candidates = repository.list_operations_candidates("engagement", 7, 20, 1)

        self.assertEqual("42", candidates[0].post_id)
        statements = [statement for statement, _ in connection.statements]
        self.assertEqual("SET SESSION TRANSACTION READ ONLY", statements[0])
        self.assertIn("p.audit_status = 'APPROVED'", statements[1])
        self.assertIn("p.comment_count", statements[1])
        self.assertFalse(any(token in statements[1].upper() for token in (" INSERT ", " UPDATE ", " DELETE ")))

    def test_community_health_is_a_read_only_aggregate(self) -> None:
        settings = self.build_settings(require_read_only=True)
        connection = FakeConnection(read_only=1, super_read_only=1)
        connection.health_row = {
            "published_posts": 8,
            "approved_comments": 12,
            "active_contributors": 7,
            "unanswered_posts": 2,
            "engaged_posts": 6,
            "total_views": 320,
        }
        repository = MysqlSearchRepository(settings)

        with patch("app.repositories.mysql_search.pymysql.connect", return_value=connection):
            snapshot = repository.get_community_health(7)

        self.assertEqual(8, snapshot.published_posts)
        self.assertEqual(7, snapshot.active_contributors)
        statements = [statement for statement, _ in connection.statements]
        self.assertEqual("SET SESSION TRANSACTION READ ONLY", statements[0])
        self.assertIn("SELECT COUNT", statements[1])
        self.assertFalse(any(token in statements[1].upper() for token in (" INSERT ", " UPDATE ", " DELETE ")))


if __name__ == "__main__":
    unittest.main()
