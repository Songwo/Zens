from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import secrets
import time
from typing import Any
from urllib.parse import urlparse

import httpx

from zens_ops.config import Settings
from zens_ops.models import ResearchResult


class RemoteError(RuntimeError):
    pass


def canonical_json(payload: Any) -> bytes:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def signed_headers(secret: str, service_id: str, method: str, path: str, body: bytes, *, timestamp: str | None = None, nonce: str | None = None) -> dict[str, str]:
    timestamp = timestamp or str(int(time.time() * 1000))
    nonce = nonce or secrets.token_hex(16)
    body_hash = hashlib.sha256(body).hexdigest()
    signing = "\n".join((method.upper(), path, timestamp, nonce, body_hash)).encode()
    signature = hmac.new(secret.encode(), signing, hashlib.sha256).hexdigest()
    return {"X-Service-Id": service_id, "X-Service-Timestamp": timestamp, "X-Service-Nonce": nonce, "X-Service-Signature": signature}


class BaseClient:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings = settings
        self.client = client or httpx.AsyncClient(timeout=settings.http_timeout_seconds)

    async def close(self) -> None:
        await self.client.aclose()

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        last: Exception | None = None
        for attempt in range(self.settings.http_retries + 1):
            try:
                response = await self.client.request(method, url, **kwargs)
                if response.status_code < 500:
                    return response
                raise RemoteError(f"upstream {response.status_code}")
            except (httpx.TimeoutException, httpx.TransportError, RemoteError) as exc:
                last = exc
                if attempt >= self.settings.http_retries:
                    break
                await asyncio.sleep(min(0.25 * 2**attempt, 2))
        raise RemoteError(str(last))


class AgentClient(BaseClient):
    async def search(self, question: str, *, limit: int = 10) -> ResearchResult:
        url = f"{str(self.settings.agent_base_url).rstrip('/')}/v1/community-qa/search"
        response = await self._request("POST", url, json={"question": question, "limit": limit, "include_comments": True, "comments_per_post": 2})
        response.raise_for_status()
        return ResearchResult.model_validate(response.json())

    async def ready(self) -> bool:
        response = await self._request("GET", f"{str(self.settings.agent_base_url).rstrip('/')}/ready")
        return response.status_code == 200


class MainSiteClient(BaseClient):
    _ALLOWED_METHODS = {
        ("GET", "/api/internal/ops/status"),
        ("POST", "/api/internal/ops/drafts"),
        ("POST", "/api/internal/ops/plans"),
        ("POST", "/api/internal/ops/metrics"),
    }

    async def public_get(self, path: str) -> dict[str, Any]:
        if not path.startswith("/api/") or path.startswith("/api/internal/"):
            raise ValueError("only public read API paths are allowed")
        response = await self._request("GET", f"{str(self.settings.main_site_base_url).rstrip('/')}{path}")
        response.raise_for_status()
        data = response.json()
        return data.get("data", data) if isinstance(data, dict) else {"data": data}

    async def request(self, method: str, path: str, payload: Any | None = None, *, idempotency_key: str | None = None) -> dict[str, Any]:
        normalized_method = method.upper()
        is_submit = normalized_method == "POST" and path.startswith("/api/internal/ops/drafts/") and path.endswith("/submit")
        if (normalized_method, path) not in self._ALLOWED_METHODS and not is_submit:
            raise ValueError("operation is outside the runner allowlist")
        body = b"" if payload is None else canonical_json(payload)
        if self.settings.dry_run:
            return {"dryRun": True, "method": method, "path": path, "payload": payload}
        secret = self.settings.service_secret
        assert secret is not None
        headers = signed_headers(secret.get_secret_value(), self.settings.service_id, normalized_method, path, body)
        headers["Content-Type"] = "application/json"
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        url = f"{str(self.settings.main_site_base_url).rstrip('/')}{path}"
        response = await self._request(normalized_method, url, content=body or None, headers=headers)
        response.raise_for_status()
        data = response.json()
        return data.get("data", data) if isinstance(data, dict) else {"data": data}

    async def ready(self) -> bool:
        if self.settings.dry_run:
            return True
        try:
            data = await self.request("GET", "/api/internal/ops/status")
            return bool(data)
        except Exception:
            return False
