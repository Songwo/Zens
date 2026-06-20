import { useEffect, useMemo, useState } from "react";
import { Header } from "./components/Header";
import { HistoryList } from "./components/HistoryList";
import { LotteryForm } from "./components/LotteryForm";
import { LotteryResult } from "./components/LotteryResult";
import { ParticipantPreview } from "./components/ParticipantPreview";
import { RulePanel } from "./components/RulePanel";
import { TopicPreview } from "./components/TopicPreview";
import {
  drawLottery,
  fetchBootstrap,
  fetchBotAccount,
  logout,
  publishResultComment,
  syncTopicComments,
} from "./lib/api";
import { buildPublicText } from "./lib/format";
import type {
  BotAccount,
  CurrentUser,
  DrawResult,
  FormState,
  HistoryRecord,
  PublishedComment,
  TopicPreviewData,
} from "./types/lottery";

const initialForm: FormState = {
  topicUrl: "",
  winnerCount: 3,
  maxFloor: null,
  excludeAuthor: true,
  dedupeUser: true,
  replyOnly: true,
};

const initialHistory: HistoryRecord[] = [
  {
    id: "history-1",
    topicTitle: "Zens 社区帖子 #1382：五月开源共读抽奖",
    drawnAt: "2026-05-25T20:30:00+08:00",
    participantCount: 86,
    winnerCount: 5,
  },
  {
    id: "history-2",
    topicTitle: "Zens 社区帖子 #1329：开发者周边申请帖",
    drawnAt: "2026-05-18T19:10:00+08:00",
    participantCount: 64,
    winnerCount: 3,
  },
];

type PersistedWorkspace = {
  form?: Partial<FormState>;
  topic?: TopicPreviewData | null;
  result?: DrawResult | null;
  publishedComment?: PublishedComment | null;
  history?: HistoryRecord[];
  participantsVisible?: boolean;
  updatedAt?: string;
};

const WORKSPACE_KEY = "zens-lottery:workspace:v1";
const MAX_HISTORY_RECORDS = 20;

function loadWorkspace(): PersistedWorkspace {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(WORKSPACE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function saveWorkspace(workspace: PersistedWorkspace) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      WORKSPACE_KEY,
      JSON.stringify({
        ...workspace,
        history: workspace.history?.slice(0, MAX_HISTORY_RECORDS),
        updatedAt: new Date().toISOString(),
      }),
    );
  } catch {
    // localStorage may be unavailable in privacy mode; the app can still run.
  }
}

function clearWorkspace() {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(WORKSPACE_KEY);
  } catch {
    // Ignore storage failures during logout.
  }
}

function restoreForm(form?: Partial<FormState>): FormState {
  const winnerCount =
    typeof form?.winnerCount === "number" && Number.isFinite(form.winnerCount)
      ? Math.max(1, Math.min(50, Math.floor(form.winnerCount)))
      : initialForm.winnerCount;
  const maxFloor =
    typeof form?.maxFloor === "number" && Number.isFinite(form.maxFloor) && form.maxFloor > 0
      ? Math.floor(form.maxFloor)
      : null;

  return {
    topicUrl: typeof form?.topicUrl === "string" ? form.topicUrl : initialForm.topicUrl,
    winnerCount,
    maxFloor,
    excludeAuthor: typeof form?.excludeAuthor === "boolean" ? form.excludeAuthor : initialForm.excludeAuthor,
    dedupeUser: typeof form?.dedupeUser === "boolean" ? form.dedupeUser : initialForm.dedupeUser,
    replyOnly: typeof form?.replyOnly === "boolean" ? form.replyOnly : initialForm.replyOnly,
  };
}

const defaultBotAccount: BotAccount = {
  id: "zens-lottery-bot",
  username: "zens-lottery-bot",
  displayName: "Zens 抽奖机器人",
  avatar: "抽",
  status: "ready",
};

const ssoStatusMessage: Record<string, { type: "success" | "error"; text: string }> = {
  ok: { type: "success", text: "社区账号已连接，可以开始同步评论和开奖。" },
  "invalid-token": {
    type: "error",
    text: "主站授权令牌验证失败，请确认抽奖站的 COMMUNITY_JWT_SECRET 与主站 JWT_SECRET 完全一致，然后重新登录。",
  },
  "missing-code": { type: "error", text: "主站授权回调缺少登录凭证，请重新连接社区账号。" },
  "bad-state": { type: "error", text: "主站授权状态校验失败，请重新连接社区账号。" },
  "exchange-failed": { type: "error", text: "主站授权兑换失败，请检查 SSO 配置后重新登录。" },
  access_denied: { type: "error", text: "已取消社区账号授权。" },
};

export default function App() {
  const [workspace] = useState<PersistedWorkspace>(() => loadWorkspace());
  const [form, setForm] = useState<FormState>(() => restoreForm(workspace.form));
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [communityBaseUrl, setCommunityBaseUrl] = useState("");
  const [logoUrl, setLogoUrl] = useState("/logo.png");
  const [ssoStartUrl, setSsoStartUrl] = useState("/api/auth/sso/start");
  const [topic, setTopic] = useState<TopicPreviewData | null>(() => workspace.topic ?? null);
  const [result, setResult] = useState<DrawResult | null>(() => workspace.result ?? null);
  const [botAccount, setBotAccount] = useState<BotAccount | null>(defaultBotAccount);
  const [publishedComment, setPublishedComment] = useState<PublishedComment | null>(
    () => workspace.publishedComment ?? null,
  );
  const [history, setHistory] = useState<HistoryRecord[]>(
    () => (workspace.history?.length ? workspace.history.slice(0, MAX_HISTORY_RECORDS) : initialHistory),
  );
  const [participantsVisible, setParticipantsVisible] = useState(() =>
    Boolean(workspace.participantsVisible && workspace.topic),
  );
  const [previewing, setPreviewing] = useState(false);
  const [autoPreviewing, setAutoPreviewing] = useState(false);
  const [syncingComments, setSyncingComments] = useState(false);
  const [drawing, setDrawing] = useState(false);
  const [publishingComment, setPublishingComment] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const isLoggedIn = Boolean(user);

  const previewPayload = useMemo(
    () => ({
      topicUrl: form.topicUrl.trim(),
      maxFloor: form.maxFloor,
      excludeAuthor: form.excludeAuthor,
      dedupeUser: form.dedupeUser,
    }),
    [form.dedupeUser, form.excludeAuthor, form.maxFloor, form.topicUrl],
  );

  useEffect(() => {
    let cancelled = false;
    fetchBootstrap()
      .then((data) => {
        if (cancelled) return;
        setUser(data.user);
        setCommunityBaseUrl(data.config?.communityBaseUrl || "");
        setLogoUrl(data.config?.logoUrl || "/logo.png");
        setSsoStartUrl(data.config?.ssoStartUrl || "/api/auth/sso/start");
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "初始化抽奖站失败");
        }
      });
    fetchBotAccount()
      .then((account) => {
        if (!cancelled) {
          setBotAccount(account);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBotAccount(defaultBotAccount);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const ssoStatus = params.get("sso");
    if (!ssoStatus) return;

    const status = ssoStatusMessage[ssoStatus] || {
      type: "error" as const,
      text: `社区账号授权失败：${ssoStatus}`,
    };
    if (status.type === "success") {
      setMessage(status.text);
      setError("");
    } else {
      setError(status.text);
      setMessage("");
    }

    params.delete("sso");
    const nextQuery = params.toString();
    const nextUrl = `${window.location.pathname}${nextQuery ? `?${nextQuery}` : ""}${window.location.hash}`;
    window.history.replaceState({}, "", nextUrl);
  }, []);

  useEffect(() => {
    saveWorkspace({
      form,
      topic,
      result,
      publishedComment,
      history,
      participantsVisible: participantsVisible && Boolean(topic),
    });
  }, [form, history, participantsVisible, publishedComment, result, topic]);

  useEffect(() => {
    if (!isLoggedIn) {
      return;
    }

    if (form.topicUrl.trim().length < 12) {
      setTopic(null);
      setParticipantsVisible(false);
      setResult(null);
      setPublishedComment(null);
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setAutoPreviewing(true);
      setSyncingComments(true);
      try {
        const data = await syncTopicComments({
          ...previewPayload,
          replyOnly: form.replyOnly,
        });
        if (!cancelled) {
          setTopic(data);
          setParticipantsVisible(true);
          setError("");
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "帖子评论同步失败");
        }
      } finally {
        if (!cancelled) {
          setAutoPreviewing(false);
          setSyncingComments(false);
        }
      }
    }, 520);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [form.replyOnly, form.topicUrl, isLoggedIn, previewPayload]);

  function updateForm(patch: Partial<FormState>) {
    setForm((current) => ({ ...current, ...patch }));
    setResult(null);
    setPublishedComment(null);
    setMessage("");
    setError("");
  }

  async function handlePreview() {
    if (!requireLogin()) return;
    if (!form.topicUrl.trim()) {
      setError("请先粘贴 Zens 社区帖子链接。");
      return;
    }
    setPreviewing(true);
    setSyncingComments(true);
    setError("");
    try {
      const data = await syncTopicComments({
        ...previewPayload,
        replyOnly: form.replyOnly,
      });
      setTopic(data);
      setParticipantsVisible(true);
      setMessage("已同步原帖评论并生成参与名单，可在开奖前再次核对。");
    } catch (err) {
      setError(err instanceof Error ? err.message : "帖子评论同步失败");
    } finally {
      setPreviewing(false);
      setSyncingComments(false);
    }
  }

  async function handleSyncComments() {
    if (!requireLogin()) return;
    if (!form.topicUrl.trim()) {
      setError("请先粘贴 Zens 社区帖子链接。");
      return;
    }
    setSyncingComments(true);
    setError("");
    setMessage("");
    try {
      const data = await syncTopicComments({
        ...previewPayload,
        replyOnly: form.replyOnly,
      });
      setTopic(data);
      setParticipantsVisible(true);
      setMessage(`已从原帖同步 ${data.participantCount} 名有效参与者。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "同步帖子评论失败");
    } finally {
      setSyncingComments(false);
    }
  }

  async function handleDraw() {
    if (!requireLogin()) return;
    if (!form.topicUrl.trim()) {
      setError("请先粘贴 Zens 社区帖子链接。");
      return;
    }
    setDrawing(true);
    setError("");
    setMessage("");
    setPublishedComment(null);
    try {
      const activeTopic =
        topic ??
        (await syncTopicComments({
          ...previewPayload,
          replyOnly: form.replyOnly,
        }));
      setTopic(activeTopic);
      const draw = await drawLottery({
        ...previewPayload,
        winnerCount: form.winnerCount,
      });
      setResult(draw);
      setParticipantsVisible(true);
      setHistory((current) => [
        {
          id: draw.drawId,
          topicTitle: activeTopic.topicTitle,
          drawnAt: new Date().toISOString(),
          participantCount: draw.participantCount,
          winnerCount: draw.winners.length,
        },
        ...current,
      ]);
      setMessage("抽奖完成，结果摘要可以复制到原帖公示。");
    } catch (err) {
      setError(err instanceof Error ? err.message : "抽奖失败，请稍后重试");
    } finally {
      setDrawing(false);
    }
  }

  async function handlePublishResult() {
    if (!result) return;
    if (!requireLogin()) return;
    if (user && !["admin", "moderator", "super_admin"].includes(user.role)) {
      setError("只有管理员或版主可以把中奖名单发布到原帖。");
      return;
    }
    const activeBotAccount = botAccount ?? defaultBotAccount;
    setPublishingComment(true);
    setError("");
    setMessage("");
    try {
      const comment = await publishResultComment({
        topicUrl: form.topicUrl.trim(),
        drawId: result.drawId,
        seed: result.seed,
        algorithm: result.algorithm,
        participantHash: result.participantHash,
        proof: result.proof,
        participantCount: result.participantCount,
        winners: result.winners,
        botAccountId: activeBotAccount.id,
      });
      setPublishedComment(comment);
      setMessage(`中奖名单已由 ${comment.botName} 发布到原帖 ${comment.commentFloor} 楼。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "发布中奖名单评论失败");
    } finally {
      setPublishingComment(false);
    }
  }

  async function copyPublicText() {
    if (!result) return;
    const text = buildPublicText(topic, result);
    try {
      await navigator.clipboard.writeText(text);
      setMessage("公示文本已复制。");
    } catch {
      setMessage("浏览器未允许自动复制，请使用导出结果获取文本。");
    }
  }

  function exportResult() {
    if (!result) return;
    const text = buildPublicText(topic, result);
    const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${result.drawId}.txt`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function requireLogin() {
    if (user) return true;
    setError("请先登录社区账号，再同步评论、预览名单或开奖。");
    return false;
  }

  async function handleLogout() {
    try {
      await logout();
      clearWorkspace();
      setUser(null);
      setForm(initialForm);
      setTopic(null);
      setResult(null);
      setHistory(initialHistory);
      setParticipantsVisible(false);
      setPublishedComment(null);
      setMessage("已退出抽奖站。");
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "退出失败，请稍后重试");
    }
  }

  return (
    <div className="min-h-[100dvh] bg-canvas text-ink">
      <Header
        user={user}
        communityBaseUrl={communityBaseUrl}
        logoUrl={logoUrl}
        ssoStartUrl={ssoStartUrl}
        onLogout={handleLogout}
      />

      <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8 lg:py-12">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_340px]">
          <section className="min-w-0 space-y-8">
            <LotteryForm
              form={form}
              onChange={updateForm}
              onPreview={handlePreview}
              onSyncComments={handleSyncComments}
              onDraw={handleDraw}
              previewing={previewing}
              syncing={syncingComments}
              drawing={drawing}
              user={user}
              ssoStartUrl={ssoStartUrl}
            />

            {(message || error) && (
              <div
                className={`section-enter rounded-lg border px-4 py-3 text-sm ${
                  error
                    ? "border-red-200 bg-red-50 text-red-700"
                    : "border-amber/30 bg-cream text-amber-ink"
                }`}
                role="status"
              >
                {error || message}
              </div>
            )}

            <TopicPreview topic={topic} loading={autoPreviewing && !topic} />
            <ParticipantPreview
              participants={topic?.participants ?? []}
              visible={participantsVisible}
              loading={syncingComments}
              syncedAt={topic?.syncedAt}
              source={topic?.commentSource}
            />
            <LotteryResult
              result={result}
              botAccount={botAccount}
              publishedComment={publishedComment}
              onCopy={copyPublicText}
              onRedraw={handleDraw}
              onExport={exportResult}
              onPublish={handlePublishResult}
              drawing={drawing}
              publishing={publishingComment}
            />
          </section>

          <RulePanel
            botAccount={botAccount}
            publishedComment={publishedComment}
          />
        </div>

        <HistoryList records={history} />
      </main>
    </div>
  );
}
