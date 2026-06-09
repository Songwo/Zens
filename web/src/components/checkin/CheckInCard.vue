<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { checkInApi, type CheckInStatus } from '@/api/checkin'
import { pulseNotification } from '@/utils/pulseNotification'
import { Calendar, Check } from '@element-plus/icons-vue'

const status = ref<CheckInStatus | null>(null)
const loading = ref(true)
const checking = ref(false)

const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

const fetchStatus = async () => {
  loading.value = true
  try {
    status.value = (await checkInApi.getStatus()).data
  } catch {
    /* 静默：签到卡不阻塞页面 */
  } finally {
    loading.value = false
  }
}

const handleCheckIn = async () => {
  if (checking.value || status.value?.checkedToday) return
  checking.value = true
  try {
    const res = await checkInApi.checkIn()
    status.value = res.data
    const { continuousDays, rewardExp, rewardPoints } = res.data
    pulseNotification.success(
      `连续签到 ${continuousDays} 天，获得 +${rewardExp} 经验 +${rewardPoints} 积分`,
      '签到成功'
    )
  } catch (e: any) {
    pulseNotification.error(e?.response?.data?.message || '签到失败，请稍后再试')
    // 失败可能是“今天已签到”，刷新一次状态纠正 UI
    fetchStatus()
  } finally {
    checking.value = false
  }
}

// 本月日历单元格：含前导空白对齐星期
const leadingBlanks = computed(() => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1).getDay()
})

const monthDays = computed(() => {
  const set = new Set(status.value?.monthDates || [])
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const todayDate = now.getDate()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: Array<{ day: number; checked: boolean; isToday: boolean; isFuture: boolean }> = []
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({
      day: d,
      checked: set.has(dateStr),
      isToday: d === todayDate,
      isFuture: d > todayDate,
    })
  }
  return cells
})

onMounted(fetchStatus)
</script>

<template>
  <div class="checkin-card" v-loading="loading">
    <div class="checkin-head">
      <div class="checkin-title">
        <el-icon><Calendar /></el-icon>
        <span>每日签到</span>
      </div>
      <el-button
        type="primary"
        round
        :icon="Check"
        :loading="checking"
        :disabled="status?.checkedToday"
        @click="handleCheckIn"
      >
        {{ status?.checkedToday ? '今日已签到' : '签到' }}
      </el-button>
    </div>

    <div class="checkin-stats">
      <div class="stat">
        <span class="stat-num">{{ status?.continuousDays ?? 0 }}</span>
        <span class="stat-label">连续天数</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat">
        <span class="stat-num">{{ status?.totalDays ?? 0 }}</span>
        <span class="stat-label">累计签到</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat">
        <span class="stat-num">{{ status?.totalPoints ?? 0 }}</span>
        <span class="stat-label">当前积分</span>
      </div>
    </div>

    <div class="checkin-calendar">
      <div class="cal-week">
        <span v-for="w in WEEK_LABELS" :key="w" class="cal-week-label">{{ w }}</span>
      </div>
      <div class="cal-grid">
        <span v-for="b in leadingBlanks" :key="`b${b}`" class="cal-cell blank"></span>
        <span
          v-for="c in monthDays"
          :key="c.day"
          class="cal-cell"
          :class="{ checked: c.checked, today: c.isToday, future: c.isFuture }"
        >
          <el-icon v-if="c.checked" class="cal-check"><Check /></el-icon>
          <template v-else>{{ c.day }}</template>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.checkin-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.checkin-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.checkin-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.checkin-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 14px;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--el-color-primary);
  line-height: 1.1;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: var(--el-border-color-lighter);
}

.checkin-calendar {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.cal-week,
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.cal-week-label {
  text-align: center;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  padding-bottom: 4px;
}

.cal-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
}

.cal-cell.blank {
  background: transparent;
}

.cal-cell.future {
  color: var(--el-text-color-placeholder);
  background: transparent;
}

.cal-cell.checked {
  background: var(--el-color-primary);
  color: #fff;
}

.cal-cell.today {
  box-shadow: 0 0 0 2px var(--el-color-primary);
}

.cal-check {
  font-size: 13px;
}
</style>
