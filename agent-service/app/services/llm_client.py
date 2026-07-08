from __future__ import annotations

import json
import time
from typing import AsyncIterator, Iterable, Tuple

import httpx

from app.config import Settings
from app.models import Citation


class LlmClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def enabled(self) -> bool:
        return self._settings.llm_enabled and bool(self._settings.llm_api_key.strip())

    def _build_prompts(
        self,
        question: str,
        citations: Iterable[Citation],
        conversation_context: str | None = None,
    ) -> tuple[str, str]:
        evidence_blocks = []
        for item in citations:
            evidence_blocks.append(
                f"[{item.index}] 标题: {item.title}\n"
                f"板块: {item.section_name or '未分类'}\n"
                f"摘录: {item.excerpt}\n"
            )

        system_prompt = (
            "你是 Zens Community Copilot。"
            "你只能基于提供的社区历史讨论回答问题，不能编造不存在的结论。"
            "请用简洁中文回答，并在关键结论后使用 [1][2] 这种引用标记。"
            "如果证据不足，请明确说明社区当前没有足够信息。"
        )
        user_prompt = (
            f"用户问题：{question.strip()}\n\n"
            "以下是社区检索到的历史讨论证据：\n"
            f"{chr(10).join(evidence_blocks)}\n"
        )
        if conversation_context and conversation_context.strip():
            user_prompt += (
                "\n补充上下文（来自上一轮对话，可帮助理解本轮追问，但不能替代检索证据）：\n"
                f"{conversation_context.strip()}\n"
            )
        user_prompt += "请输出最终回答。"
        return system_prompt, user_prompt

    def _build_payload(
        self,
        question: str,
        citations: Iterable[Citation],
        *,
        stream: bool,
        conversation_context: str | None = None,
    ) -> dict:
        system_prompt, user_prompt = self._build_prompts(question, citations, conversation_context)
        payload = {
            "model": self._settings.llm_model,
            "temperature": self._settings.llm_temperature,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        if stream:
            payload["stream"] = True
        return payload

    def _build_headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._settings.llm_api_key}",
            "Content-Type": "application/json",
        }

    async def answer(
        self,
        question: str,
        citations: Iterable[Citation],
        conversation_context: str | None = None,
    ) -> Tuple[str, int]:
        started_at = time.perf_counter()
        payload = self._build_payload(
            question,
            citations,
            stream=False,
            conversation_context=conversation_context,
        )
        headers = self._build_headers()
        async with httpx.AsyncClient(timeout=self._settings.llm_timeout_seconds) as client:
            response = await client.post(
                f"{self._settings.llm_base_url.rstrip('/')}/chat/completions",
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
        text = (
            data.get("choices", [{}])[0]
            .get("message", {})
            .get("content", "")
            .strip()
        )
        elapsed_ms = int((time.perf_counter() - started_at) * 1000)
        return text, elapsed_ms

    async def stream_answer(
        self,
        question: str,
        citations: Iterable[Citation],
        conversation_context: str | None = None,
    ) -> AsyncIterator[str]:
        payload = self._build_payload(
            question,
            citations,
            stream=True,
            conversation_context=conversation_context,
        )
        headers = self._build_headers()

        async with httpx.AsyncClient(timeout=self._settings.llm_timeout_seconds) as client:
            async with client.stream(
                "POST",
                f"{self._settings.llm_base_url.rstrip('/')}/chat/completions",
                headers=headers,
                json=payload,
            ) as response:
                response.raise_for_status()
                async for raw_line in response.aiter_lines():
                    line = (raw_line or "").strip()
                    if not line or not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if not data or data == "[DONE]":
                        continue
                    chunk = self._extract_stream_delta(data)
                    if chunk:
                        yield chunk

    def _extract_stream_delta(self, raw_data: str) -> str:
        try:
            payload = json.loads(raw_data)
        except ValueError:
            return ""

        choices = payload.get("choices") or []
        if not choices:
            return ""

        delta = choices[0].get("delta") or {}
        content = delta.get("content")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            parts: list[str] = []
            for item in content:
                if isinstance(item, str):
                    parts.append(item)
                    continue
                if not isinstance(item, dict):
                    continue
                text_part = item.get("text")
                if isinstance(text_part, str):
                    parts.append(text_part)
                    continue
                if item.get("type") == "text" and isinstance(item.get("content"), str):
                    parts.append(item["content"])
            return "".join(parts)
        return ""
