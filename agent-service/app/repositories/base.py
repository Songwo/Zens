from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import List, Literal, Protocol, Sequence


@dataclass(slots=True)
class PostHit:
    post_id: str
    title: str
    summary: str
    content_snippet: str
    section_name: str | None
    tags: List[str]
    score: float
    url: str


@dataclass(slots=True)
class CommentHit:
    comment_id: str
    post_id: str
    content: str
    like_count: int


@dataclass(slots=True)
class OperationsCandidate:
    post_id: str
    title: str
    summary: str
    section_name: str | None
    tags: List[str]
    comment_count: int
    like_count: int
    collect_count: int
    view_count: int
    heat_score: float
    is_featured: bool
    created_at: datetime
    last_activity_at: datetime
    url: str


class SearchRepository(Protocol):
    backend_name: str

    def open(self) -> None: ...

    def close(self) -> None: ...

    def ping(self) -> bool: ...

    def search_posts(self, question: str, section_id: int | None, limit: int) -> List[PostHit]: ...

    def fetch_top_comments(self, post_ids: Sequence[str], per_post: int) -> List[CommentHit]: ...

    def list_operations_candidates(
        self,
        kind: Literal["engagement", "weekly"],
        days: int,
        limit: int,
        max_comments: int,
    ) -> List[OperationsCandidate]: ...
