<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { performanceApi, type PerformanceSummary, type SlowRequestEvent, type SlowSqlEvent, type WebVitalEvent } from '@/api/performance'

const loading = ref(false)
const keyword = ref('')
const minMs = ref(300)
const limit = ref(80)
const summary = ref<PerformanceSummary>({})
const slowRequests = ref<SlowRequestEvent[]>([])
const slowSql = ref<SlowSqlEvent[]>([])
const webVitals = ref<WebVitalEvent[]>([])

const hikari = computed(() => summary.value.hikari || {})
const jvm = computed(() => summary.value.jvm || {})
const webVitalMetrics = computed(() => summary.value.webVitals?.metrics || {})
const webVitalTotal = computed(() => summary.value.webVitals?.total || 0)
const lcpP75 = computed(() => webVitalMetrics.value.LCP?.p75 ?? '-')
const clsP75 = computed(() => webVitalMetrics.value.CLS?.p75 ?? '-')

const formatTime = (value?: string) => {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      keyword: keyword.value.trim() || undefined,
      minMs: minMs.value || undefined,
      limit: limit.value,
    }
    const [summaryRes, requestsRes, sqlRes, vitalsRes] = await Promise.all([
      performanceApi.summary(),
      performanceApi.slowRequests(params),
      performanceApi.slowSql(params),
      performanceApi.webVitals({ limit: limit.value }),
    ])
    summary.value = summaryRes.data || {}
    slowRequests.value = requestsRes.data || []
    slowSql.value = sqlRes.data || []
    webVitals.value = vitalsRes.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '性能数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="performance-page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2>性能观测</h2>
        <p>慢接口、慢 SQL、连接池、JVM 与真实用户 Web Vitals 快照</p>
      </div>
      <el-button type="primary" :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <div class="metrics-grid">
      <el-card shadow="never">
        <div class="metric-title">HikariCP</div>
        <div class="metric-main">{{ hikari.active ?? '-' }} / {{ hikari.maxPoolSize ?? '-' }}</div>
        <div class="metric-sub">活跃连接 / 最大连接，等待 {{ hikari.waiting ?? 0 }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="metric-title">JVM 堆内存</div>
        <div class="metric-main">{{ jvm.heapUsedMB ?? '-' }} MB</div>
        <div class="metric-sub">上限 {{ jvm.heapMaxMB ?? '-' }} MB，使用率 {{ jvm.heapUsageRatio ?? '-' }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="metric-title">慢接口</div>
        <div class="metric-main">{{ slowRequests.length }}</div>
        <div class="metric-sub">当前筛选结果，内存最多保留最近 200 条</div>
      </el-card>
      <el-card shadow="never">
        <div class="metric-title">Web Vitals</div>
        <div class="metric-main">{{ webVitalTotal }}</div>
        <div class="metric-sub">LCP P75 {{ lcpP75 }}ms，CLS P75 {{ clsP75 }}</div>
      </el-card>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜索 URI、Mapper 或 SQL"
        clearable
        @keyup.enter="loadData"
      />
      <el-input-number v-model="minMs" :min="0" :step="100" controls-position="right" />
      <el-select v-model="limit" class="limit-select">
        <el-option :value="30" label="最近 30 条" />
        <el-option :value="80" label="最近 80 条" />
        <el-option :value="150" label="最近 150 条" />
      </el-select>
      <el-button type="primary" @click="loadData">筛选</el-button>
    </el-card>

    <el-card shadow="never" class="web-vitals-card">
      <template #header>真实用户体验指标</template>
      <div class="vitals-grid">
        <div v-for="(metric, name) in webVitalMetrics" :key="name" class="vital-item">
          <span>{{ name }}</span>
          <strong>{{ metric.p75 ?? '-' }}</strong>
          <small>P75 / 平均 {{ metric.avg ?? '-' }} / 差 {{ metric.poor ?? 0 }}</small>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="table-card">
          <template #header>慢接口</template>
          <el-table :data="slowRequests" height="430" empty-text="暂无慢接口记录">
            <el-table-column prop="time" label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="uri" label="路径" min-width="220" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column prop="costMs" label="耗时(ms)" width="100" sortable />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="table-card">
          <template #header>慢 SQL</template>
          <el-table :data="slowSql" height="430" empty-text="暂无慢 SQL 记录">
            <el-table-column prop="time" label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="mapperId" label="Mapper" min-width="220" show-overflow-tooltip />
            <el-table-column prop="costMs" label="耗时(ms)" width="100" sortable />
            <el-table-column prop="sql" label="SQL" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <template #header>Web Vitals 明细</template>
      <el-table :data="webVitals" height="360" empty-text="暂无前端体验指标">
        <el-table-column prop="time" label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.time) }}</template>
        </el-table-column>
        <el-table-column prop="name" label="指标" width="90" />
        <el-table-column prop="value" label="数值" width="110" sortable />
        <el-table-column prop="rating" label="评级" width="150" />
        <el-table-column prop="route" label="页面" min-width="220" show-overflow-tooltip />
        <el-table-column prop="navigationType" label="导航类型" width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.performance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-head h2 {
  margin: 0;
  font-size: 22px;
}

.page-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-main {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.metric-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.filter-card :deep(.el-card__body) {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 150px 140px auto;
  gap: 10px;
  align-items: center;
}

.limit-select {
  width: 140px;
}

.table-card {
  height: 100%;
}

.web-vitals-card :deep(.el-card__body) {
  padding: 14px;
}

.vitals-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.vital-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
}

.vital-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.vital-item strong {
  color: var(--el-text-color-primary);
  font-size: 22px;
  line-height: 1;
}

.vital-item small {
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}

@media (max-width: 1100px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .vitals-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metrics-grid,
  .vitals-grid,
  .filter-card :deep(.el-card__body) {
    grid-template-columns: 1fr;
  }

  .limit-select {
    width: 100%;
  }
}
</style>
