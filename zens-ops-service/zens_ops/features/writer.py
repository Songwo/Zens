from __future__ import annotations

import asyncio
import logging

import httpx

from zens_ops.config import Settings
from zens_ops.models import Draft, TopicPlan

log = logging.getLogger(__name__)


class Writer:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def write(self, plan: TopicPlan) -> Draft:
        if not self.settings.llm_enabled:
            return self._template(plan)
        prompt = (
            "你是 Zens 开放社区官方编辑。写一篇原创 Markdown 草稿，不复制来源，不虚构事实。"
            "结构包含背景、实际过程、取舍、可执行清单和开放问题。语气友善真实。\n"
            f"选题：{plan.topic}\n简报：{plan.brief}"
        )
        headers = {"Authorization": f"Bearer {self.settings.llm_api_key.get_secret_value()}"}
        payload = {"model": self.settings.llm_model, "messages": [{"role": "user", "content": prompt}], "temperature": 0.5}
        try:
            async with httpx.AsyncClient(timeout=self.settings.http_timeout_seconds) as client:
                for attempt in range(self.settings.http_retries + 1):
                    try:
                        response = await client.post(f"{str(self.settings.llm_base_url).rstrip('/')}/chat/completions", headers=headers, json=payload)
                        response.raise_for_status()
                        content = response.json()["choices"][0]["message"]["content"]
                        return Draft(title=plan.title, content=content, section_id=plan.section_id, tags=plan.tags, sensitive=plan.sensitive, metadata=plan.metadata | {"writer": "llm"})
                    except (httpx.HTTPError, KeyError, IndexError, TypeError):
                        if attempt >= self.settings.http_retries:
                            raise
                        await asyncio.sleep(min(0.25 * 2**attempt, 2))
        except Exception as exc:
            log.warning("llm_writer_fallback", extra={"errorType": type(exc).__name__})
            fallback = self._template(plan)
            fallback.metadata["llmFallback"] = True
            return fallback

    def _template(self, plan: TopicPlan) -> Draft:
        content = f"""# {plan.title}

很多值得长期保存的内容，并不是一开始就有标准答案，而是来自一次真实尝试后的复盘。本文围绕“{plan.topic}”整理一个可继续补充的思考框架。

## 先把背景讲清楚

在分享结论之前，可以先说明当时遇到的限制、目标和已有条件。相同做法放在不同背景下，结果可能完全不同。

## 记录过程，而不只展示结果

1. 写下最初的判断以及依据。
2. 记录真正采取的步骤和中途调整。
3. 区分已经验证的事实与个人感受。
4. 保留失败路径，它往往比一句“成功了”更有复用价值。

## 一个可执行的复盘清单

- 哪个问题最值得先解决？
- 做过哪些取舍，代价是什么？
- 哪些证据改变了原来的判断？
- 如果再来一次，第一步会怎样调整？
- 哪部分经验适用于别人，哪部分只适用于当前情境？

## 留给社区的问题

你是否遇到过类似情况？欢迎补充自己的背景、选择与结果。不同答案并不冲突，它们能共同形成更完整的经验地图。
"""
        return Draft(title=plan.title, content=content, section_id=plan.section_id, tags=plan.tags, sensitive=plan.sensitive, metadata=plan.metadata | {"writer": "deterministic-template"})
