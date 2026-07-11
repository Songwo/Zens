from __future__ import annotations

import hashlib
import json
import logging
from datetime import datetime
from zoneinfo import ZoneInfo

from zens_ops.clients.http import AgentClient, MainSiteClient
from zens_ops.config import Settings
from zens_ops.features.engagement import Engagement
from zens_ops.features.metrics import MetricsReporter
from zens_ops.features.topic_planner import TopicPlanner
from zens_ops.features.weekly_digest import WeeklyDigest
from zens_ops.features.writer import Writer
from zens_ops.models import Draft, DraftCreate

log = logging.getLogger(__name__)


class OpsService:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.agent = AgentClient(settings)
        self.main = MainSiteClient(settings)
        self.tz = ZoneInfo(settings.timezone)
        self.planner = TopicPlanner(self.agent, settings)
        self.writer = Writer(settings)
        self.engagement = Engagement(self.agent)
        self.digest = WeeklyDigest(self.agent, settings)
        self.metrics = MetricsReporter(self.main)
        self._budget_date = datetime.now(self.tz).date()
        self._posts_today = 0
        self._replies_today = 0

    def _refresh_budget(self) -> None:
        today = datetime.now(self.tz).date()
        if today != self._budget_date:
            self._budget_date = today
            self._posts_today = 0
            self._replies_today = 0

    def _idem(self, scope: str, value: str) -> str:
        return f"zens-ops:{scope}:{hashlib.sha256(value.encode()).hexdigest()[:24]}"

    def _is_sensitive(self, draft: Draft) -> bool:
        text = f"{draft.title} {draft.content} {' '.join(draft.tags)}".lower()
        return draft.sensitive or any(topic in text for topic in self.settings.sensitive_topic_set)

    async def save_draft(self, draft: Draft, kind: str = "POST", plan_id: str | None = None) -> dict:
        kind = kind.upper()
        if kind not in {"POST", "COMMENT"}:
            raise ValueError("draft kind must be POST or COMMENT")
        metadata = draft.metadata | {"requiresHumanApproval": True, "sensitive": self._is_sensitive(draft), "draftOnly": self.settings.draft_only}
        target_post_id = str(metadata.get("targetPostId") or "").strip() or None
        parent_comment_id = str(metadata.get("parentCommentId") or "").strip() or None
        if kind == "COMMENT" and not target_post_id:
            raise ValueError("COMMENT draft requires targetPostId")
        fingerprint = json.dumps({
            "type": kind, "title": draft.title.strip(), "content": draft.content,
            "sectionId": draft.section_id, "tags": draft.tags, "targetPostId": target_post_id,
            "parentCommentId": parent_comment_id, "planId": plan_id,
            "periodKey": metadata.get("periodKey"),
        }, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        idem = self._idem(kind.lower(), fingerprint)
        body = DraftCreate(
            idempotencyKey=idem, type=kind, title=draft.title, content=draft.content,
            sectionId=draft.section_id, tags=",".join(draft.tags), coverImage=draft.cover_image,
            targetPostId=target_post_id, parentCommentId=parent_comment_id,
            metadataJson=json.dumps(metadata, ensure_ascii=False),
            planId=plan_id,
        ).model_dump()
        result = await self.main.request("POST", "/api/internal/ops/drafts", body, idempotency_key=idem)
        draft_id = result.get("id") or result.get("draftId")
        if draft_id and not self.settings.dry_run:
            await self.main.request(
                "POST", f"/api/internal/ops/drafts/{draft_id}/submit", {},
                idempotency_key=f"{idem}:submit",
            )
            result = result | {"status": "PENDING_APPROVAL"}
        elif self.settings.dry_run:
            result = result | {"status": "PENDING_APPROVAL"}
        return result

    async def plan_daily(self) -> dict:
        self._guard()
        self._refresh_budget()
        if self._posts_today >= self.settings.max_posts_per_day:
            return {"skipped": True, "reason": "daily_post_limit"}
        plan = await self.planner.plan()
        plan_key = self._idem("plan", f"{datetime.now(self.tz).date()}:{plan.topic}")
        created_plan = await self.main.request("POST", "/api/internal/ops/plans", {
            "idempotencyKey": plan_key, "topic": plan.topic, "title": plan.title, "brief": plan.brief,
            "scheduledAt": plan.scheduled_at.replace(tzinfo=None).isoformat() if plan.scheduled_at else None,
            "metadataJson": json.dumps(plan.metadata, ensure_ascii=False),
        }, idempotency_key=plan_key)
        raw_plan_id = created_plan.get("id") or created_plan.get("planId")
        plan_id = str(raw_plan_id) if raw_plan_id is not None else None
        result = await self.save_draft(await self.writer.write(plan), plan_id=plan_id)
        self._posts_today += 1
        return result

    async def scan_engagement(self) -> list[dict]:
        self._guard()
        self._refresh_budget()
        drafts = await self.engagement.candidates()
        remaining = max(0, self.settings.max_replies_per_day - self._replies_today)
        results = [await self.save_draft(item, "COMMENT") for item in drafts[:remaining]]
        self._replies_today += len(results)
        return results

    async def weekly(self) -> dict:
        self._guard()
        draft = await self.digest.build()
        if draft is None:
            return {"skipped": True, "reason": "no_weekly_candidates"}
        return await self.save_draft(draft)

    async def report_metrics(self) -> dict:
        self._guard()
        return await self.metrics.report()

    def _guard(self) -> None:
        if self.settings.kill_switch:
            raise RuntimeError("OPS_KILL_SWITCH is enabled")

    async def health(self) -> dict:
        agent_ready = await self.agent.ready()
        main_ready = await self.main.ready()
        return {"status": "ok" if agent_ready and main_ready else "degraded", "agentReady": agent_ready, "mainSiteReady": main_ready, "dryRun": self.settings.dry_run, "draftOnly": self.settings.draft_only, "killSwitch": self.settings.kill_switch}

    async def close(self) -> None:
        await self.agent.close()
        await self.main.close()
