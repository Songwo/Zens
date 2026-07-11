from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


class Citation(BaseModel):
    post_id: str
    title: str
    excerpt: str = ""
    section_name: str | None = None
    url: str | None = None

    @field_validator("post_id", "title", mode="before")
    @classmethod
    def normalize_required_text(cls, value: Any) -> str:
        return "" if value is None else str(value).strip()


class RelatedPost(BaseModel):
    post_id: str
    title: str
    summary: str = ""
    section_name: str | None = None
    tags: list[str] = Field(default_factory=list)
    score: float = 0
    url: str | None = None

    @field_validator("post_id", "title", mode="before")
    @classmethod
    def normalize_required_text(cls, value: Any) -> str:
        return "" if value is None else str(value).strip()


class OperationsCandidate(RelatedPost):
    comment_count: int = 0
    like_count: int = 0
    collect_count: int = 0
    view_count: int = 0
    heat_score: float = 0
    is_featured: bool = False
    reason: str = ""
    created_at: datetime | None = None
    last_activity_at: datetime | None = None


class ResearchResult(BaseModel):
    citations: list[Citation] = Field(default_factory=list)
    related_posts: list[RelatedPost] = Field(default_factory=list)
    hit_count: int = 0
    backend: str = "unknown"

    def unique_posts(self) -> list[RelatedPost]:
        """Merge both Agent search result collections into valid, unique posts."""
        posts: list[RelatedPost] = []
        seen: set[str] = set()
        for item in self.related_posts:
            if not item.post_id or not item.title or item.post_id in seen:
                continue
            seen.add(item.post_id)
            posts.append(item)
        for item in self.citations:
            if not item.post_id or not item.title or item.post_id in seen:
                continue
            seen.add(item.post_id)
            posts.append(RelatedPost(
                post_id=item.post_id,
                title=item.title,
                summary=item.excerpt,
                section_name=item.section_name,
                url=item.url,
            ))
        return posts


class TopicPlan(BaseModel):
    topic: str
    title: str
    brief: str
    section_id: int | None = None
    tags: list[str] = Field(default_factory=list)
    sensitive: bool = False
    scheduled_at: datetime | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class Draft(BaseModel):
    title: str
    content: str
    section_id: int | None = None
    tags: list[str] = Field(default_factory=list)
    cover_image: str | None = None
    sensitive: bool = False
    metadata: dict[str, Any] = Field(default_factory=dict)


class DraftCreate(BaseModel):
    idempotencyKey: str
    type: Literal["POST", "COMMENT"]
    title: str = ""
    content: str
    sectionId: int | None = None
    tags: str = ""
    coverImage: str | None = None
    targetPostId: str | None = None
    parentCommentId: str | None = None
    metadataJson: str = "{}"
    planId: str | None = None
