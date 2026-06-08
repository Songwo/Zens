import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { adminApi } from "../../lib/api";
import {
  AlertPanel,
  CopyButton,
  DataTable,
  FilterSelect,
  MiniStatCard,
  PageHeader,
  ProgressBar,
  QuickActions,
  SearchInput,
  StatCard,
  StatusBadge,
  SystemHealthCard,
} from "../../components/ui";
import { DistributionGuideCard } from "../../components/DistributionGuide";

function fmt(n) {
  return new Intl.NumberFormat("zh-CN").format(Number(n || 0));
}

function formatTime(iso) {
  if (!iso) return "--";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function campaignStatusLabel(status) {
  if (status === "disabled") return "paused";
  return status;
}

function percent(part, total) {
  if (!total) return 0;
  return Math.min(100, Math.round((Number(part || 0) / Number(total || 0)) * 100));
}

function getOpsInsight(stats = {}) {
  const totalStock = Number(stats.totalStock || 0);
  const remaining = Number(stats.remainingCount || 0);
  const successRate = Number(stats.successRate || 0);
  const lowStock = totalStock > 0 && remaining / totalStock < 0.15;
  if (remaining <= 0 && totalStock > 0) return { tone: "danger", title: "库存已耗尽", text: "优先补充 CDK 或归档已结束活动，避免社区用户继续进入空通道。" };
  if (lowStock) return { tone: "warning", title: "库存进入低水位", text: "建议先检查高转化节点，再决定补货、暂停入口或延长活动周期。" };
  if (successRate < 60 && Number(stats.claimedCount || 0) > 0) return { tone: "warning", title: "领取成功率偏低", text: "重点查看失败原因、验证码配置和风控命中记录。" };
  return { tone: "success", title: "活动分发稳定", text: "当前库存和领取表现正常，可以继续观察节点转化和社区反馈。" };
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [sort, setSort] = useState("recent");

  async function reload() {
    setLoading(true);
    try {
      const [dashboardData, healthData] = await Promise.all([
        adminApi.dashboard(),
        adminApi.health().catch(() => null),
      ]);
      setData(dashboardData);
      setHealth(healthData);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { reload(); }, []);

  const campaigns = useMemo(() => {
    const q = search.trim().toLowerCase();
    const rows = (data?.campaigns || []).filter((item) => {
      if (status !== "all" && item.status !== status) return false;
      if (!q) return true;
      return [item.name, item.id, item.projectCode, item.claimUrl].some((v) => String(v || "").toLowerCase().includes(q));
    });
    return [...rows].sort((a, b) => {
      if (sort === "stock") return Number(a.remaining || 0) - Number(b.remaining || 0);
      if (sort === "claimed") return Number(b.claimedCount || 0) - Number(a.claimedCount || 0);
      return new Date(b.createdAt || b.startTime || 0) - new Date(a.createdAt || a.startTime || 0);
    });
  }, [data, search, status, sort]);

  const stats = data?.stats || {};
  const nodes = data?.nodes || [];
  const captchaRequired = nodes.some((node) => node.requireCaptcha);
  const captchaConfigured = Boolean(import.meta.env.VITE_HCAPTCHA_SITE_KEY);
  const opsInsight = getOpsInsight(stats);
  const stockUsage = percent(stats.claimedCount, stats.totalStock);
  const activeNodes = nodes.filter((node) => node.status === "active").length;
  const topNode = [...nodes].sort((a, b) => Number(b.claims || 0) - Number(a.claims || 0))[0];

  const coreStats = [
    ["活跃活动", stats.activeCampaigns, `共 ${fmt(stats.totalCampaigns)} 个活动`, "success"],
    ["库存消耗", `${stockUsage}%`, `${fmt(stats.claimedCount)} / ${fmt(stats.totalStock)}`, "gold"],
    ["今日领取", stats.todayClaims, "今日成功发放", "default"],
    ["剩余库存", stats.remainingCount, "可继续分发", Number(stats.remainingCount) === 0 ? "danger" : "default"],
  ];
  const miniStats = [
    ["活跃节点", activeNodes, `共 ${fmt(stats.totalNodes)} 个入口`, "success"],
    ["异常节点", stats.abnormalNodes, "暂停或异常节点", Number(stats.abnormalNodes) ? "danger" : "success"],
    ["最佳节点", topNode?.name || "--", topNode ? `${fmt(topNode.claims)} 次领取` : "暂无数据", "default"],
    ["领取成功率", `${(stats.successRate || 0).toFixed(1)}%`, "已领取 / 总库存", "success"],
  ];

  const quickActions = [
    { label: "新建活动", icon: "活", onClick: () => navigate("/admin/campaigns?open=create") },
    { label: "导入 CDK", icon: "码", onClick: () => navigate("/admin/cdks/import") },
    { label: "创建节点", icon: "链", onClick: () => navigate("/admin/nodes/create") },
    { label: "领取记录", icon: "录", onClick: () => navigate("/admin/claims") },
    { label: "验证码", icon: "验", onClick: () => navigate("/admin/captcha") },
    { label: "系统日志", icon: "志", onClick: () => navigate("/admin/logs") },
  ];

  return (
    <div className="admin-page dashboard-page">
      <PageHeader
        eyebrow="Operations"
        title="社区福利运营台"
        description="把活动创建、CDK 库存、领取入口、风控和社区反馈放在同一张工作台里。"
        actions={<div className="btn-group"><button className="btn btn--primary" onClick={() => navigate("/admin/campaigns?open=create")}>新建活动</button><button className="btn btn--secondary" onClick={() => navigate("/admin/nodes/create")}>创建节点</button></div>}
      />

      <section className={`ops-hero ops-hero--${opsInsight.tone}`}>
        <div>
          <span className="ops-hero__eyebrow">当前运营判断</span>
          <h2>{opsInsight.title}</h2>
          <p>{opsInsight.text}</p>
        </div>
        <div className="ops-hero__steps" aria-label="活动发布闭环">
          <button onClick={() => navigate("/admin/projects")}>项目归属</button>
          <button onClick={() => navigate("/admin/campaigns?open=create")}>配置活动</button>
          <button onClick={() => navigate("/admin/cdks/import")}>导入库存</button>
          <button onClick={() => navigate("/admin/nodes/create")}>发布入口</button>
          <button onClick={() => navigate("/admin/analytics")}>复盘数据</button>
        </div>
      </section>

      <section className="stats-grid stats-grid--core">
        {coreStats.map(([label, value, hint, tone]) => <StatCard key={label} label={label} value={typeof value === "number" ? fmt(value) : value} hint={hint} tone={tone} />)}
      </section>

      <DistributionGuideCard stats={stats} nodes={nodes} captchaConfigured={captchaConfigured} />

      <section className="stats-grid stats-grid--mini">
        {miniStats.map(([label, value, hint, tone]) => <MiniStatCard key={label} label={label} value={typeof value === "number" ? fmt(value) : value} hint={hint} tone={tone} />)}
      </section>

      <section className="dashboard-main-grid">
        <div className="dashboard-primary">
          <section className="panel">
            <div className="panel__header">
              <div><h2>分发活动总览</h2><p>默认展示最近 8 条，支持状态筛选、搜索和排序。</p></div>
              <button className="btn btn--secondary" onClick={() => navigate("/admin/campaigns")}>查看全部活动</button>
            </div>
            <div className="table-toolbar">
              <FilterSelect value={status} onChange={setStatus}>
                <option value="all">全部状态</option>
                <option value="active">进行中</option>
                <option value="disabled">已暂停</option>
                <option value="upcoming">未开始</option>
                <option value="ended">已结束</option>
                <option value="soldout">库存耗尽</option>
              </FilterSelect>
              <SearchInput value={search} onChange={setSearch} placeholder="搜索活动 / 活动 ID / 短链接" />
              <FilterSelect value={sort} onChange={setSort}>
                <option value="recent">最近创建</option>
                <option value="claimed">领取最多</option>
                <option value="stock">库存最少</option>
              </FilterSelect>
              <button className="btn btn--secondary" onClick={reload}>刷新</button>
            </div>
            <DataTable
              loading={loading}
              rows={campaigns}
              pageSize={8}
              emptyText="暂无匹配活动"
              columns={[
                { key: "name", title: "活动名称", render: (r) => <><strong>{r.name}</strong><small>{r.id || r.projectCode}</small></> },
                { key: "status", title: "状态", render: (r) => <StatusBadge status={campaignStatusLabel(r.status)} /> },
                { key: "progress", title: "领取进度", render: (r) => <ProgressBar claimed={r.claimedCount} total={r.totalStock} remaining={r.remaining} /> },
                { key: "time", title: "开始 / 结束", render: (r) => <span>{formatTime(r.startTime)}<br /><small>{formatTime(r.endTime)}</small></span> },
                { key: "nodes", title: "绑定节点", render: (r) => r.nodeCount || 0 },
                { key: "actions", title: "操作", render: (r) => <div className="row-actions"><button className="btn btn--text" onClick={() => navigate("/admin/campaigns")}>详情</button><button className="btn btn--text" onClick={() => navigate("/admin/cdks")}>CDK</button>{r.claimUrl && <CopyButton value={window.location.origin + r.claimUrl}>复制链接</CopyButton>}</div> },
              ]}
            />
          </section>

          <section className="panel">
            <div className="panel__header">
              <div><h2>节点总览</h2><p>观察分发入口的访问、领取、转化和库存。</p></div>
              <button className="btn btn--secondary" onClick={() => navigate("/admin/nodes")}>管理节点</button>
            </div>
            <DataTable
              loading={loading}
              rows={nodes}
              pageSize={6}
              emptyText="暂无分发节点"
              columns={[
                { key: "name", title: "节点", render: (r) => <><strong>{r.name}</strong><small>{r.slug}</small></> },
                { key: "campaignName", title: "绑定活动", render: (r) => <span>{r.campaignName || "--"}</span> },
                { key: "status", title: "状态", render: (r) => <StatusBadge status={r.status === "active" ? (r.campaignStatus || "active") : r.status} /> },
                { key: "traffic", title: "访问 / 领取 / 转化", render: (r) => <span>{fmt(r.visits)} / {fmt(r.claims)} / {r.conversion || 0}%</span> },
                { key: "remaining", title: "剩余库存", render: (r) => fmt(r.remaining) },
                { key: "copy", title: "链接", render: (r) => <CopyButton value={window.location.origin + r.claimUrl}>复制</CopyButton> },
              ]}
            />
          </section>
        </div>

        <aside className="dashboard-side-stack">
          <section className="panel panel--tight">
            <div className="panel__header"><div><h2>异常提醒</h2><p>最多显示 4 条高优先级提醒。</p></div></div>
            <AlertPanel alerts={data?.alerts || []} onViewAll={() => navigate("/admin/logs")} />
          </section>
          <section className="panel panel--tight">
            <div className="panel__header"><div><h2>快捷操作</h2><p>常用运营入口。</p></div></div>
            <QuickActions actions={quickActions} />
          </section>
          <section className="panel panel--tight">
            <div className="panel__header"><div><h2>系统健康</h2><p>关键服务状态概览。</p></div></div>
            <SystemHealthCard health={health || {}} captchaConfigured={captchaConfigured} captchaRequired={captchaRequired} />
          </section>
          <section className="panel panel--tight">
            <div className="panel__header"><div><h2>最近领取</h2><p>最近 4 条成功或失败记录。</p></div></div>
            <div className="recent-claim-list">
              {(data?.recentRecords || []).slice(0, 4).map((record) => (
                <div className="recent-claim" key={record.id || record.claimToken || record.createdAt}>
                  <code>{record.rewardContent || record.code || "--"}</code>
                  <span>{record.ip || record.fingerprint || "匿名"} · {formatTime(record.createdAt)}</span>
                </div>
              ))}
              {!(data?.recentRecords || []).length && <div className="empty-panel empty-panel--compact"><strong>暂无领取记录</strong></div>}
            </div>
          </section>
        </aside>
      </section>
    </div>
  );
}
