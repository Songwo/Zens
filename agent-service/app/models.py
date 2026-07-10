from __future__ import annotations

from typing import List, Literal, Optional

from pydantic import BaseModel, Field


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=400)
    retrieval_query: Optional[str] = Field(default=None, max_length=500)
    conversation_context: Optional[str] = Field(default=None, max_length=2000)
    section_id: Optional[int] = None
    limit: Optional[int] = Field(default=None, ge=1, le=20)
    include_comments: bool = True
    comments_per_post: Optional[int] = Field(default=None, ge=0, le=10)


class SearchOnlyRequest(BaseModel):
    question: str = Field(min_length=1, max_length=400)
    section_id: Optional[int] = None
    limit: Optional[int] = Field(default=None, ge=1, le=20)
    include_comments: bool = True
    comments_per_post: Optional[int] = Field(default=None, ge=0, le=10)


class RelatedPost(BaseModel):
    post_id: str
    title: str
    section_name: Optional[str] = None
    summary: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
    score: float = 0.0
    url: str


class Citation(BaseModel):
    index: int
    post_id: str
    title: str
    section_name: Optional[str] = None
    excerpt: str
    source_type: Literal["post", "comment"] = "post"
    comment_id: Optional[str] = None
    url: str


class AskTrace(BaseModel):
    backend: str
    retrieval_ms: int
    llm_ms: int
    total_ms: int
    hit_count: int


class AskResponse(BaseModel):
    answer: str
    confidence: Literal["low", "medium", "high"]
    citations: List[Citation]
    related_posts: List[RelatedPost]
    trace: AskTrace


class SearchResponse(BaseModel):
    citations: List[Citation]
    related_posts: List[RelatedPost]
    hit_count: int
    backend: str


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    service: str = "community-qa-agent"
    version: str = "0.1.0"
    uptime_seconds: int = 0
    backend: str
    postgres: str = "not_configured"
    mysql: str = "not_configured"
    mysql_replica: str = "not_configured"
    replica_read_only: Optional[bool] = None
    replica_read_only_required: bool = False
    error: Optional[str] = None
    search_backend: str = "auto"
    llm_enabled: bool = False
    llm_configured: bool = False
    llm_status: str = "disabled"
    llm_model: Optional[str] = None
    default_search_limit: int = 6
    min_question_length: int = 2
    default_comments_per_post: int = 2
