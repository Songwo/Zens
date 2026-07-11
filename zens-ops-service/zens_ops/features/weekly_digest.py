from zens_ops.clients.http import AgentClient
from zens_ops.config import Settings
from zens_ops.models import Draft


class WeeklyDigest:
    def __init__(self, agent: AgentClient, settings: Settings):
        self.agent = agent
        self.settings = settings

    async def build(self) -> Draft:
        result = await self.agent.search("本周最有价值、讨论充分且值得回看的帖子", limit=10)
        lines = ["# Zens 本周精选", "", "这一期收录值得继续阅读和讨论的内容。精选关注内容本身，而不是单纯按热度排序。", ""]
        for item in result.citations[:8]:
            title = f"[{item.title}]({item.url})" if item.url else item.title
            excerpt = (item.excerpt or "这篇内容提供了值得继续展开的经验或观点。").strip()[:240]
            lines.extend((f"## {title}", "", excerpt, "", "推荐理由：它保留了具体背景和讨论空间，适合继续补充不同经历。", ""))
        lines.extend(("## 下周想聊什么？", "", "欢迎在评论里推荐主题，也欢迎分享你希望被更多人看到的长期内容。"))
        return Draft(title="Zens 本周精选", content="\n".join(lines), section_id=self.settings.default_section_id, tags=["每周精选"], metadata={"sourcePostIds": [x.post_id for x in result.citations]})
