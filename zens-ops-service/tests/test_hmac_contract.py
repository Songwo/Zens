import hashlib
import hmac
import json

import httpx
import pytest

from zens_ops.clients.http import MainSiteClient, canonical_json, signed_headers
from zens_ops.config import Settings


def test_signature_matches_spring_contract():
    body = canonical_json({"content": "中文", "title": "draft"})
    headers = signed_headers("s" * 32, "zens-ops", "POST", "/api/internal/ops/drafts", body, timestamp="1700000000000", nonce="abc123456789012345678901")
    body_hash = hashlib.sha256(body).hexdigest()
    payload = f"POST\n/api/internal/ops/drafts\n1700000000000\nabc123456789012345678901\n{body_hash}"
    expected = hmac.new(("s" * 32).encode(), payload.encode(), hashlib.sha256).hexdigest()
    assert headers["X-Service-Id"] == "zens-ops"
    assert headers["X-Service-Signature"] == expected


@pytest.mark.asyncio
async def test_main_site_uses_exact_raw_body_and_idempotency_header():
    captured = {}

    async def handler(request: httpx.Request):
        captured["request"] = request
        return httpx.Response(200, json={"data": {"id": "D1"}})

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(transport=transport)
    settings = Settings(_env_file=None, environment="test", dry_run=False, service_secret="x" * 32, main_site_base_url="http://main.test", http_retries=0)
    api = MainSiteClient(settings, client)
    await api.request("POST", "/api/internal/ops/drafts", {"b": 2, "a": "中"}, idempotency_key="idem-1")
    request = captured["request"]
    assert request.content == json.dumps({"b": 2, "a": "中"}, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
    assert request.headers["Idempotency-Key"] == "idem-1"
    assert request.headers["X-Service-Id"] == "zens-ops"
    body_hash = hashlib.sha256(request.content).hexdigest()
    signing = "\n".join((
        request.method,
        request.url.path,
        request.headers["X-Service-Timestamp"],
        request.headers["X-Service-Nonce"],
        body_hash,
    )).encode()
    expected = hmac.new(("x" * 32).encode(), signing, hashlib.sha256).hexdigest()
    assert request.headers["X-Service-Signature"] == expected
    await api.close()


@pytest.mark.asyncio
async def test_status_get_signs_an_exact_empty_body():
    captured = {}

    async def handler(request: httpx.Request):
        captured["request"] = request
        return httpx.Response(200, json={"code": 200, "data": {"circuitOpen": True}})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    settings = Settings(_env_file=None, environment="test", dry_run=False, service_secret="x" * 32, main_site_base_url="http://main.test", http_retries=0)
    api = MainSiteClient(settings, client)
    result = await api.request("GET", "/api/internal/ops/status")
    request = captured["request"]
    assert request.content == b""
    assert "Idempotency-Key" not in request.headers
    assert result == {"circuitOpen": True}
    body_hash = hashlib.sha256(b"").hexdigest()
    signing = "\n".join((
        "GET", "/api/internal/ops/status", request.headers["X-Service-Timestamp"],
        request.headers["X-Service-Nonce"], body_hash,
    )).encode()
    assert request.headers["X-Service-Signature"] == hmac.new(
        ("x" * 32).encode(), signing, hashlib.sha256,
    ).hexdigest()
    await api.close()


@pytest.mark.asyncio
async def test_client_rejects_non_ops_path():
    api = MainSiteClient(Settings(_env_file=None))
    with pytest.raises(ValueError):
        await api.request("POST", "/api/internal/user/write", {})
    await api.close()


@pytest.mark.asyncio
async def test_runner_client_cannot_call_publish_or_admin_operations():
    api = MainSiteClient(Settings(_env_file=None))
    with pytest.raises(ValueError, match="allowlist"):
        await api.request("POST", "/api/internal/ops/drafts/D1/publish", {"idempotencyKey": "publish-D1"})
    with pytest.raises(ValueError, match="allowlist"):
        await api.request("POST", "/api/internal/ops/circuit", {"open": False})
    await api.close()
