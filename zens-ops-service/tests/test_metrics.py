import json
from unittest.mock import AsyncMock

import pytest

from zens_ops.features.metrics import MetricsReporter


@pytest.mark.asyncio
async def test_metrics_body_contains_same_idempotency_key_as_header():
    main = AsyncMock()
    main.public_get = AsyncMock(return_value={"value": 1})
    main.request = AsyncMock(return_value={"id": 1})
    await MetricsReporter(main).report()
    _, path, payload = main.request.call_args.args
    assert path == "/api/internal/ops/metrics"
    assert payload["idempotencyKey"] == main.request.call_args.kwargs["idempotency_key"]
    assert "+00:00" not in payload["periodStart"]
    assert "+00:00" not in payload["periodEnd"]
    assert json.loads(payload["metricsJson"])["site"] == {"value": 1}
