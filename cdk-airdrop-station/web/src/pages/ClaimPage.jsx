import { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { useParams } from "react-router-dom";
import { claimApi, isLoggedIn, publicApi, userApi, clearAuthToken } from "../lib/api";
import { DEFAULT_BRAND, normalizeBrand, setAppTitle } from "../lib/brand";
import { getFingerprint } from "../lib/storage";
import ParticleBurst from "../components/ParticleBurst";
import HCaptchaBox from "../components/HCaptchaBox";

const CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$*&!";

function formatTime(iso) {
  if (!iso) return "--";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function formatNumber(value) {
  return new Intl.NumberFormat("zh-CN").format(Number(value || 0));
}

function getUserDisplay(user) {
  if (!user) return "";
  return user.nickname || user.username || user.email || "社区成员";
}

function splitRules(text) {
  return String(text || "")
    .split(/\n|。|；|;/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 5);
}

function listItems(res) {
  return res?.items || res || [];
}

/* 内联 CDK 解密动画组件 */
function InlineCDKReveal({ code, claimedAt }) {
  const [chars, setChars] = useState([]);
  const [done, setDone] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!code) return;
    const len = code.length;
    setChars(Array.from({ length: len }, () => ({ c: CHARS[Math.floor(Math.random() * CHARS.length)], ok: false })));
    let idx = 0;
    const t = setInterval(() => {
      setChars(prev => {
        const next = [...prev];
        for (let i = idx; i < len; i++) {
          next[i] = { c: /[\s/:.\-]/.test(code[i]) ? code[i] : CHARS[Math.floor(Math.random() * CHARS.length)], ok: false };
        }
        if (idx < len) next[idx] = { c: code[idx], ok: true };
        return next;
      });
      idx++;
      if (idx > len) { clearInterval(t); setDone(true); }
    }, 40);
    return () => clearInterval(t);
  }, [code]);

  async function copy() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="cdk-reveal">
      <div className="cdk-reveal__header">
        <span>兑换信息</span>
        <button onClick={copy} disabled={!code}>{copied ? "✓ 已复制" : "复制内容"}</button>
      </div>
      <div className={`cdk-reveal__code ${done ? "cdk-reveal__code--done" : ""}`}>
        {!code && <span className="cdk-reveal__waiting">&gt;&gt; 等待获取兑换链接 / 口令...</span>}
        {code && chars.map((item, i) => (
          <span key={i} className={item.ok ? "cdk-char--resolved" : "cdk-char--scramble"}>{item.c}</span>
        ))}
      </div>
      {claimedAt && <p className="cdk-reveal__hint">锁定时间 {new Date(claimedAt).toLocaleString("zh-CN")}</p>}
    </div>
  );
}

/* 当前登录账号徽章 */
function CurrentUserBadge({ user, loggedIn }) {
  if (!loggedIn) {
    return (
      <a
        href={`/login?returnUrl=${encodeURIComponent(window.location.pathname)}`}
        className="claim-userbadge claim-userbadge--guest"
        title="点击前往登录"
      >
        <span className="claim-userbadge__avatar claim-userbadge__avatar--guest">?</span>
        <div className="claim-userbadge__meta">
          <span className="claim-userbadge__label">未登录</span>
          <span className="claim-userbadge__hint">点击使用社区账号登录</span>
        </div>
      </a>
    );
  }
  if (!user) {
    return (
      <div className="claim-userbadge claim-userbadge--loading">
        <span className="claim-userbadge__avatar claim-userbadge__avatar--guest">…</span>
        <div className="claim-userbadge__meta">
          <span className="claim-userbadge__label">正在校验身份…</span>
        </div>
      </div>
    );
  }
  const display = user.nickname || user.username || user.email || "已登录用户";
  const sub = user.username && user.username !== display ? `@${user.username}` : (user.email || (user.role === 'admin' ? '管理员' : '社区账号'));
  const initial = (display || "?").trim().charAt(0).toUpperCase();
  return (
    <div className="claim-userbadge" title={`已登录：${display}`}>
      {user.avatar ? (
        <img src={user.avatar} alt={display} className="claim-userbadge__avatar" />
      ) : (
        <span className="claim-userbadge__avatar">{initial}</span>
      )}
      <div className="claim-userbadge__meta">
        <span className="claim-userbadge__label">{display}</span>
        <span className="claim-userbadge__hint">{sub}</span>
      </div>
    </div>
  );
}

/* 倒计时组件 */
function Countdown({ targetTime, onComplete }) {
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    const end = new Date(targetTime).getTime();
    const update = () => {
      const now = Date.now();
      const diff = end - now;
      if (diff <= 0) {
        setTimeLeft(0);
        if (onComplete) onComplete();
      } else {
        setTimeLeft(diff);
      }
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [targetTime, onComplete]);

  if (timeLeft <= 0) return <span style={{color: 'var(--cp-brand)', fontWeight: 800}}>通道已开启</span>;

  const d = Math.floor(timeLeft / (1000 * 60 * 60 * 24));
  const h = Math.floor((timeLeft / (1000 * 60 * 60)) % 24);
  const m = Math.floor((timeLeft / 1000 / 60) % 60);
  const s = Math.floor((timeLeft / 1000) % 60);

  const pad = (n) => String(n).padStart(2, '0');

  return (
    <div className="countdown-box">
      <div className="countdown-item"><span className="val">{d}</span><span className="lbl">天</span></div>
      <div className="countdown-sep">:</div>
      <div className="countdown-item"><span className="val">{pad(h)}</span><span className="lbl">时</span></div>
      <div className="countdown-sep">:</div>
      <div className="countdown-item"><span className="val">{pad(m)}</span><span className="lbl">分</span></div>
      <div className="countdown-sep">:</div>
      <div className="countdown-item"><span className="val">{pad(s)}</span><span className="lbl">秒</span></div>
    </div>
  );
}

export default function ClaimPage() {
  const { projectCode } = useParams();
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [claiming, setClaiming] = useState(false);
  const [claimResult, setClaimResult] = useState(null);
  const [burstTrigger, setBurstTrigger] = useState("");
  const [hcaptchaToken, setHcaptchaToken] = useState("");
  const [toast, setToast] = useState("");
  const [brand, setBrand] = useState(DEFAULT_BRAND);
  const [currentUser, setCurrentUser] = useState(null);
  const [myClaims, setMyClaims] = useState([]);
  const hcaptchaRef = useRef(null);
  const fingerprint = getFingerprint();
  const userScope = currentUser?.userId || currentUser?.id || "guest";
  const storageKey = useMemo(() => `claim-result:${userScope}:${projectCode}`, [userScope, projectCode]);

  useEffect(() => {
    setAppTitle(DEFAULT_BRAND);
    publicApi.brand().then((d) => {
      if (d?.systemName) {
        const next = normalizeBrand(d);
        setBrand(next);
        setAppTitle(next);
      }
    }).catch(() => {});
  }, []);

  // 拉取当前登录用户信息（用于在领取页展示当前账号）
  useEffect(() => {
    if (!isLoggedIn()) {
      setCurrentUser(null);
      return;
    }
    let cancelled = false;
    userApi.getMe()
      .then((data) => {
        if (cancelled) return;
        if (data?.loggedIn === false) {
          setCurrentUser(null);
          clearAuthToken();
          return;
        }
        setCurrentUser(data?.user || data || null);
      })
      .catch(() => { if (!cancelled) setCurrentUser(null); });
    return () => { cancelled = true; };
  }, []);

  // 切换账号或登出后，清理上一账号遗留在本地的领取缓存（避免串号）
  useEffect(() => {
    Object.keys(localStorage).forEach((key) => {
      if (key.startsWith("claim-result:") && !key.startsWith(`claim-result:${userScope}:`)) {
        // 历史的 `claim-result:<projectCode>` 旧格式也清掉
        const parts = key.split(":");
        if (parts.length === 2 || (parts.length === 3 && parts[1] !== userScope)) {
          localStorage.removeItem(key);
        }
      }
    });
  }, [userScope]);

  const fetchProject = useCallback(async () => {
    try {
      const data = await claimApi.getNode(projectCode, fingerprint).catch(() => claimApi.getProject(projectCode, fingerprint));
      setProject(data);
      // 后端在登录态下会返回 userInfo，作为兜底的当前用户信息源
      if (data?.userInfo && !currentUser) {
        setCurrentUser(data.userInfo);
      }
      // 后端按 userID 严格判定是否已领取；只要 userClaimed=true，刷新页面也只会显示既有兑换码
      if (data.userClaimed) {
        const normalized = {
          rewardContent: data.userRewardContent,
          code: data.userRewardContent,
          claimedAt: data.userClaimedAt,
        };
        setClaimResult(normalized);
        localStorage.setItem(storageKey, JSON.stringify(normalized));
        return;
      }
      // 未领取：清掉本地任何陈旧缓存，避免显示其他账号留下的兑换码
      setClaimResult(null);
      localStorage.removeItem(storageKey);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [projectCode, fingerprint, storageKey, currentUser]);

  useEffect(() => { fetchProject(); }, [fetchProject]);

  useEffect(() => {
    if (!isLoggedIn()) {
      setMyClaims([]);
      return;
    }
    let cancelled = false;
    userApi.claims({ status: "success", pageSize: 4 })
      .then((data) => {
        if (!cancelled) setMyClaims(listItems(data));
      })
      .catch(() => {
        if (!cancelled) setMyClaims([]);
      });
    return () => { cancelled = true; };
  }, [claimResult]);

  async function handleClaim() {
    setClaiming(true);
    setError("");
    try {
      const payload = {
        fingerprint,
        captchaProvider: project?.requireCaptcha ? "hcaptcha" : "",
        hcaptchaToken,
      };
      const result = await claimApi.submitNode(projectCode, payload).catch(() => claimApi.submit(projectCode, { fingerprint }));
      const normalized = normalizeClaimResult(result);
      setClaimResult(normalized);
      localStorage.setItem(storageKey, JSON.stringify(normalized));
      setBurstTrigger(normalized.code || normalized.rewardContent || "success");
      await fetchProject();
    } catch (err) {
      setError(err.message);
      if (["CAPTCHA_INVALID", "HCAPTCHA_INVALID", "CAPTCHA_EXPIRED"].includes(err.code)) {
        setHcaptchaToken("");
        hcaptchaRef.current?.resetCaptcha?.();
      }
    } finally {
      setClaiming(false);
    }
  }

  function getClaimState() {
    if (!project) return { disabled: true, text: "加载中..." };
    if (project.userClaimed || claimResult) return { disabled: true, text: "已领取" };
    if (!project.enabled || project.status === "disabled" || project.status === "paused" || project.status === "archived") return { disabled: true, text: "通道已关闭" };
    if (project.status === "upcoming" || project.status === "draft") return { disabled: true, text: "尚未激活" };
    if (project.status === "ended") return { disabled: true, text: "已过生命周期" };
    if (project.status === "soldout" || project.status === "exhausted" || project.remaining <= 0) return { disabled: true, text: "资源已耗尽" };
    if (!isLoggedIn()) return { disabled: true, text: "需要身份验证" };
    if (project.requireCaptcha && !hcaptchaToken) return { disabled: true, text: "请先完成人机验证" };
    if (claiming) return { disabled: true, text: "正在领取..." };
    return { disabled: false, text: "立即领取福利" };
  }

  async function copyShareLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setToast("领取链接已复制");
    } catch {
      setToast("复制失败，请手动复制浏览器地址");
    }
    setTimeout(() => setToast(""), 1800);
  }

  async function copyReward(value) {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value);
      setToast("兑换信息已复制");
    } catch {
      setToast("复制失败，请手动选择兑换信息");
    }
    setTimeout(() => setToast(""), 1800);
  }

  const claimState = getClaimState();

  /* ── 加载状态 ── */
  if (loading) {
    return (
      <div className="claim-shell">
        <div className="claim-shell__center">
          <div className="claim-loader"><div className="claim-loader__spinner" /></div>
          <p style={{color: 'var(--cp-muted)', marginTop: '16px'}}>正在连接分发节点...</p>
        </div>
      </div>
    );
  }

  /* ── 错误 / 404 ── */
  if (error && !project) {
    return (
      <div className="claim-shell">
        <div className="claim-shell__center">
          <h2 style={{fontSize: '28px', fontWeight: 800, color: 'var(--cp-ink)', marginBottom: '12px'}}>节点不可达</h2>
          <p style={{color: 'var(--cp-muted)'}}>{error}</p>
        </div>
      </div>
    );
  }

  // 严格拦截
  if (project.status === "disabled" || project.status === "paused" || project.status === "archived") {
    return (
      <div className="claim-shell">
        <div className="claim-shell__center">
          <h2 style={{fontSize: '28px', fontWeight: 800, color: 'var(--cp-ink)', marginBottom: '12px'}}>通道已关停</h2>
          <p style={{color: 'var(--cp-muted)'}}>该资源的释放已被管理员中断，无法继续访问。</p>
        </div>
      </div>
    );
  }

  const claimedCount = Number(project.claimedCount || 0);
  const remaining = Number(project.remaining ?? project.remainingCount ?? Math.max(0, Number(project.totalStock || 0) - claimedCount));
  const totalStock = Number(project.totalStock || claimedCount + remaining || 0);
  const progress = totalStock > 0 ? Math.min(100, Math.round((claimedCount / totalStock) * 100)) : 0;
  const rulesList = splitRules(project.rules);
  const displayName = getUserDisplay(currentUser);
  const nodeLabel = project.nodeSlug || project.projectCode || projectCode;
  const timeRange = `${formatTime(project.startTime || project.startAt)} - ${formatTime(project.endTime || project.endAt)}`;
  const showStock = project.showStock !== false;
  const showEndTime = project.showEndTime !== false;
  const statusLabels = {
    active: "正在分发", upcoming: "等待激活", draft: "等待激活", ended: "周期结束", soldout: "额度耗尽", exhausted: "额度耗尽", disabled: "已关停", paused: "已暂停",
  };

  return (
    <div className="claim-shell claim-shell--community">
      <ParticleBurst trigger={burstTrigger} />

      <header className="claim-brand claim-brand--community anim-fade-up">
        <div className="claim-brand__identity">
          <img src="/logo.png" alt="Logo" width="48" height="48" className="claim-brand__logo" style={{ objectFit: 'cover', background: 'transparent', boxShadow: 'none' }} />
          <div>
            <h1>{brand.systemName}</h1>
            <span>社区福利发放中心</span>
          </div>
        </div>
        <CurrentUserBadge user={currentUser} loggedIn={isLoggedIn()} />
      </header>

      <main className="community-claim anim-fade-up stagger-1">
        <section className="community-claim__hero">
          <div className="community-claim__status-row">
            <span className={`status-dot status--${project.status}`}>{statusLabels[project.status] || project.status}</span>
            <span className="claim-time-range">{timeRange}</span>
          </div>

          {(project.status === "upcoming" || project.status === "draft") && project.startTime && (
            <div className="community-countdown">
              <h3>距离开放领取</h3>
              <Countdown targetTime={project.startTime} onComplete={() => fetchProject()} />
            </div>
          )}

          <div className="community-claim__copy">
            <span className="community-claim__eyebrow">Community Benefit</span>
            <h2 className="community-claim__title">{project.name}</h2>
            <p className="community-claim__desc">
              {project.description || "这是一个面向社区成员的限量福利活动。系统会校验社区账号、设备指纹和实时库存，成功领取后兑换信息会绑定到当前账号。"}
            </p>
          </div>

          <div className="community-claim__chips" aria-label="活动关键信息">
            <span>节点 {nodeLabel}</span>
            <span>单账号 {project.perUserLimit || 1} 次</span>
            {project.requireCaptcha && <span>人机验证</span>}
            {project.needLogin !== false && <span>社区账号领取</span>}
          </div>
        </section>

        <section className="community-claim__action-card">
          <div className="claim-action-card__top">
            <div>
              <span className="claim-action-card__label">当前身份</span>
              <strong>{displayName || "未登录社区账号"}</strong>
            </div>
            <span className={`claim-action-card__state ${isLoggedIn() ? "is-ready" : "is-waiting"}`}>
              {isLoggedIn() ? "身份已确认" : "需要登录"}
            </span>
          </div>

          {claimResult ? (
            <div className="claim-result claim-result--community">
              <div className="claim-result__label">领取成功，兑换信息已绑定到当前账号</div>
              <InlineCDKReveal code={claimResult.code || claimResult.rewardContent} claimedAt={claimResult.claimedAt} />
              <div className="claim-result__next">
                <span>后续建议</span>
                <p>妥善保存兑换信息；如福利来自社区活动帖，可返回活动帖完成反馈或晒图。</p>
              </div>
            </div>
          ) : (
            <>
              {project.requireCaptcha && (
                <div className="claim-captcha-wrap">
                  <HCaptchaBox
                    ref={hcaptchaRef}
                    onVerify={setHcaptchaToken}
                    onExpire={() => setHcaptchaToken("")}
                    onError={() => setHcaptchaToken("")}
                  />
                </div>
              )}

              {isLoggedIn() ? (
                <button
                  className={`btn ${claimState.disabled ? "btn--secondary" : "btn--primary"} claim-action__btn`}
                  disabled={claimState.disabled}
                  onClick={handleClaim}
                >
                  {project.buttonText && !claimState.disabled && !claiming ? project.buttonText : claimState.text}
                </button>
              ) : (
                <a href={`/login?returnUrl=${encodeURIComponent(window.location.pathname)}`} className="btn btn--primary claim-action__btn">
                  使用社区账号登录
                </a>
              )}
              {error && <p className="claim-error">{error}</p>}
            </>
          )}

          <div className="claim-action-card__footer">
            <button className="btn btn--secondary" onClick={copyShareLink}>复制领取链接</button>
            <span>刷新页面不会重复发放已绑定福利。</span>
          </div>
        </section>

        <section className="community-claim__insights">
          <div className="community-stat">
            <span>领取进度</span>
            <strong>{progress}%</strong>
            <div className="track-bar">
              <div className="track-bar__fill" style={{ width: `${progress}%` }} />
            </div>
            <p>{showStock ? `${formatNumber(claimedCount)} 已领取 / ${formatNumber(totalStock)} 总库存` : "库存由运营方隐藏展示"}</p>
          </div>

          <div className="community-stat">
            <span>剩余额度</span>
            <strong>{showStock ? formatNumber(remaining) : "限量"}</strong>
            <p>{remaining <= 0 ? "当前活动额度已发完" : "库存会在领取成功后实时扣减"}</p>
          </div>

          <div className="community-stat">
            <span>活动周期</span>
            <strong>{showEndTime ? (statusLabels[project.status] || project.status) : "进行中"}</strong>
            <p>{showEndTime ? timeRange : "结束时间由运营方控制"}</p>
          </div>
        </section>

        <section className="community-claim__details">
          <div className="community-panel">
            <div className="community-panel__header">
              <h3>领取资格</h3>
              <span>实时校验</span>
            </div>
            <div className="eligibility-list">
              <div className="eligibility-item is-done"><b>1</b><span>使用社区账号完成身份确认</span></div>
              <div className={`eligibility-item ${project.requireCaptcha ? "" : "is-done"}`}><b>2</b><span>{project.requireCaptcha ? "完成人机验证后领取" : "当前节点无需额外验证码"}</span></div>
              <div className="eligibility-item is-done"><b>3</b><span>系统校验库存、账号、IP 与设备指纹</span></div>
            </div>
          </div>

          <div className="community-panel">
            <div className="community-panel__header">
              <h3>活动规则</h3>
              <span>{rulesList.length ? `${rulesList.length} 条` : "默认规则"}</span>
            </div>
            {rulesList.length ? (
              <ul className="claim-rule-list">
                {rulesList.map((rule, index) => <li key={`${rule}-${index}`}>{rule}</li>)}
              </ul>
            ) : (
              <p className="community-panel__empty">每个社区账号仅按活动限制领取，异常设备、重复请求和黑名单会被自动拦截。</p>
            )}
          </div>
        </section>

        {isLoggedIn() && (
          <section className="community-panel community-claim__history">
            <div className="community-panel__header">
              <h3>我的最近福利</h3>
              <span>{myClaims.length ? `${myClaims.length} 条` : "暂无记录"}</span>
            </div>
            {myClaims.length ? (
              <div className="claim-history-list">
                {myClaims.map((item) => (
                  <div className="claim-history-item" key={item.id || item.claimToken || item.createdAt}>
                    <div>
                      <strong>{item.campaignName || item.nodeName || "社区福利"}</strong>
                      <span>{formatTime(item.createdAt)} · {item.nodeName || "领取节点"}</span>
                    </div>
                    <button className="btn btn--text" onClick={() => copyReward(item.rewardContent || item.code)}>
                      复制
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="community-panel__empty">你在本系统领取过的福利会显示在这里，方便之后回看兑换信息。</p>
            )}
          </section>
        )}
      </main>

      <footer className="claim-footer-bar">
        Powered by {brand.systemName} · {brand.brandEnglishName}
      </footer>
      {toast && <div className="toast" onAnimationEnd={() => {}}>{toast}</div>}
    </div>
  );
}

function normalizeClaimResult(result) {
  return {
    claimId: result.claimId,
    claimToken: result.claimToken,
    campaignId: result.campaignId,
    nodeId: result.nodeId,
    code: result.code || result.rewardContent,
    rewardContent: result.rewardContent || result.code,
    claimedAt: result.claimedAt,
  };
}
