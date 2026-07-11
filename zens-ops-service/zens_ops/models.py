from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class Citation(BaseModel):
    post_id: str
    title: str
    excerpt: str = ""
    section_name: str | None = None
    url: str | None = None


class ResearchResult(BaseModel):
    citations: list[Citation] = Field(default_factory=list)
    related_posts: list[dict[str, Any]] = Field(default_factory=list)
    hit_count: int = 0
    backend: str = "unknown"


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
