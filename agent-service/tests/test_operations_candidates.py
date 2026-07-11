from __future__ import annotations

from datetime import datetime
import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException

_ENV = {
    "AGENT_SEARCH_BACKEND": "mysql", "AGENT_MYSQL_REPLICA_HOST": "replica",
    "AGENT_MYSQL_REPLICA_DATABASE": "community", "AGENT_MYSQL_REPLICA_USERNAME": "reader",
    "AGENT_MYSQL_REPLICA_PASSWORD": "secret", "AGENT_MYSQL_REQUIRE_READ_ONLY": "true",
}
with patch.dict(os.environ, _ENV, clear=True):
    from app import main as main_module
    from app.repositories.base import OperationsCandidate


class FakeRepository:
    backend_name = "mysql"

    def __init__(self) -> None:
        self.calls = []

    def list_operations_candidates(self, kind, days, limit, max_comments):
        self.calls.append((kind, days, limit, max_comments))
        now = datetime(2026, 7, 11, 9, 0)
        return [OperationsCandidate("42", "真实问题", "摘要", "技术", ["Python"], 0, 3, 2, 80, 12.5, False, now, now, "/t/42")]


class OperationsCandidatesContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repository = FakeRepository()
        main_module.app.state.repository = self.repository

    def test_needs_reply_contract_and_repository_arguments(self) -> None:
        response = main_module.operations_candidates("engagement", 14, 10, 1)
        self.assertEqual("engagement", response.kind)
        self.assertEqual("mysql", response.backend)
        self.assertEqual("42", response.candidates[0].post_id)
        self.assertEqual(0, response.candidates[0].comment_count)
        self.assertIn("仅有 0 条回复", response.candidates[0].reason)
        self.assertEqual([("engagement", 14, 10, 1)], self.repository.calls)

    def test_bounds_are_enforced_before_query(self) -> None:
        with self.assertRaises(HTTPException) as raised:
            main_module.operations_candidates("weekly", 31, 20, 1)
        self.assertEqual(422, raised.exception.status_code)
        self.assertEqual([], self.repository.calls)


if __name__ == "__main__":
    unittest.main()
