<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Link, Setting } from '@element-plus/icons-vue'
import { subsiteEventApi, type SubsiteEvent } from '@/api/subsiteEvent'
import {
  getMetaverseCategoryMeta,
  metaverseAccessMeta,
  metaverseAdminWarnings,
  metaverseFlows,
  metaverseSpaces,
  metaverseStatusMeta,
  resolveMetaverseHref,
  type MetaverseSpace,
} from '@/data/metaverseSpaces'

const eventLoading = ref(false)
const eventRows = ref<SubsiteEvent[]>([])
const eventTotal = ref(0)
const eventQuery = reactive({
  page: 1,
  pageSize: 10,
  source: '',
  eventType: '',
  userId: '',
  status: '',
})

const summaryCards = computed(() => [
  { label: '空间总数', value: metaverseSpaces.length, note: '前台星港统一展示' },
  { label: '内测空间', value: metaverseSpaces.filter(space => space.status === 'beta').length, note: '需要重点看联调状态' },
  { label: '管理员入口', value: metaverseSpaces.filter(space => space.access === 'admin').length, note: '仅管理员可见可进' },
  { label: 'SSO 子站', value: metaverseSpaces.filter(space => space.clientId).length, note: '已登记 clientId' },
])

const ledgerSummaryCards = computed(() => [
  { label: '事件总数', value: eventTotal.value, note: '当前筛选条件下' },
  { label: '成功事件', value: eventRows.value.filter(item => item.severity === 'success').length, note: '本页已完成' },
  { label: '需关注', value: eventRows.value.filter(item => ['warning', 'danger', 'error'].includes(item.severity)).length, note: '本页异常/退款' },
])

const tableData = computed(() =>
  metaverseSpaces.map(space => ({
    ...space,
    categoryLabel: getMetaverseCategoryMeta(space.category)?.label || space.category,
    statusLabel: metaverseStatusMeta[space.status].label,
    statusType: metaverseStatusMeta[space.status].type,
    accessLabel: metaverseAccessMeta[space.access].label,
    entryHref: resolveMetaverseHref(space),
  }))
)

const openHref = (href?: string) => {
  if (!href) return
  if (/^https?:\/\//i.test(href)) {
    window.open(href, '_blank', 'noopener,noreferrer')
    return
  }
  window.location.href = href
}

const copyHref = async (space: MetaverseSpace) => {
  const href = resolveMetaverseHref(space)
  try {
    await navigator.clipboard.writeText(href)
    ElMessage.success(`已复制 ${space.title} 的入口地址`)
  } catch {
    ElMessage.error('复制失败，请手动复制入口地址')
  }
}

const fetchEvents = async () => {
  eventLoading.value = true
  try {
    const res = await subsiteEventApi.admin({
      page: eventQuery.page,
      pageSize: eventQuery.pageSize,
      source: eventQuery.source || undefined,
      eventType: eventQuery.eventType || undefined,
      userId: eventQuery.userId || undefined,
      status: eventQuery.status || undefined,
    })
    if (res.code === 2000) {
      eventRows.value = res.data.records || []
      eventTotal.value = Number(res.data.total || 0)
    }
  } finally {
    eventLoading.value = false
  }
}

const resetEventQuery = () => {
  eventQuery.page = 1
  eventQuery.source = ''
  eventQuery.eventType = ''
  eventQuery.userId = ''
  eventQuery.status = ''
  fetchEvents()
}

const handleEventPageChange = (page: number) => {
  eventQuery.page = page
  fetchEvents()
}

const severityTagType = (severity: string) => {
  if (severity === 'success') return 'success'
  if (severity === 'warning') return 'warning'
  if (severity === 'danger' || severity === 'error') return 'danger'
  return 'info'
}

onMounted(fetchEvents)
</script>

<template>
  <div class="metaverse-admin-page">
    <section class="admin-hero">
      <div>
        <p class="eyebrow">Zens Starport Config</p>
        <h1>星港配置</h1>
        <p>集中盘点主站与子项目入口、SSO Client、权限门槛和上线前风险项。</p>
      </div>
      <el-button class="hero-action" type="primary" :icon="Setting" @click="openHref('/metaverse')">
        查看前台星港
      </el-button>
    </section>

    <section class="summary-grid">
      <article v-for="card in summaryCards" :key="card.label" class="summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </section>

    <section class="warning-grid">
      <article v-for="warning in metaverseAdminWarnings" :key="warning.title" class="warning-card">
        <el-icon><component :is="warning.icon" /></el-icon>
        <div>
          <h2>{{ warning.title }}</h2>
          <p>{{ warning.description }}</p>
        </div>
      </article>
    </section>

    <section class="table-panel">
      <div class="panel-head">
        <div>
          <h2>空间清单</h2>
          <p>V1 为配置驱动，不新增数据库表；上线域名、权限和 clientId 应在发布前统一复核。</p>
        </div>
        <el-tag type="warning" effect="light">Static Config V1</el-tag>
      </div>

      <el-table :data="tableData" class="spaces-table" stripe>
        <el-table-column label="空间" min-width="230" fixed>
          <template #default="{ row }">
            <div class="space-cell" :style="{ '--space-accent': row.accent }">
              <span class="space-icon">
                <el-icon><component :is="row.icon" /></el-icon>
              </span>
              <div>
                <strong>{{ row.title }}</strong>
                <small>{{ row.system }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryLabel" label="分类" width="110" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="row.statusType">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="105">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.accessLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="clientId" label="Client ID" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.clientId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入口地址" min-width="220">
          <template #default="{ row }">
            <span class="href-text">{{ row.entryHref }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Link" @click="openHref(row.entryHref)">进入</el-button>
            <el-button text :disabled="!row.adminHref" @click="openHref(row.adminHref)">管理</el-button>
            <el-button text :icon="CopyDocument" @click="copyHref(row)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="event-panel">
      <div class="panel-head">
        <div>
          <h2>子系统事件账本</h2>
          <p>商城、CDK、抽奖等子站回流到主站的业务事件。用于排查积分、订单、开奖、领取等闭环链路。</p>
        </div>
        <el-button :loading="eventLoading" @click="fetchEvents">刷新</el-button>
      </div>

      <div class="ledger-summary-grid">
        <article v-for="card in ledgerSummaryCards" :key="card.label" class="ledger-summary-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.note }}</small>
        </article>
      </div>

      <div class="event-filter">
        <el-input v-model="eventQuery.source" clearable placeholder="来源 zdc-shop" />
        <el-input v-model="eventQuery.eventType" clearable placeholder="事件类型 shop.order.delivered" />
        <el-input v-model="eventQuery.userId" clearable placeholder="用户 ID" />
        <el-input v-model="eventQuery.status" clearable placeholder="状态 delivered/refunded" />
        <el-button type="primary" @click="eventQuery.page = 1; fetchEvents()">查询</el-button>
        <el-button @click="resetEventQuery">重置</el-button>
      </div>

      <el-table v-loading="eventLoading" :data="eventRows" class="events-table">
        <el-table-column label="事件" min-width="270" fixed>
          <template #default="{ row }">
            <div class="event-cell">
              <strong>{{ row.title }}</strong>
              <small>{{ row.content }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="eventType" label="类型" min-width="190" />
        <el-table-column prop="userId" label="用户" min-width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.userId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="severityTagType(row.severity)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="relatedId" label="关联对象" min-width="180">
          <template #default="{ row }">
            <span class="mono href-text">{{ row.relatedId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>

      <div class="event-pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="eventQuery.page"
          :page-size="eventQuery.pageSize"
          :total="eventTotal"
          @current-change="handleEventPageChange"
        />
      </div>
    </section>

    <section class="flow-panel">
      <div class="panel-head">
        <div>
          <h2>集成链路</h2>
          <p>用于检查子项目是否复用了主站身份、积分、内容或媒体服务能力。</p>
        </div>
      </div>
      <div class="flow-list">
        <article v-for="flow in metaverseFlows" :key="flow.id" class="flow-item">
          <div class="flow-title">
            <el-icon><component :is="flow.icon" /></el-icon>
            <strong>{{ flow.title }}</strong>
          </div>
          <p>{{ flow.description }}</p>
          <div class="flow-route">
            <span>{{ flow.from }}</span>
            <span>-></span>
            <span>{{ flow.to }}</span>
          </div>
          <div class="touchpoints">
            <el-tag v-for="point in flow.touchpoints" :key="point" size="small" effect="plain">
              {{ point }}
            </el-tag>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metaverse-admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-hero,
.summary-card,
.warning-card,
.table-panel,
.event-panel,
.flow-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
}

.admin-hero {
  min-height: 142px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
  border-color: #f5dfaa;
  background:
    radial-gradient(420px 240px at 84% 12%, rgba(246, 168, 0, 0.18), transparent 68%),
    linear-gradient(135deg, #fff8e7 0%, #fffdf8 48%, #ffffff 100%);
  color: #111827;
}

.eyebrow {
  margin: 0 0 8px;
  color: #b45309;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0;
}

.admin-hero h1 {
  margin: 0;
  color: #111827;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: 0;
}

.admin-hero p:last-child {
  margin: 8px 0 0;
  max-width: 720px;
  color: #6b7280;
  line-height: 1.7;
}

.hero-action {
  --el-button-bg-color: #f6a800;
  --el-button-border-color: #f6a800;
  --el-button-hover-bg-color: #e99a00;
  --el-button-hover-border-color: #e99a00;
  --el-button-active-bg-color: #d88b00;
  --el-button-active-border-color: #d88b00;
  flex: 0 0 auto;
  min-height: 40px;
  border-radius: 8px;
  font-weight: 800;
  box-shadow: 0 10px 24px rgba(246, 168, 0, 0.18);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  min-height: 112px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 800;
}

.summary-card strong {
  color: var(--el-text-color-primary);
  font-size: 32px;
  line-height: 1;
}

.summary-card small {
  color: var(--el-text-color-placeholder);
  line-height: 1.45;
}

.warning-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.warning-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  padding: 14px;
  background: #fffaf0;
}

.warning-card > .el-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: #a16207;
  background: #fef3c7;
}

.warning-card h2 {
  margin: 0;
  font-size: 14px;
  color: #7a5700;
}

.warning-card p {
  margin: 6px 0 0;
  color: #8a6a18;
  font-size: 13px;
  line-height: 1.55;
}

.table-panel,
.event-panel,
.flow-panel {
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.spaces-table {
  width: 100%;
}

.ledger-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.ledger-summary-card {
  min-height: 88px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ledger-summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 800;
}

.ledger-summary-card strong {
  color: var(--el-text-color-primary);
  font-size: 26px;
  line-height: 1;
}

.ledger-summary-card small {
  color: var(--el-text-color-placeholder);
}

.event-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr)) auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.events-table {
  width: 100%;
}

.event-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.event-cell strong,
.event-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-cell strong {
  color: var(--el-text-color-primary);
}

.event-cell small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.event-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.space-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.space-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 8px;
  color: var(--space-accent);
  background: color-mix(in srgb, var(--space-accent) 12%, white);
}

.space-cell strong,
.space-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-cell strong {
  color: var(--el-text-color-primary);
}

.space-cell small,
.href-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.href-text {
  display: block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.flow-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px;
  background: var(--el-fill-color-blank);
}

.flow-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
}

.flow-title .el-icon {
  color: var(--el-color-primary);
}

.flow-item p {
  margin: 10px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.flow-route {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 800;
}

.touchpoints {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .summary-grid,
  .warning-grid,
  .ledger-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .event-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-hero,
  .panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .warning-grid,
  .ledger-summary-grid,
  .flow-list {
    grid-template-columns: 1fr;
  }

  .event-filter {
    grid-template-columns: 1fr;
  }

  .event-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
