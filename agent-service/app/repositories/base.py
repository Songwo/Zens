from __future__ import annotations

from dataclasses import dataclass
from typing import List, Protocol, Sequence


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


class SearchRepository(Protocol):
    backend_name: str

    def open(self) -> None: ...

    def close(self) -> None: ...

    def ping(self) -> bool: ...

    def search_posts(self, question: str, section_id: int | None, limit: int) -> List[PostHit]: ...

    def fetch_top_comments(self, post_ids: Sequence[str], per_post: int) -> List[CommentHit]: ...
