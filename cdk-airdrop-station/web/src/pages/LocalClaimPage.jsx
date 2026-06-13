import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { claimApi, devApi, setAuthToken, userApi } from "../lib/api";
import { DEFAULT_BRAND, normalizeBrand, setAppTitle } from "../lib/brand";
import { getFingerprint } from "../lib/storage";

const DEFAULT_NODE = "freshman-2026";

function normalizeClaimResult(result) {
  return {
    code: result?.code || result?.rewardContent || "",
    claimToken: result?.claimToken || "",
    claimedAt: result?.claimedAt || "",
    message: result?.message || "",
  };
}

function formatTime(value) {
  if (!value) return "--";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function LocalClaimPage() {
  const [searchParams] = useSearchParams();
  const initialCode = searchParams.get("code") || localStorage.getItem("local-claim-code") || DEFAULT_NODE;
  const [projectCode, setProjectCode] = useState(initialCode);
  const [username, setUsername] = useState(localStorage.getItem("local-claim-username") || "test_user_01");
  const [nickname, setNickname] = useState(localStorage.getItem("local-claim-nickname") || "本地测试账号");
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [project, setProject] = useState(null);
  const [result, setResult] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);
  const [brand, setBrand] = useState(DEFAULT_BRAND);
  const fingerprint = useMemo(() => getFingerprint(), []);

  useEffect(() => {
    setAppTitle(DEFAULT_BRAND);
    fetchBrand();
  }, []);

  useEffect(() => {
    localStorage.setItem("local-claim-code", projectCode);
  }, [projectCode]);

  useEffect(() => {
    localStorage.setItem("local-claim-username", username);
  }, [username]);

  useEffect(() => {
    localStorage.setItem("local-claim-nickname", nickname);
  }, [nickname]);

  async function fetchBrand() {
    try {
      const data = await fetch("/health").then((res) => res.json());
      if (data?.systemName) {
        const next = normalizeBrand(data);
        setBrand(next);
        setAppTitle(next);
      }
    } catch {
      setBrand(DEFAULT_BRAND);
    }
  }

  async function loginForLocalTest() {
    const name = username.trim();
    if (!name) throw new Error("请输入本地测试账号");
    const data = await devApi.login({ username: name, nickname: nickname.trim() || name });
    const token = data?.token || data?.data?.token;
    if (!token) throw new Error("后端没有返回测试登录凭证");
    setAuthToken(token);
    const user = data?.user || data?.data?.user || (await userApi.getMe()).user;
    setCurrentUser(user);
    return user;
  }

  async function checkNode() {
    const code = projectCode.trim();
    if (!code) {
      setError("请输入领取页项目码 / 节点 slug");
      return;
    }
    setChecking(true);
    setError("");
    setResult(null);
    try {
      await loginForLocalTest();
      const data = await claimApi.getNode(code, fingerprint);
      setProject(data);
      if (data?.userClaimed) {
        setResult(normalizeClaimResult({
          code: data.userRewardContent,
          rewardContent: data.userRewardContent,
          claimedAt: data.userClaimedAt,
        }));
      }
      setToast("账号已登录，节点信息已读取");
    } catch (err) {
      setProject(null);
      setError(err.message || "读取节点失败");
    } finally {
      setChecking(false);
      setTimeout(() => setToast(""), 1600);
    }
  }

  async function claimNow(event) {
    event.preventDefault();
    const code = projectCode.trim();
    if (!code) {
      setError("请输入领取页项目码 / 节点 slug");
      return;
    }
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const user = await loginForLocalTest();
      const node = await claimApi.getNode(code, fingerprint);
      setProject(node);
      if (node?.requireCaptcha) {
        throw new Error("该节点开启了 hCaptcha，请点右侧正式领取页完成验证领取");
      }
      const claimed = await claimApi.submitNode(code, { fingerprint });
      const next = normalizeClaimResult(claimed);
      setCurrentUser(user);
      setResult(next);
      setToast("领取成功");
    } catch (err) {
      setError(err.message || "领取失败");
    } finally {
      setLoading(false);
      setTimeout(() => setToast(""), 1600);
    }
  }

  async function copyCode() {
    if (!result?.code) return;
    await navigator.clipboard.writeText(result.code);
    setToast("CDK 已复制");
    setTimeout(() => setToast(""), 1600);
  }

  const directClaimPath = `/claim/${encodeURIComponent(projectCode.trim() || DEFAULT_NODE)}`;

  return (
    <div className="local-claim-page">
      <main className="local-claim-shell anim-fade-up">
        <section className="local-claim-panel local-claim-panel--main">
          <div className="local-claim-brand">
            <img src="/logo.png" alt="Logo" width="48" height="48" />
            <div>
              <span>Local CDK Test</span>
              <h1>{brand.systemName}</h1>
            </div>
          </div>

          <form className="local-claim-form" onSubmit={claimNow}>
            <label>
              <span>测试账号</span>
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="test_user_01"
                autoComplete="username"
              />
            </label>

            <label>
              <span>账号显示名</span>
              <input
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="本地测试账号"
              />
            </label>

            <label>
              <span>领取页项目码 / 节点 slug</span>
              <input
                value={projectCode}
                onChange={(event) => setProjectCode(event.target.value)}
                placeholder={DEFAULT_NODE}
              />
            </label>

            {error && <div className="local-claim-alert local-claim-alert--error">{error}</div>}

            <div className="local-claim-actions">
              <button type="submit" className="btn btn--primary" disabled={loading}>
                {loading ? "正在领取..." : "登录账号并直接领取"}
              </button>
              <button type="button" className="btn btn--secondary" disabled={checking || loading} onClick={checkNode}>
                {checking ? "读取中..." : "只登录并检查节点"}
              </button>
            </div>
          </form>
        </section>

        <aside className="local-claim-panel">
          <div className="local-claim-sidehead">
            <span>当前状态</span>
            <Link className="btn btn--secondary" to={directClaimPath}>正式领取页</Link>
          </div>

          <div className="local-claim-meta">
            <div>
              <span>账号</span>
              <strong>{currentUser?.nickname || currentUser?.username || "未登录"}</strong>
            </div>
            <div>
              <span>节点</span>
              <strong>{project?.nodeSlug || projectCode || "--"}</strong>
            </div>
            <div>
              <span>库存</span>
              <strong>{project ? `${project.remaining ?? project.remainingCount ?? "--"} / ${project.totalStock ?? "--"}` : "--"}</strong>
            </div>
            <div>
              <span>状态</span>
              <strong>{project?.status || "--"}</strong>
            </div>
          </div>

          {project && (
            <div className="local-claim-node">
              <span>{project.name}</span>
              <p>{project.description || "该节点没有填写描述。"}</p>
            </div>
          )}

          {result ? (
            <div className="local-claim-result">
              <span>领取结果</span>
              <code>{result.code || "已领取，但后端没有返回 CDK 内容"}</code>
              <p>领取时间 {formatTime(result.claimedAt)}</p>
              <button className="btn btn--primary" type="button" onClick={copyCode} disabled={!result.code}>
                复制 CDK
              </button>
            </div>
          ) : (
            <div className="local-claim-empty">
              <span>可用测试码</span>
              <p>默认 `freshman-2026`。如果你在后台新建了项目或节点，把领取链接最后一段填到左侧即可。</p>
            </div>
          )}
        </aside>
      </main>
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
