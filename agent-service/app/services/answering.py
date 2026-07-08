from __future__ import annotations

from typing import Iterable, List, Literal

from app.models import Citation, RelatedPost


def estimate_confidence(citations: List[Citation]) -> Literal["low", "medium", "high"]:
    if len(citations) >= 5:
        return "high"
    if len(citations) >= 2:
        return "medium"
    return "low"


def build_fallback_answer(
    question: str,
    citations: Iterable[Citation],
    related_posts: Iterable[RelatedPost] | None = None,
) -> str:
    items = list(citations)
    related = list(related_posts or [])
    if not items:
        if related:
            bullets = []
            for index, item in enumerate(related[:2], start=1):
                excerpt = (item.summary or "这条帖子和当前问题只有部分关键词重合").strip()
                bullets.append(f"{index}. 《{item.title}》：{excerpt}")
            return "\n".join(
                [
                    f"社区里暂时没有检索到与“{question.strip()}”高度相关的历史讨论。",
                    "下面这些帖子只是在关键词上有部分交集，可以先做弱相关参考：",
                    *bullets,
                    "如果你愿意继续追问，建议补充报错信息、依赖版本、部署方式或业务场景，检索命中会明显更稳定。",
                ]
            )
        return (
            f"社区里暂时没有检索到与“{question.strip()}”高度相关的历史讨论。"
            "建议你补充使用场景、报错信息、技术栈或期望结果后再试一次。"
        )

    top_items = items[:3]
    intro = (
        f"根据社区历史讨论，和“{question.strip()}”最接近的信息主要集中在以下几个方向："
    )
    bullets = []
    for item in top_items:
        bullets.append(
            f"{item.index}. 《{item.title}》提到：{item.excerpt} [{item.index}]"
        )
    outro = (
        "建议先查看这些原帖和上下文。如果你的实际环境和这些讨论不完全一致，"
        "最好再补充具体报错、依赖版本或部署方式，社区更容易给出准确结论。"
    )
    return "\n".join([intro, *bullets, outro])
