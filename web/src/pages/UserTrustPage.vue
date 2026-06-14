<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { trustLevelApi, type TrustInfo } from '@/api/trustLevel'
import { levelApi, type LevelInfo } from '@/api/level'
import { levelTitle } from '@/utils/levelPrivileges'
import { TRUST_LEVELS, trustLevelColor, trustLevelLabel } from '@/utils/trustLevel'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const info = ref<TrustInfo | null>(null)
const levelInfo = ref<LevelInfo | null>(null)

const currentExpLevel = computed(() => levelInfo.value?.level ?? userStore.userInfo?.level ?? 1)
const currentExpTitle = computed(() => levelTitle(currentExpLevel.value))

const currentLevel = computed(() => info.value?.trustLevel ?? 0)
const metrics = computed(() => info.value?.metrics)
const levels = computed(() => info.value?.levels ?? TRUST_LEVELS)

// Song：下一级阈值进度（基于后端返回的指标和本级/下一级要求，这里仅做简单展示）
const progressToNext = computed(() => {
    if (!metrics.value) return 0
    const m = metrics.value
    const next = currentLevel.value + 1
    if (next > 4) return 100
    // 简化：用阅读时长占比作为进度示意
    const targetMinutes = next >= 3 ? 500 : next >= 2 ? 30 : 5
    const cur = Math.min(100, Math.round((m.readTimeSec / 60 / targetMinutes) * 100))
    return cur
})

const readMinutes = computed(() => Math.round((metrics.value?.readTimeSec ?? 0) / 60))

const fetchInfo = async () => {
    loading.value = true
    try {
        const [trustRes, levelRes] = await Promise.all([
            trustLevelApi.info(),
            levelApi.getInfo().catch(() => null),
        ])
        info.value = trustRes.data
        if (levelRes?.data) levelInfo.value = levelRes.data
    } catch (e: any) {
        ElMessage.error(e?.message || '获取信任等级信息失败')
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    fetchInfo()
})
</script>

<template>
    <div class="trust-page" v-loading="loading">
        <div class="page-header">
            <h1 class="page-title">信任等级</h1>
            <p class="page-desc">
                信任等级反映你在社区的行为质量，控制功能权限（外链/附件/私信/举报权重）。TL0-TL3 由行为指标自动晋升，TL3 不活跃会降级，TL4 由管理员授予。
            </p>
        </div>

        <!-- Song：资历副轴徽章 —— 提示用户另有 Lv 资历体系，点击跳转 -->
        <div class="exp-axis-link" @click="router.push('/level')">
            <span class="exp-axis-label">资历等级（经验值轴）</span>
            <span class="exp-axis-badge">Lv.{{ currentExpLevel }} · {{ currentExpTitle }}</span>
            <span class="exp-axis-exp" v-if="levelInfo">{{ levelInfo.experience }} 经验</span>
            <span class="exp-axis-go">查看资历 →</span>
        </div>

        <!-- 当前等级卡片 -->
        <el-card class="current-card" shadow="hover" v-if="info">
            <div class="current-row">
                <div class="level-chip" :style="{ background: trustLevelColor(currentLevel) }">
                    <span class="chip-label">TL{{ currentLevel }}</span>
                    <span class="chip-name">{{ trustLevelLabel(currentLevel) }}</span>
                </div>
                <div class="current-meta">
                    <div class="meta-title">{{ trustLevelLabel(currentLevel) }}</div>
                    <div class="meta-desc">{{ levels[currentLevel]?.description }}</div>
                    <el-progress
                        v-if="currentLevel < 4"
                        :percentage="progressToNext"
                        :stroke-width="8"
                        :color="trustLevelColor(currentLevel)"
                        class="meta-progress"
                    >
                        <span class="progress-text">距离 TL{{ currentLevel + 1 }} 还需努力</span>
                    </el-progress>
                </div>
            </div>
        </el-card>

        <!-- 行为指标 -->
        <el-card class="metrics-card" shadow="never" v-if="metrics">
            <template #header><span class="card-title">行为指标</span></template>
            <div class="metrics-grid">
                <div class="metric">
                    <div class="metric-value">{{ metrics.daysSinceRegister }}</div>
                    <div class="metric-label">注册天数</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.daysVisited }}</div>
                    <div class="metric-label">累计访问天数</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.daysVisitedRecent }}</div>
                    <div class="metric-label">近100天访问</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.postsReadRecent }}</div>
                    <div class="metric-label">近期阅读帖数</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ readMinutes }}</div>
                    <div class="metric-label">累计阅读(分钟)</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.likesReceived }}</div>
                    <div class="metric-label">收到点赞</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.likesGiven }}</div>
                    <div class="metric-label">给出点赞</div>
                </div>
                <div class="metric">
                    <div class="metric-value">{{ metrics.postsCreated }}</div>
                    <div class="metric-label">发布帖子</div>
                </div>
            </div>
        </el-card>

        <!-- 等级路线图 -->
        <el-card class="roadmap-card" shadow="never">
            <template #header><span class="card-title">等级与特权</span></template>
            <div class="roadmap">
                <div
                    v-for="spec in levels"
                    :key="spec.level"
                    class="roadmap-item"
                    :class="{ active: spec.level === currentLevel, achieved: spec.level <= currentLevel }"
                >
                    <div class="roadmap-head">
                        <span class="roadmap-chip" :style="{ background: trustLevelColor(spec.level) }">
                            TL{{ spec.level }}
                        </span>
                        <span class="roadmap-name">{{ spec.label }}</span>
                    </div>
                    <div class="roadmap-desc">{{ spec.description }}</div>
                    <div class="roadmap-priv">
                        <span v-for="p in spec.privileges" :key="p" class="priv-tag">{{ p }}</span>
                    </div>
                </div>
            </div>
        </el-card>
    </div>
</template>

<style scoped>
.trust-page {
    max-width: 860px;
    margin: 0 auto;
    padding: 24px 16px 48px;
}

.page-header {
    margin-bottom: 20px;
}

/* Song：资历副轴链接条 —— 提示用户另有 Lv 体系 */
.exp-axis-link {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 10px 14px;
    margin-bottom: 16px;
    border: 1px dashed var(--el-border-color);
    border-radius: 8px;
    background: var(--el-fill-color-light);
    cursor: pointer;
    transition: border-color 0.2s;
}
.exp-axis-link:hover {
    border-color: var(--el-color-primary);
}
.exp-axis-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    font-weight: 600;
}
.exp-axis-badge {
    font-size: 13px;
    font-weight: 700;
    color: var(--el-text-color-primary);
    padding: 2px 8px;
    border-radius: 6px;
    background: var(--el-fill-color);
}
.exp-axis-exp {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}
.exp-axis-go {
    margin-left: auto;
    font-size: 12px;
    color: var(--el-color-primary);
    font-weight: 600;
}

.page-title {
    font-size: 24px;
    font-weight: 700;
    margin: 0 0 8px;
}

.page-desc {
    color: var(--el-text-color-secondary);
    font-size: 14px;
    line-height: 1.6;
    margin: 0;
}

.current-card {
    margin-bottom: 16px;
}

.current-row {
    display: flex;
    gap: 20px;
    align-items: center;
}

.level-chip {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 16px 20px;
    border-radius: 12px;
    color: #fff;
    min-width: 90px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.chip-label {
    font-size: 22px;
    font-weight: 800;
    line-height: 1;
}

.chip-name {
    font-size: 13px;
    margin-top: 4px;
    opacity: 0.95;
}

.current-meta {
    flex: 1;
}

.meta-title {
    font-size: 18px;
    font-weight: 700;
}

.meta-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin: 4px 0 12px;
}

.meta-progress {
    max-width: 420px;
}

.progress-text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.metrics-card,
.roadmap-card {
    margin-bottom: 16px;
}

.card-title {
    font-weight: 600;
}

.metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 16px;
}

.metric {
    text-align: center;
    padding: 12px 8px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
}

.metric-value {
    font-size: 22px;
    font-weight: 700;
    color: var(--el-color-primary);
}

.metric-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
}

.roadmap {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.roadmap-item {
    padding: 14px 16px;
    border-radius: 10px;
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-blank);
    transition: all 0.2s;
}

.roadmap-item.achieved {
    border-color: var(--el-color-success-light-5);
    background: var(--el-color-success-light-9);
}

.roadmap-item.active {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.roadmap-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;
}

.roadmap-chip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 2px 8px;
    border-radius: 6px;
    color: #fff;
    font-size: 12px;
    font-weight: 700;
}

.roadmap-name {
    font-weight: 600;
}

.roadmap-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin-bottom: 8px;
}

.roadmap-priv {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.priv-tag {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    background: var(--el-fill-color);
    color: var(--el-text-color-regular);
}
</style>
