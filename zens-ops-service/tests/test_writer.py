import pytest

from zens_ops.config import Settings
from zens_ops.features.writer import Writer
from zens_ops.models import TopicPlan


@pytest.mark.asyncio
async def test_deterministic_fallback_is_markdown_and_stable():
    writer = Writer(Settings(_env_file=None))
    plan = TopicPlan(topic="一次真实复盘", title="把经历写完整", brief="test")
    first = await writer.write(plan)
    second = await writer.write(plan)
    assert first.content == second.content
    assert "## 一个可执行的复盘清单" in first.content
    assert first.metadata["writer"] == "deterministic-template"
