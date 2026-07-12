from __future__ import annotations

from datetime import datetime
import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException

_ENV = {
    "AGENT_SEARCH_BACKEND": "mysql",
    "AGENT_MYSQL_REPLICA_HOST": "replica",
    "AGENT_MYSQL_REPLICA_DATABASE": "community",
    "AGENT_MYSQL_REPLICA_USERNAME": "reader",
    "AGENT_MYSQL_REPLICA_PASSWORD": "secret",
    "AGENT_MYSQL_REQUIRE_READ_ONLY": "true",
}

with patch.dict(os.environ, _ENV, clear=True):
    from app import main as main_module
    from app.repositories.base import CommunityHealth, OperationsCandidate
    from app.services.community_insights import CommunityInsightsService


class FakeRepository:
    backend_name = "mysql"

    def __init__(self) -> None:
        self.candidate_calls: list[tuple[str, int, int, int]] = []
        self.health_calls: list[int] = []

    def list_operations_candidates(self, kind, days, limit, max_comments):
        self.candidate_calls.append((kind, days, limit, max_comments))
        now = datetime(2026, 7, 12, 9, 0)
        return [
            OperationsCandidate(
                "42", "主从复制排查", "一份真实排查记录", "技术", ["MySQL"],
                0, 3, 2, 80, 12.5, True, now, now, "/t/42"
            )
        ]

    def get_community_health(self, days):
        self.health_calls.append(days)
        return CommunityHealth(8, 12, 7, 2, 6, 320)


class CommunityInsightsContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repository = FakeRepository()
        self.service = CommunityInsightsService(self.repository)
        main_module.app.state.insights_service = self.service

    def test_weekly_digest_is_typed_and_ranked(self) -> None:
        response = main_module.weekly_digest(7, 6)
        self.assertEqual(7, response.window_days)
        self.assertEqual("42", response.highlights[0].post_id)
        self.assertIn("精华", response.highlights[0].reason)
        self.assertGreater(response.highlights[0].score, 0)
        self.assertEqual([("weekly", 7, 6, 0)], self.repository.candidate_calls)

    def test_unanswered_questions_preserve_reply_threshold(self) -> None:
        response = main_module.unanswered_questions(14, 5, 0)
        self.assertEqual(0, response.max_comments)
        self.assertIn("尚无回复", response.questions[0].reason)
        self.assertEqual([("engagement", 14, 5, 0)], self.repository.candidate_calls)

    def test_community_health_derives_bounded_metrics(self) -> None:
        response = main_module.community_health(7)
        self.assertEqual(0.75, response.response_rate)
        self.assertEqual(1.5, response.comments_per_post)
        self.assertGreaterEqual(response.health_score, 0)
        self.assertLessEqual(response.health_score, 100)
        self.assertEqual([7], self.repository.health_calls)

    def test_invalid_bounds_fail_before_replica_query(self) -> None:
        with self.assertRaises(HTTPException) as raised:
            main_module.unanswered_questions(90, 8, 0)
        self.assertEqual(422, raised.exception.status_code)
        self.assertEqual([], self.repository.candidate_calls)


if __name__ == "__main__":
    unittest.main()
