import type {
  BotAccount,
  BootstrapData,
  CurrentUser,
  DrawPayload,
  DrawResult,
  Participant,
  PublishedComment,
  PublishResultPayload,
  PreviewPayload,
  SyncCommentsPayload,
  TopicPreviewData,
} from "../types/lottery";

const MOCK_ENABLED = import.meta.env.VITE_USE_MOCK_API === "true";

let mockBotAccount: BotAccount = {
  id: "zens-lottery-bot",
  username: "zens-lottery-bot",
  displayName: "Zens 抽奖机器人",
  avatar: "抽",
  status: "ready",
};

const baseParticipants: Participant[] = [
  {
    id: "u-1001",
    username: "chen-ke",
    displayName: "陈可",
    avatar: "陈",
    floor: 2,
    repliedAt: "2026-05-29T09:12:00+08:00",
    excerpt: "报名参加，最近正好在整理社区工具链。",
  },
  {
    id: "u-1002",
    username: "lin-dev",
    displayName: "林序",
    avatar: "林",
    floor: 4,
    repliedAt: "2026-05-29T09:28:00+08:00",
    excerpt: "支持透明抽奖，参与一下。",
  },
  {
    id: "u-1003",
    username: "miao-ui",
    displayName: "苗雨",
    avatar: "苗",
    floor: 7,
    repliedAt: "2026-05-29T10:03:00+08:00",
    excerpt: "报名，感谢社区组织。",
  },
  {
    id: "u-1004",
    username: "song-api",
    displayName: "宋岩",
    avatar: "宋",
    floor: 9,
    repliedAt: "2026-05-29T10:36:00+08:00",
    excerpt: "已阅读规则，按回复参与。",
  },
  {
    id: "u-1005",
    username: "wei-build",
    displayName: "魏晨",
    avatar: "魏",
    floor: 11,
    repliedAt: "2026-05-29T11:10:00+08:00",
    excerpt: "报名，希望后续也能看到复验摘要。",
  },
  {
    id: "u-1006",
    username: "han-rs",
    displayName: "韩知",
    avatar: "韩",
    floor: 14,
    repliedAt: "2026-05-29T11:42:00+08:00",
    excerpt: "参与一次，社区规则很清楚。",
  },
  {
    id: "u-1007",
    username: "zhou-data",
    displayName: "周衡",
    avatar: "周",
    floor: 17,
    repliedAt: "2026-05-29T12:08:00+08:00",
    excerpt: "报名，感谢维护。",
  },
  {
    id: "u-1008",
    username: "qiao-front",
    displayName: "乔安",
    avatar: "乔",
    floor: 19,
    repliedAt: "2026-05-29T12:33:00+08:00",
    excerpt: "按楼层参与，已确认不重复。",
  },
  {
    id: "u-1009",
    username: "xu-kernel",
    displayName: "许栈",
    avatar: "许",
    floor: 22,
    repliedAt: "2026-05-29T13:18:00+08:00",
    excerpt: "报名，期待结果公示。",
  },
  {
    id: "u-1010",
    username: "lu-docs",
    displayName: "陆青",
    avatar: "陆",
    floor: 26,
    repliedAt: "2026-05-29T14:02:00+08:00",
    excerpt: "参与，辛苦社区运营。",
  },
  {
    id: "u-1011",
    username: "tang-devops",
    displayName: "唐一",
    avatar: "唐",
    floor: 31,
    repliedAt: "2026-05-29T15:21:00+08:00",
    excerpt: "报名，规则透明就很好。",
  },
  {
    id: "u-1012",
    username: "he-cache",
    displayName: "何川",
    avatar: "何",
    floor: 35,
    repliedAt: "2026-05-29T16:15:00+08:00",
    excerpt: "参与，支持社区工具化。",
  },
];

export async function previewLottery(payload: PreviewPayload): Promise<TopicPreviewData> {
  if (MOCK_ENABLED) {
    return mockPreview(payload);
  }
  return postJson<TopicPreviewData>("/api/lottery/preview", payload);
}

export async function syncTopicComments(payload: SyncCommentsPayload): Promise<TopicPreviewData> {
  if (MOCK_ENABLED) {
    return mockSyncComments(payload);
  }
  return postJson<TopicPreviewData>("/api/lottery/comments/sync", payload);
}

export async function drawLottery(payload: DrawPayload): Promise<DrawResult> {
  if (MOCK_ENABLED) {
    return mockDraw(payload);
  }
  return postJson<DrawResult>("/api/lottery/draw", payload);
}

export async function fetchBotAccount(): Promise<BotAccount> {
  if (MOCK_ENABLED) {
    await wait(260);
    return mockBotAccount;
  }
  return getJson<BotAccount>("/api/lottery/bot-account");
}

export async function applyBotAccount(): Promise<BotAccount> {
  if (MOCK_ENABLED) {
    await wait(680);
    mockBotAccount = {
      ...mockBotAccount,
      status: "ready",
    };
    return mockBotAccount;
  }
  return postJson<BotAccount>("/api/lottery/bot-account/apply", {});
}

export async function publishResultComment(payload: PublishResultPayload): Promise<PublishedComment> {
  if (MOCK_ENABLED) {
    await wait(860);
    return {
      commentId: `comment-${Date.now().toString(36)}`,
      commentUrl: `${payload.topicUrl.replace(/\/$/, "")}#comment-${payload.drawId}`,
      commentFloor: 43,
      botName: mockBotAccount.displayName,
      postedAt: new Date().toISOString(),
    };
  }
  return postJson<PublishedComment>("/api/lottery/results/publish", payload);
}

export async function fetchMe(): Promise<CurrentUser | null> {
  if (MOCK_ENABLED) {
    await wait(180);
    return null;
  }
  return getJson<CurrentUser | null>("/api/me");
}

export async function fetchBootstrap(): Promise<BootstrapData> {
  if (MOCK_ENABLED) {
    await wait(180);
    return {
      user: null,
      config: {
        communityBaseUrl: "http://localhost:5173",
        logoUrl: "/logo.png",
        ssoEnabled: true,
        ssoClientId: "campus-lottery-station",
        ssoStartUrl: "/api/auth/sso/start",
      },
    };
  }
  return getJson<BootstrapData>("/api/bootstrap");
}

export async function logout(): Promise<void> {
  if (MOCK_ENABLED) {
    await wait(160);
    return;
  }
  await postJson<void>("/api/auth/logout", {});
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.ok === false) {
    throwApiError(response.status, body.error || `请求失败：${response.status}`);
  }
  return (body.data ?? body) as T;
}

async function postJson<T>(url: string, payload: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.ok === false) {
    throwApiError(response.status, body.error || `请求失败：${response.status}`);
  }
  return (body.data ?? body) as T;
}

function throwApiError(status: number, message: string): never {
  const error = new Error(message) as Error & { status?: number };
  error.status = status;
  throw error;
}

async function mockPreview(payload: PreviewPayload): Promise<TopicPreviewData> {
  await wait(420);
  const participants = filterParticipants(payload);
  const topicId = extractTopicId(payload.topicUrl);
  const lastParticipant = participants[participants.length - 1];
  return {
    topicTitle: `Zens 社区帖子 #${topicId}：开发者工具抽奖登记`,
    author: "Zens 运营组",
    replyCount: 42,
    participantCount: participants.length,
    lastFloor: lastParticipant?.floor ?? 1,
    participants,
  };
}

async function mockSyncComments(payload: SyncCommentsPayload): Promise<TopicPreviewData> {
  await wait(980);
  const preview = await mockPreview(payload);
  return {
    ...preview,
    syncedAt: new Date().toISOString(),
    commentSource: payload.replyOnly ? "帖子回复用户" : "帖子互动用户",
  };
}

async function mockDraw(payload: DrawPayload): Promise<DrawResult> {
  await wait(760);
  const preview = await mockPreview(payload);
  const seed = buildSeed(payload.topicUrl);
  const winners = seededShuffle(preview.participants, seed)
    .slice(0, payload.winnerCount)
    .map((participant, index) => ({
      ...participant,
      rank: index + 1,
    }));
  return {
    drawId: `zens-draw-${Date.now().toString(36)}`,
    seed,
    participantCount: preview.participantCount,
    winners,
  };
}

function filterParticipants(payload: PreviewPayload): Participant[] {
  const maxFloor = payload.maxFloor ?? Number.POSITIVE_INFINITY;
  return baseParticipants.filter((participant) => participant.floor <= maxFloor);
}

function extractTopicId(url: string): string {
  const match = url.match(/(?:topic|post|threads?|t)\/?(\d+)/i) || url.match(/(\d{3,})/);
  return match?.[1] ?? "1428";
}

function buildSeed(value: string): string {
  let hash = 2166136261;
  for (const char of value) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return `zens-${(hash >>> 0).toString(16)}-${new Date().toISOString().slice(0, 10)}`;
}

function seededShuffle<T>(items: T[], seed: string): T[] {
  const output = [...items];
  let state = 0;
  for (const char of seed) {
    state = (state * 31 + char.charCodeAt(0)) >>> 0;
  }
  for (let index = output.length - 1; index > 0; index -= 1) {
    state = (1664525 * state + 1013904223) >>> 0;
    const swapIndex = state % (index + 1);
    [output[index], output[swapIndex]] = [output[swapIndex], output[index]];
  }
  return output;
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}
