from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from zens_ops import __version__
from zens_ops.config import get_settings
from zens_ops.logging import configure_logging
from zens_ops.scheduler import build_scheduler
from zens_ops.service import OpsService

settings = get_settings()
configure_logging(settings.log_level)
service = OpsService(settings)
scheduler = build_scheduler(service, settings)


@asynccontextmanager
async def lifespan(_: FastAPI):
    scheduler.start()
    yield
    scheduler.shutdown(wait=False)
    await service.close()


app = FastAPI(title="Zens Ops Service", version=__version__, lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "zens-ops", "version": __version__, "killSwitch": settings.kill_switch}


@app.get("/ready")
async def ready():
    snapshot = await service.health()
    if snapshot["status"] != "ok":
        raise HTTPException(status_code=503, detail=snapshot)
    return snapshot
