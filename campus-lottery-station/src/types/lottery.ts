export type FormState = {
  topicUrl: string;
  winnerCount: number;
  maxFloor: number | null;
  excludeAuthor: boolean;
  dedupeUser: boolean;
  replyOnly: boolean;
};

export type CurrentUser = {
  id: string;
  username: string;
  displayName: string;
  avatar?: string;
  level: number;
  points: number;
  role: "user" | "moderator" | "admin" | string;
  provider?: string;
  lastLoginAt?: string;
};

export type PublicConfig = {
  communityBaseUrl: string;
  logoUrl?: string;
  ssoEnabled: boolean;
  ssoClientId: string;
  ssoStartUrl: string;
};

export type PreviewPayload = {
  topicUrl: string;
  maxFloor: number | null;
  excludeAuthor: boolean;
  dedupeUser: boolean;
};

export type SyncCommentsPayload = PreviewPayload & {
  replyOnly: boolean;
};

export type DrawPayload = PreviewPayload & {
  winnerCount: number;
};

export type Participant = {
  id: string;
  username: string;
  displayName: string;
  avatar?: string;
  floor: number;
  repliedAt: string;
  excerpt?: string;
};

export type TopicPreviewData = {
  topicTitle: string;
  author: string;
  replyCount: number;
  participantCount: number;
  lastFloor: number;
  syncedAt?: string;
  commentSource?: string;
  participants: Participant[];
};

export type Winner = Participant & {
  rank: number;
};

export type DrawResult = {
  drawId: string;
  seed: string;
  algorithm?: string;
  participantHash?: string;
  proof?: string;
  participantCount: number;
  winners: Winner[];
};

export type BotAccount = {
  id: string;
  username: string;
  displayName: string;
  avatar: string;
  status: "not_configured" | "pending" | "ready";
};

export type PublishResultPayload = {
  topicUrl: string;
  drawId: string;
  seed: string;
  algorithm?: string;
  participantHash?: string;
  proof?: string;
  participantCount: number;
  winners: Winner[];
  botAccountId: string;
};

export type PublishedComment = {
  commentId: string;
  commentUrl: string;
  commentFloor: number;
  botName: string;
  postedAt: string;
};

export type HistoryRecord = {
  id: string;
  topicTitle: string;
  drawnAt: string;
  participantCount: number;
  winnerCount: number;
};

export type BootstrapData = {
  user: CurrentUser | null;
  config: PublicConfig;
};
