from __future__ import annotations

from datetime import datetime, timezone

from app.models import (
    CommunityHealthResponse,
    InsightPost,
    UnansweredQuestionsResponse,
    WeeklyDigestResponse,
)
from app.repositories.base import OperationsCandidate, SearchRepository


class CommunityInsightsService:
    """Deterministic, read-only community insights backed by the search replica."""

    def __init__(self, repository: SearchRepository) -> None:
        self._repository = repository

    def weekly_digest(self, days: int, limit: int) -> WeeklyDigestResponse:
        rows = self._repository.list_operations_candidates("weekly", days, limit, 0)
        return WeeklyDigestResponse(
            window_days=days,
            generated_at=datetime.now(timezone.utc),
            highlights=[self._to_weekly_post(row) for row in rows],
            backend=self._repository.backend_name,
        )

    def unanswered_questions(
        self, days: int, limit: int, max_comments: int
    ) -> UnansweredQuestionsResponse:
        rows = self._repository.list_operations_candidates(
            "engagement", days, limit, max_comments
        )
        return UnansweredQuestionsResponse(
            window_days=days,
            max_comments=max_comments,
            questions=[self._to_unanswered_post(row) for row in rows],
            backend=self._repository.backend_name,
        )

    def community_health(self, days: int) -> CommunityHealthResponse:
        snapshot = self._repository.get_community_health(days)
        response_rate = (
            snapshot.engaged_posts / snapshot.published_posts
            if snapshot.published_posts
            else 0.0
        )
        comments_per_post = (
            snapshot.approved_comments / snapshot.published_posts
            if snapshot.published_posts
            else 0.0
        )
        activity_score = min(35.0, snapshot.published_posts * 4.0)
        response_score = response_rate * 45.0
        contributor_score = min(20.0, snapshot.active_contributors * 2.5)
        health_score = round(activity_score + response_score + contributor_score)

        if health_score >= 70:
            status = "healthy"
            summary = "内容供给和回复覆盖较稳定，适合继续放大优质讨论。"
        elif health_score >= 40:
            status = "watch"
            summary = "社区保持活跃，但仍有部分新帖需要更及时的真实回应。"
        else:
            status = "needs_attention"
            summary = "近期内容或互动偏少，建议优先关注未回复问题和真实创作者。"

        return CommunityHealthResponse(
            window_days=days,
            generated_at=datetime.now(timezone.utc),
            published_posts=snapshot.published_posts,
            approved_comments=snapshot.approved_comments,
            active_contributors=snapshot.active_contributors,
            unanswered_posts=snapshot.unanswered_posts,
            engaged_posts=snapshot.engaged_posts,
            total_views=snapshot.total_views,
            response_rate=round(response_rate, 4),
            comments_per_post=round(comments_per_post, 2),
            health_score=max(0, min(100, health_score)),
            status=status,
            summary=summary,
            backend=self._repository.backend_name,
        )

    def _to_weekly_post(self, row: OperationsCandidate) -> InsightPost:
        score = row.heat_score + row.like_count * 2 + row.collect_count * 3 + row.comment_count
        signals: list[str] = []
        if row.is_featured:
            signals.append("精华")
        if row.comment_count:
            signals.append(f"{row.comment_count} 条讨论")
        if row.collect_count:
            signals.append(f"{row.collect_count} 次收藏")
        reason = " · ".join(signals) or "本周值得回看的新内容"
        return self._to_post(row, score, reason)

    def _to_unanswered_post(self, row: OperationsCandidate) -> InsightPost:
        age_priority = max(0.0, 8.0 - row.comment_count * 2.0)
        score = age_priority + min(6.0, row.view_count / 20.0) + row.collect_count
        reason = (
            "尚无回复，适合提供第一条有依据的帮助"
            if row.comment_count == 0
            else f"目前仅 {row.comment_count} 条回复，仍需要补充经验"
        )
        return self._to_post(row, score, reason)

    @staticmethod
    def _to_post(row: OperationsCandidate, score: float, reason: str) -> InsightPost:
        return InsightPost(
            post_id=row.post_id,
            title=row.title,
            summary=row.summary,
            section_name=row.section_name,
            tags=row.tags,
            comment_count=row.comment_count,
            like_count=row.like_count,
            collect_count=row.collect_count,
            view_count=row.view_count,
            score=round(score, 3),
            reason=reason,
            created_at=row.created_at,
            url=row.url,
        )
