<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { trustLevelApi, type TrustInfo } from '@/api/trustLevel'
import { TRUST_LEVELS, trustLevelColor, trustLevelLabel } from '@/utils/trustLevel'
import { useUserStore } from '@/store/user'
import { Check, Close, Lightning, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const info = ref<TrustInfo | null>(null)

const currentLevel = computed(() => info.value?.trustLevel ?? userStore.userInfo?.trustLevel ?? 0)
const metrics = computed(() => info.value?.metrics)
const isRestricted = computed(() => currentLevel.value < 1)
const readMinutes = computed(() => Math.round((metrics.value?.readTimeSec ?? 0) / 60))

// Song：TL1 晋升条件进度（与后端 campus.trust-level.tl1 默认值对齐）
const tl1Progress = computed(() => {
    if (!metrics.value) return { items: [], percent: 0 }
    const m = metrics.value
    const items = [
        { label: '注册满 1 天', current: m.daysSinceRegister, target: 1, unit: '天' },
        { label: '进入不同帖子', current: m.postsEnteredRecent, target: 3, unit: '个' },
        { label: '阅读帖子', current: m.postsReadRecent, target: 15, unit: '篇' },
        { label: '累计阅读时长', current: readMinutes.value, target: 5, unit: '分钟' },
    ]
    const done = items.filter(i => i.current >= i.target).length
    return { items, percent: Math.round((done / items.length) * 100) }
})

const fetchInfo = async () => {
    loading.value = true
    try {
        const res = await trustLevelApi.info()
        info.value = res.data
    } catch (e: any) {
        // 未登录或获取失败，用 userStore 兜底
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    fetchInfo()
})
</script>

<template>
    <div class="onboarding-page" v-loading="loading">
        <!-- 顶部欢迎区 -->
        <div class="hero" :style="{ '--hero-color': trustLevelColor(currentLevel) }">
            <div class="hero-icon">
                <el-icon :size="48"><Lightning /></el-icon>
            </div>
            <div class="hero-text">
                <h1>欢迎来到 Campus Pulse！</h1>
                <p class="hero-sub">
                    你当前的信任等级是
                    <span class="hero-level" :style="{ background: trustLevelColor(currentLevel) }">
                        TL{{ currentLevel }} · {{ trustLevelLabel(currentLevel) }}
                    </span>
                </p>
            </div>
        </div>

        <!-- TL0 受限提示（仅受限用户显示） -->
        <el-alert
            v-if="isRestricted"
            class="restriction-alert"
            type="warning"
            :closable="false"
            show-icon
        >
            <template #title>你的账号当前处于新人期，部分功能受限</template>
            <template #default>
                <div class="restriction-detail">
                    <p><el-icon><Close /></el-icon> 暂不能在帖文中发布外链（防止垃圾内容）</p>
                    <p><el-icon><Close /></el-icon> 暂不能上传附件</p>
                    <p style="margin-top:8px;color:var(--el-color-success);">
                        <el-icon><Check /></el-icon> 完成下方任务，自动晋升 TL1 后即可解锁全部功能
                    </p>
                </div>
            </template>
        </el-alert>

        <!-- 升级任务清单（TL0 → TL1） -->
        <el-card v-if="currentLevel < 2" class="tasks-card" shadow="never">
            <template #header>
                <div class="card-head">
                    <span class="card-title">升级到 TL1 · 基础用户</span>
                    <el-progress
                        :percentage="tl1Progress.percent"
                        :stroke-width="10"
                        :color="trustLevelColor(1)"
                        class="head-progress"
                    />
                </div>
            </template>
            <div class="tasks-list">
                <div
                    v-for="(task, idx) in tl1Progress.items"
                    :key="idx"
                    class="task-item"
                    :class="{ done: task.current >= task.target }"
                >
                    <el-icon class="task-icon">
                        <Check v-if="task.current >= task.target" />
                        <Close v-else />
                    </el-icon>
                    <span class="task-label">{{ task.label }}</span>
                    <span class="task-progress">
                        <b>{{ Math.min(task.current, task.target) }}</b> / {{ task.target }} {{ task.unit }}
                    </span>
                </div>
            </div>
            <div class="tasks-tip">
                💡 这些任务会随着你正常使用社区自动完成——多逛帖子、多阅读，无需额外操作。
            </div>
        </el-card>

        <!-- 等级路线图（精简版） -->
        <el-card class="roadmap-card" shadow="never">
            <template #header><span class="card-title">信任等级路线</span></template>
            <div class="roadmap-row">
                <div
                    v-for="spec in TRUST_LEVELS"
                    :key="spec.level"
                    class="roadmap-step"
                    :class="{
                        current: spec.level === currentLevel,
                        achieved: spec.level <= currentLevel,
                        locked: spec.level > currentLevel
                    }"
                >
                    <div class="step-circle" :style="{ background: spec.level <= currentLevel ? spec.color : '' }">
                        {{ spec.level }}
                    </div>
                    <div class="step-label">{{ spec.label }}</div>
                </div>
            </div>
            <div class="roadmap-desc-list">
                <div v-for="spec in TRUST_LEVELS" :key="spec.level" class="desc-item">
                    <span class="desc-chip" :style="{ background: spec.color }">TL{{ spec.level }}</span>
                    <span class="desc-text">{{ spec.description }}</span>
                </div>
            </div>
        </el-card>

        <!-- 行动按钮 -->
        <div class="actions">
            <el-button type="primary" size="large" @click="router.push('/')">
                去逛逛社区 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
            <el-button size="large" @click="router.push('/trust')">查看信任详情</el-button>
        </div>
    </div>
</template>

<style scoped>
.onboarding-page {
    max-width: 720px;
    margin: 0 auto;
    padding: 32px 16px 64px;
}

.hero {
    text-align: center;
    padding: 40px 24px;
    border-radius: 16px;
    background: linear-gradient(135deg, var(--hero-color, var(--el-color-primary)) 0%, var(--el-fill-color-light) 100%);
    color: #fff;
    margin-bottom: 24px;
}
html.dark .hero {
    background: linear-gradient(135deg, var(--hero-color, var(--el-color-primary)) 0%, var(--el-fill-color-dark) 100%);
}

.hero-icon {
    margin-bottom: 12px;
}

.hero-text h1 {
    font-size: 26px;
    font-weight: 800;
    margin: 0 0 8px;
}

.hero-sub {
    font-size: 15px;
    opacity: 0.95;
    margin: 0;
}

.hero-level {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 6px;
    font-weight: 700;
    margin-left: 4px;
}

.restriction-alert {
    margin-bottom: 20px;
    border-radius: 10px;
}

.restriction-detail p {
    margin: 4px 0;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
}

.tasks-card,
.roadmap-card {
    margin-bottom: 20px;
}

.card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.card-title {
    font-weight: 700;
    font-size: 15px;
}

.head-progress {
    width: 160px;
}

.tasks-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.task-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
}

.task-item.done {
    background: var(--el-color-success-light-9);
}

.task-icon {
    font-size: 16px;
    color: var(--el-color-danger);
}

.task-item.done .task-icon {
    color: var(--el-color-success);
}

.task-label {
    flex: 1;
    font-size: 14px;
}

.task-progress {
    font-size: 13px;
    color: var(--el-text-color-secondary);
}

.task-progress b {
    color: var(--el-color-primary);
    font-size: 15px;
}

.tasks-tip {
    margin-top: 12px;
    padding: 10px 14px;
    background: var(--el-color-primary-light-9);
    border-radius: 8px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
}

.roadmap-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;
    position: relative;
}

.roadmap-row::before {
    content: '';
    position: absolute;
    top: 20px;
    left: 10%;
    right: 10%;
    height: 2px;
    background: var(--el-border-color);
    z-index: 0;
}

.roadmap-step {
    position: relative;
    z-index: 1;
    text-align: center;
    flex: 1;
}

.step-circle {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    margin: 0 auto 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    color: #fff;
    background: var(--el-fill-color);
    border: 3px solid var(--el-bg-color);
}

.roadmap-step.locked .step-circle {
    background: var(--el-fill-color);
    color: var(--el-text-color-placeholder);
}

.step-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.roadmap-step.current .step-label {
    color: var(--el-color-primary);
    font-weight: 700;
}

.roadmap-desc-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.desc-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
}

.desc-chip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 1px 8px;
    border-radius: 4px;
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    min-width: 36px;
}

.desc-text {
    color: var(--el-text-color-regular);
}

.actions {
    display: flex;
    gap: 12px;
    justify-content: center;
    margin-top: 8px;
}
</style>
