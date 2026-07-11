from unittest.mock import AsyncMock

import pytest

from zens_ops.config import Settings
from datetime import datetime, timezone

from zens_ops.models import Draft, TopicPlan
from zens_ops.service import OpsService


@pytest.mark.asyncio
async def test_dry_run_never_calls_network_and_always_marks_approval():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    result = await service.save_draft(Draft(title="普通话题", content="原创内容"))
    assert result["dryRun"] is True
    assert result["payload"]["metadataJson"].find('"requiresHumanApproval": true') >= 0
    await service.close()


@pytest.mark.asyncio
async def test_post_limit_and_no_publish_path():
    service = OpsService(Settings(_env_file=None, dry_run=True, max_posts_per_day=1))
    service.planner.plan = AsyncMock(return_value=TopicPlan(topic="t", title="T", brief="b"))
    service.writer.write = AsyncMock(return_value=Draft(title="T", content="body"))
    first = await service.plan_daily()
    second = await service.plan_daily()
    assert first.get("dryRun") is True
    assert second == {"skipped": True, "reason": "daily_post_limit"}
    await service.close()


@pytest.mark.asyncio
async def test_kill_switch_stops_jobs():
    service = OpsService(Settings(_env_file=None, kill_switch=True))
    with pytest.raises(RuntimeError, match="KILL_SWITCH"):
        await service.plan_daily()
    await service.close()


def test_chinese_sensitive_topic_requires_approval():
    service = OpsService(Settings(_env_file=None))
    assert service._is_sensitive(Draft(title="医疗经验", content="仅供讨论")) is True


@pytest.mark.asyncio
async def test_plan_id_is_propagated_to_draft_create():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    service.planner.plan = AsyncMock(return_value=TopicPlan(
        topic="t", title="T", brief="b", scheduled_at=datetime(2026, 7, 12, 9, 30, tzinfo=timezone.utc),
    ))
    service.writer.write = AsyncMock(return_value=Draft(title="T", content="body", section_id=1, tags=["长期表达", "社区"]))
    original_request = service.main.request
    calls = []

    async def request(method, path, payload=None, **kwargs):
        calls.append((path, payload))
        if path == "/api/internal/ops/plans":
            return {"id": "OPSP_42"}
        return await original_request(method, path, payload, **kwargs)

    service.main.request = request
    await service.plan_daily()
    plan_payload = next(payload for path, payload in calls if path == "/api/internal/ops/plans")
    draft_payload = next(payload for path, payload in calls if path == "/api/internal/ops/drafts")
    assert plan_payload["scheduledAt"] == "2026-07-12T09:30:00"
    assert draft_payload["planId"] == "OPSP_42"
    assert draft_payload["tags"] == "长期表达,社区"
    await service.close()


@pytest.mark.asyncio
async def test_comment_contract_propagates_parent_comment_id():
    service = OpsService(Settings(_env_file=None, dry_run=True))
    result = await service.save_draft(Draft(
        title="",
        content="继续补充一个经过人工复核的回复。",
        metadata={"targetPostId": "POST_1", "parentCommentId": "COMMENT_1"},
    ), kind="COMMENT")
    assert result["payload"]["targetPostId"] == "POST_1"
    assert result["payload"]["parentCommentId"] == "COMMENT_1"
    await service.close()
