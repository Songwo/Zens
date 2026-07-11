from datetime import datetime

from zens_ops.clients.http import AgentClient
from zens_ops.config import Settings
from zens_ops.models import TopicPlan


class TopicPlanner:
    def __init__(self, agent: AgentClient, settings: Settings):
        self.agent = agent
        self.settings = settings

    async def plan(self) -> TopicPlan:
        result = await self.agent.search("最近值得长期讨论的经验、作品、问题和观点", limit=12)
        sources = result.unique_posts()
        source = sources[0] if sources else None
        topic = source.title if source else "如何把一次真实经历整理成对他人有帮助的长期内容"
        return TopicPlan(
            topic=topic,
            title=f"从一次讨论出发：{topic}",
            brief="提炼背景、过程、取舍、结果与可复用经验，避免复述原帖。",
            tags=["长期表达", "经验分享"],
            section_id=self.settings.default_section_id,
            metadata={"researchHitCount": result.hit_count, "sourcePostIds": [item.post_id for item in sources[:5]]},
        )
