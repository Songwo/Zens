import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from zens_ops.config import Settings
from zens_ops.service import OpsService

log = logging.getLogger(__name__)


def build_scheduler(service: OpsService, settings: Settings) -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler(timezone=settings.timezone)

    async def safe(name: str, call):
        try:
            await call()
            log.info("scheduled_job_complete", extra={"job": name})
        except Exception:
            log.exception("scheduled_job_failed", extra={"job": name})

    scheduler.add_job(safe, CronTrigger(hour=6, minute=30), args=["plan_daily", service.plan_daily], id="plan_daily", max_instances=1, coalesce=True)
    scheduler.add_job(safe, CronTrigger(hour="*/2", minute=15), args=["engagement", service.scan_engagement], id="engagement", max_instances=1, coalesce=True)
    scheduler.add_job(safe, CronTrigger(day_of_week="sun", hour=18, minute=0), args=["weekly", service.weekly], id="weekly", max_instances=1, coalesce=True)
    scheduler.add_job(safe, CronTrigger(day_of_week="mon", hour=8, minute=0), args=["metrics", service.report_metrics], id="metrics", max_instances=1, coalesce=True)
    return scheduler
