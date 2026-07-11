from zens_ops.clients.http import AgentClient
from zens_ops.models import Draft


class Engagement:
    def __init__(self, agent: AgentClient):
        self.agent = agent

    async def candidates(self) -> list[Draft]:
        candidates = await self.agent.operations_candidates("engagement", limit=20, max_comments=1)
        source = "operations-candidates"
        if candidates is None:
            result = await self.agent.search("最近无人回复或值得继续讨论的问题", limit=10)
            candidates = result.unique_posts()
            source = "engagement-search"
        drafts: list[Draft] = []
        seen: set[str] = set()
        for candidate in candidates:
            if not candidate.post_id or not candidate.title or candidate.post_id in seen:
                continue
            seen.add(candidate.post_id)
            drafts.append(Draft(
                title="",
                content=f"谢谢你分享《{candidate.title}》。结合你提到的背景，这里最值得继续展开的是：当时有哪些限制或证据影响了你的选择？补充这些信息，会让后来读到的人更容易判断这段经验是否适合自己。",
                tags=[], metadata={
                    "targetPostId": candidate.post_id,
                    "source": source,
                    "candidateReason": getattr(candidate, "reason", ""),
                    "confidence": "low",
                    "risk": "requires-human-approval",
                },
            ))
        return drafts
