import argparse
import asyncio
import json

import uvicorn

from zens_ops.config import get_settings
from zens_ops.logging import configure_logging
from zens_ops.service import OpsService


async def run_once(command: str) -> int:
    settings = get_settings()
    service = OpsService(settings)
    try:
        mapping = {"health": service.health, "plan-daily": service.plan_daily, "engagement": service.scan_engagement, "weekly": service.weekly, "metrics": service.report_metrics}
        result = await mapping[command]()
        print(json.dumps(result, ensure_ascii=False, default=str))
        return 0 if command != "health" or result["status"] == "ok" else 1
    finally:
        await service.close()


def main() -> None:
    parser = argparse.ArgumentParser(prog="zens-ops")
    parser.add_argument("command", choices=["serve", "health", "plan-daily", "engagement", "weekly", "metrics"])
    parser.add_argument("--once", action="store_true", help="explicit marker for one-shot operational commands")
    args = parser.parse_args()
    settings = get_settings()
    configure_logging(settings.log_level)
    if args.command == "serve":
        uvicorn.run("zens_ops.api:app", host=settings.health_host, port=settings.health_port)
    else:
        raise SystemExit(asyncio.run(run_once(args.command)))


if __name__ == "__main__":
    main()
