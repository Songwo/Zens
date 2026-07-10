from __future__ import annotations

import time
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from app.config import get_settings
from app.models import AskRequest, AskResponse, HealthResponse, SearchResponse
from app.repositories.base import SearchRepository
from app.repositories.mysql_search import MysqlSearchRepository, ReplicaSafetyError
from app.repositories.postgres_search import PostgresSearchRepository
from app.services.community_qa import CommunityQaService
from app.services.llm_client import LlmClient

STARTED_AT = time.time()


def build_repository() -> SearchRepository:
    settings = get_settings()
    backend = settings.selected_backend
    if backend == "postgres":
        return PostgresSearchRepository(settings)
    if backend == "mysql":
        return MysqlSearchRepository(settings)
    raise ValueError(f"unsupported backend: {backend}")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    repository = build_repository()
    repository.open()
    app.state.settings = settings
    app.state.repository = repository
    app.state.qa_service = CommunityQaService(
        settings=settings,
        repository=repository,
        llm_client=LlmClient(settings),
    )
    try:
        yield
    finally:
        repository.close()


app = FastAPI(
    title="Community QA Agent Service",
    version="0.1.0",
    lifespan=lifespan,
)

settings = get_settings()
if settings.allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


def build_health_response() -> HealthResponse:
    settings = get_settings()
    repository: SearchRepository = app.state.repository
    repository_error: str | None = None
    try:
        repository_ok = repository.ping()
    except Exception as exc:
        repository_ok = False
        repository_error = str(exc) if isinstance(exc, ReplicaSafetyError) else "database health check failed"
    active_backend = repository.backend_name
    replica_read_only = getattr(repository, "replica_read_only", None)
    replica_read_only_required = bool(getattr(repository, "replica_read_only_required", False))
    mysql_status = (
        "ok"
        if active_backend == "mysql" and repository_ok
        else ("down" if active_backend == "mysql" else "not_configured")
    )
    llm_configured = bool(settings.llm_api_key.strip())
    if not settings.llm_enabled:
        llm_status = "disabled"
    elif llm_configured:
        llm_status = "ready"
    else:
        llm_status = "missing_api_key"
    return HealthResponse(
        status="ok" if repository_ok else "degraded",
        uptime_seconds=max(0, int(time.time() - STARTED_AT)),
        backend=active_backend,
        postgres="ok" if active_backend == "postgres" and repository_ok else ("down" if active_backend == "postgres" else "not_configured"),
        mysql=mysql_status,
        mysql_replica=mysql_status,
        replica_read_only=replica_read_only,
        replica_read_only_required=replica_read_only_required,
        error=repository_error,
        search_backend=settings.normalized_search_backend,
        llm_enabled=settings.llm_enabled,
        llm_configured=llm_configured,
        llm_status=llm_status,
        llm_model=settings.llm_model if settings.llm_enabled else None,
        default_search_limit=settings.default_search_limit,
        min_question_length=settings.min_question_length,
        default_comments_per_post=settings.default_comments_per_post,
    )


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return build_health_response()


@app.get("/ready", response_model=HealthResponse)
def ready(response: Response) -> HealthResponse:
    health_response = build_health_response()
    if health_response.status != "ok":
        response.status_code = 503
    return health_response


@app.post("/v1/community-qa/search", response_model=SearchResponse)
def search_only(request: AskRequest) -> SearchResponse:
    service: CommunityQaService = app.state.qa_service
    try:
        return service.search_only(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"search failed: {exc}") from exc


@app.post("/v1/community-qa/ask", response_model=AskResponse)
async def ask(request: AskRequest) -> AskResponse:
    service: CommunityQaService = app.state.qa_service
    try:
        return await service.ask(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"qa failed: {exc}") from exc


@app.post("/v1/community-qa/ask-stream")
async def ask_stream(request: AskRequest) -> StreamingResponse:
    service: CommunityQaService = app.state.qa_service
    headers = {
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
    }
    return StreamingResponse(
        service.ask_stream(request),
        media_type="text/event-stream",
        headers=headers,
    )
