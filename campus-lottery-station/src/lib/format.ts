import type { DrawResult, TopicPreviewData } from "../types/lottery";

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function formatFullDateTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function buildPublicText(topic: TopicPreviewData | null, result: DrawResult) {
  const title = topic?.topicTitle ?? "Zens 社区抽奖帖";
  const winnerNames = result.winners
    .map(
      (winner) =>
        `${winner.rank}. ${winner.displayName}（@${winner.username}，${winner.floor} 楼，回复时间 ${formatFullDateTime(winner.repliedAt)}）`,
    )
    .join("\n");

  return [
    `本次抽奖基于帖子回复数据生成，共 ${result.participantCount} 名有效参与者，抽取 ${result.winners.length} 名中奖者。`,
    `帖子：${title}`,
    result.algorithm ? `随机算法：${result.algorithm}` : "",
    `随机种子：${result.seed}`,
    result.participantHash ? `名单快照哈希：${result.participantHash}` : "",
    result.proof ? `结果证明哈希：${result.proof}` : "",
    "规则：排除发帖人、同一用户只计入一次，并按设置的截止楼层统计。",
    "",
    "中奖名单：",
    winnerNames,
    "",
    "本结果由 Zens 抽奖工具生成，已排除发帖人并按用户去重，可用于原帖公示。",
  ]
    .filter(Boolean)
    .join("\n");
}
