import json
from datetime import datetime, timedelta, timezone

from zens_ops.clients.http import MainSiteClient


class MetricsReporter:
    def __init__(self, main_site: MainSiteClient):
        self.main_site = main_site

    async def report(self) -> dict:
        end = datetime.now(timezone.utc)
        start = end - timedelta(days=7)
        snapshots = {}
        errors = {}
        for name, path in {
            "site": "/api/stats/site",
            "trend": "/api/trend-stat/dashboard",
            "webVitals": "/api/performance/web-vitals/summary",
        }.items():
            try:
                snapshots[name] = await self.main_site.public_get(path)
            except Exception as exc:
                errors[name] = type(exc).__name__
        snapshots["collectionErrors"] = errors
        idem = f"zens-ops:metrics:{start.date()}:{end.date()}"
        payload = {
            "idempotencyKey": idem,
            "periodStart": start.replace(tzinfo=None).isoformat(),
            "periodEnd": end.replace(tzinfo=None).isoformat(),
            "metricsJson": json.dumps(snapshots, ensure_ascii=False, default=str),
        }
        return await self.main_site.request("POST", "/api/internal/ops/metrics", payload, idempotency_key=idem)
