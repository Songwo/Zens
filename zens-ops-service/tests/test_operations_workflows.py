from datetime import datetime
from unittest.mock import AsyncMock

import httpx
import pytest

from zens_ops.clients.http import AgentClient, RemoteError
from zens_ops.config import Settings
from zens_ops.features.engagement import Engagement
from zens_ops.features.topic_planner import TopicPlanner
from zens_ops.features.weekly_digest import WeeklyDigest
from zens_ops.models import Citation, Draft, ResearchResult
from zens_ops.service import OpsService


@pytest.mark.asyncio
async def test_agent_candidates_accepts_items_contract_and_filters_invalid_duplicates():
    captured = {}

    async def handler(request: httpx.Request):
        captured["request"] = request
        return httpx.Response(200, json={"backend": "mysql", "items": [
            {"post_id": " P1 ", "title": " 第一篇 ", "summary": "内容", "score": 3.5},
            {"post_id": "P1", "title": "重复项"},
            {"post_id": "", "title": "空 ID"},
            {"post_id": "P2", "title": "   "},
        ]})

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    agent = AgentClient(Settings(_env_file=None, agent_base_url="http://agent.test", http_retries=0), http)
    result = await agent.operations_candidates("engagement", limit=12, max_comments=1)

    assert [(item.post_id, item.title) for item in result] == [("P1", "第一篇")]
    assert captured["request"].method == "GET"
    assert captured["request"].url.path == "/v1/operations/candidates"
    assert captured["request"].url.params["kind"] == "engagement"
    assert captured["request"].url.params["limit"] == "12"
    await agent.close()


@pytest.mark.asyncio
async def test_agent_candidates_supports_candidates_envelope_and_legacy_time_name():
    async def handler(request: httpx.Request):
        return httpx.Response(200, json={"data": {"candidates": [{
            "post_id": "P8", "title": "兼容候选", "create_time": "2026-07-11T10:00:00",
            "comment_count": 0, "like_count": 2, "view_count": 9,
        }]}})

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    agent = AgentClient(Settings(_env_file=None, agent_base_url="http://agent.test", http_retries=0), http)
    result = await agent.operations_candidates("weekly")
    assert result and result[0].post_id == "P8"
    assert result[0].created_at is not None
    await agent.close()


@pytest.mark.asyncio
async def test_agent_candidates_only_falls_back_when_endpoint_is_not_supported():
    status = 404

    async def handler(request: httpx.Request):
        return httpx.Response(status, json={"detail": "unavailable"})

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    agent = AgentClient(Settings(_env_file=None, agent_base_url="http://agent.test", http_retries=0), http)
    assert await agent.operations_candidates("engagement") is None

    status = 500
    with pytest.raises(RemoteError):
        await agent.operations_candidates("engagement")
    await agent.close()


@pytest.mark.asyncio
async def test_engagement_falls_back_on_missing_candidate_api_and_consumes_related_posts():
    agent = AsyncMock()
    agent.operations_candidates.return_value = None
    agent.search.return_value = ResearchResult(
        related_posts=[
            {"post_id": "R1", "title": "相关帖子也必须被消费", "summary": "摘要"},
            {"post_id": "R1", "title": "重复"},
            {"post_id": "", "title": "无效"},
        ],
        citations=[Citation(post_id="C1", title="引用帖子", excerpt="正文")],
    )

    drafts = await Engagement(agent).candidates()

    assert [item.metadata["targetPostId"] for item in drafts] == ["R1", "C1"]
    assert all(item.metadata["source"] == "engagement-search" for item in drafts)


@pytest.mark.asyncio
async def test_empty_authoritative_candidate_result_does_not_trigger_broad_search():
    agent = AsyncMock()
    agent.operations_candidates.return_value = []
    assert await Engagement(agent).candidates() == []
    agent.search.assert_not_awaited()


@pytest.mark.asyncio
async def test_topic_planner_uses_related_post_when_citations_are_empty():
    agent = AsyncMock()
    agent.search.return_value = ResearchResult(
        related_posts=[{"post_id": "R9", "title": "独立作品的长期维护", "summary": "摘要"}],
        hit_count=1,
    )
    plan = await TopicPlanner(agent, Settings(_env_file=None)).plan()
    assert plan.topic == "独立作品的长期维护"
    assert plan.metadata["sourcePostIds"] == ["R9"]


@pytest.mark.asyncio
async def test_weekly_digest_skips_empty_candidates_and_deduplicates_sources():
    agent = AsyncMock()
    agent.operations_candidates.return_value = []
    digest = WeeklyDigest(agent, Settings(_env_file=None))
    assert await digest.build() is None

    agent.operations_candidates.return_value = None
    agent.search.return_value = ResearchResult(
        related_posts=[{"post_id": "P1", "title": "本周内容", "summary": "值得回看"}],
        citations=[Citation(post_id="P1", title="重复引用"), Citation(post_id="P2", title="另一篇")],
    )
    draft = await digest.build()
    assert draft is not None
    assert draft.metadata["sourcePostIds"] == ["P1", "P2"]
    assert draft.metadata["periodKey"].startswith("20")
    assert draft.content.count("## 本周内容") == 1


def test_weekly_period_key_uses_iso_week_and_configured_timezone():
    digest = WeeklyDigest(AsyncMock(), Settings(_env_file=None, timezone="Asia/Shanghai"))
    assert digest.period_key(datetime.fromisoformat("2026-01-01T00:30:00+08:00")) == "2026-W01"
    assert digest.period_key(datetime.fromisoformat("2025-12-29T23:30:00+00:00")) == "2026-W01"


@pytest.mark.asyncio
async def test_comment_requires_non_empty_target_post_contract():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    with pytest.raises(ValueError, match="targetPostId"):
        await service.save_draft(Draft(title="", content="待审核评论"), "COMMENT")
    await service.close()


@pytest.mark.asyncio
async def test_real_write_retries_are_idempotent_and_only_submit_for_approval():
    service = OpsService(Settings(
        _env_file=None, environment="test", dry_run=False, service_secret="x" * 32,
        main_site_base_url="http://127.0.0.1:7800", agent_base_url="http://127.0.0.1:7810",
    ))
    calls = []

    async def request(method, path, payload=None, **kwargs):
        calls.append((method, path, payload, kwargs.get("idempotency_key")))
        if path == "/api/internal/ops/drafts":
            return {"id": "DRAFT_1", "status": "CREATED"}
        return {"id": "DRAFT_1", "status": "PENDING_APPROVAL"}

    service.main.request = AsyncMock(side_effect=request)
    draft = Draft(
        title="", content="这是一个必须经过人工审批的评论草稿。",
        metadata={"targetPostId": "POST_1"},
    )
    first = await service.save_draft(draft, "COMMENT")
    second = await service.save_draft(draft, "COMMENT")

    create_calls = [call for call in calls if call[1] == "/api/internal/ops/drafts"]
    submit_calls = [call for call in calls if call[1].endswith("/submit")]
    assert first["status"] == second["status"] == "PENDING_APPROVAL"
    assert create_calls[0][2]["targetPostId"] == "POST_1"
    assert create_calls[0][3] == create_calls[1][3]
    assert submit_calls[0][3] == submit_calls[1][3]
    assert all("publish" not in path for _, path, _, _ in calls)
    await service.close()


@pytest.mark.asyncio
async def test_weekly_idempotency_reuses_same_week_but_changes_across_weeks():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    common = {"title": "Zens 本周精选", "content": "本周有一篇值得长期回看的内容。", "tags": ["每周精选"]}
    first = await service.save_draft(Draft(**common, metadata={"periodKey": "2026-W28"}))
    retry = await service.save_draft(Draft(**common, metadata={"periodKey": "2026-W28"}))
    next_week = await service.save_draft(Draft(**common, metadata={"periodKey": "2026-W29"}))
    assert first["payload"]["idempotencyKey"] == retry["payload"]["idempotencyKey"]
    assert first["payload"]["idempotencyKey"] != next_week["payload"]["idempotencyKey"]
    await service.close()


@pytest.mark.asyncio
async def test_dry_run_is_reported_as_pending_approval_without_publish():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    draft = Draft(title="合规草稿", content="这是一篇仅进入人工审批队列、不会自动发布的原创草稿内容。")
    result = await service.save_draft(draft)
    retry = await service.save_draft(draft)
    assert result["status"] == "PENDING_APPROVAL"
    assert result["path"] == "/api/internal/ops/drafts"
    assert result["payload"]["metadataJson"].find('"requiresHumanApproval": true') >= 0
    assert result["payload"]["idempotencyKey"] == retry["payload"]["idempotencyKey"]
    await service.close()
