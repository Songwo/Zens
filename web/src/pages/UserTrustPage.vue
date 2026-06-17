<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { trustLevelApi, type TrustInfo, type TrustEvent } from '@/api/trustLevel'
import { levelApi, type LevelInfo } from '@/api/level'
import { levelTitle } from '@/utils/levelPrivileges'
import { TRUST_LEVELS, trustLevelColor } from '@/utils/trustLevel'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { ArrowRight, Check, Lock, Trophy, Clock, ArrowUp, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const info = ref<TrustInfo | null>(null)
const levelInfo = ref<LevelInfo | null>(null)
const events = ref<TrustEvent[]>([])
const eventsLoading = ref(false)

const currentLevel = computed(() => info.value?.trustLevel ?? userStore.userInfo?.trustLevel ?? 0)
const metrics = computed(() => info.value?.metrics)
const spec = computed(() => TRUST_LEVELS[currentLevel.value])
const nextSpec = computed(() => (currentLevel.value < 4 ? TRUST_LEVELS[currentLevel.value + 1] : null))
const readMinutes = computed(() => Math.round((metrics.value?.readTimeSec ?? 0) / 60))
const expLevel = computed(() => levelInfo.value?.level ?? userStore.userInfo?.level ?? 1)

// Song：综合进度（用 4 项核心指标的平均完成度估算）
const overallProgress = computed(() => {
    if (!metrics.value || currentLevel.value >= 4) return 100
    const m = metrics.value
    const next = currentLevel.value + 1
    const targets = next >= 3
        ? { days: 50, posts: 500, likes: 10, given: 10 }
        : next >= 2
        ? { days: 7, posts: 60, likes: 1, given: 0 }
        : { days: 1, posts: 15, likes: 0, given: 0 }
    const items = [
        Math.min(100, (m.daysVisitedRecent / targets.days) * 100),
        Math.min(100, (m.postsReadRecent / targets.posts) * 100),
        Math.min(100, (m.likesReceived / Math.max(targets.likes, 1)) * 100),
        Math.min(100, (m.likesGiven / Math.max(targets.given, 1)) * 100),
    ]
    return Math.round(items.reduce((a, b) => a + b, 0) / items.length)
})

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

const fetchEvents = async () => {
    eventsLoading.value = true
    try {
        const res = await trustLevelApi.myEvents(1, 50)
        events.value = res.data || []
    } catch {
        // 静默失败，变更历史非核心功能
    } finally {
        eventsLoading.value = false
    }
}

/** 格式化时间 */
const formatTime = (iso: string) => {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
    fetchInfo()
    fetchEvents()
})
</script>

<template>
    <MainLayout>
        <div class="trust-center" v-loading="loading">
            <PageBackButton class="back-btn" fallback="/me" />

            <!-- ============ 1. 身份卡片（核心视觉） ============ -->
            <div
                class="identity-card"
                :style="{ '--identity-color': trustLevelColor(currentLevel) }"
            >
                <div class="identity-bg-orb orb-1"></div>
                <div class="identity-bg-orb orb-2"></div>

                <div class="identity-content">
                    <!-- 左：等级徽章（光环效果） -->
                    <div class="badge-wrap">
                        <div class="badge-ring"></div>
                        <div class="badge-ring badge-ring-2"></div>
                        <div class="badge-core">
                            <span class="badge-tl">TL{{ currentLevel }}</span>
                            <span class="badge-label">{{ spec?.label }}</span>
                        </div>
                    </div>

                    <!-- 右：身份信息 + 进度 -->
                    <div class="identity-meta">
                        <div class="meta-greeting">
                            你好，{{ userStore.userInfo?.nickname || '同学' }}
                        </div>
                        <h1 class="meta-title">
                            你是社区{{ spec?.label }}
                            <span v-if="currentLevel >= 3" class="meta-honor">🏆</span>
                        </h1>
                        <p class="meta-desc">{{ spec?.description }}</p>

                        <!-- 升级进度 -->
                        <div class="meta-progress" v-if="currentLevel < 4">
                            <div class="progress-head">
                                <span class="progress-label">距 {{ nextSpec?.label }} 还有</span>
                                <span class="progress-pct">{{ overallProgress }}%</span>
                            </div>
                            <div class="progress-track">
                                <div
                                    class="progress-fill"
                                    :style="{ width: overallProgress + '%' }"
                                ></div>
                            </div>
                            <div class="progress-hint">
                                多逛帖子、多阅读，系统会自动为你晋升
                            </div>
                        </div>
                        <div class="meta-progress meta-max" v-else>
                            <el-icon><Trophy /></el-icon>
                            <span>已达到最高信任等级，社区领袖 🎉</span>
                        </div>

                        <!-- 资历副轴 -->
                        <div class="meta-sub" @click="router.push('/level')">
                            <span class="sub-label">资历等级</span>
                            <span class="sub-value">Lv.{{ expLevel }} · {{ levelTitle(expLevel) }}</span>
                            <el-icon class="sub-arrow"><ArrowRight /></el-icon>
                        </div>
                    </div>
                </div>
            </div>

            <!-- ============ 2. 我的特权（卡片网格） ============ -->
            <section class="section">
                <div class="section-head">
                    <h2 class="section-title">我的特权</h2>
                    <span class="section-sub">TL{{ currentLevel }} 已解锁</span>
                </div>
                <div class="priv-grid">
                    <div
                        v-for="(p, i) in (spec?.privileges || [])"
                        :key="i"
                        class="priv-card"
                    >
                        <div class="priv-check"><el-icon><Check /></el-icon></div>
                        <span class="priv-text">{{ p }}</span>
                    </div>
                </div>

                <!-- 下一级预告 -->
                <div v-if="nextSpec" class="next-preview">
                    <div class="next-head">
                        <el-icon><Lock /></el-icon>
                        <span>升级到 TL{{ nextSpec.level }} · {{ nextSpec.label }} 后解锁</span>
                    </div>
                    <div class="next-privs">
                        <span v-for="(p, i) in nextSpec.privileges" :key="i" class="next-priv">{{ p }}</span>
                    </div>
                </div>
            </section>

            <!-- ============ 3. 行为雷达（数据可视化） ============ -->
            <section class="section">
                <div class="section-head">
                    <h2 class="section-title">行为画像</h2>
                    <span class="section-sub">你的社区活跃度</span>
                </div>
                <div class="metrics-grid">
                    <div class="metric-card metric-primary">
                        <div class="metric-value">{{ metrics?.daysVisited ?? 0 }}</div>
                        <div class="metric-label">累计访问<span>天</span></div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value">{{ readMinutes }}</div>
                        <div class="metric-label">阅读时长<span>分钟</span></div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value">{{ metrics?.postsReadRecent ?? 0 }}</div>
                        <div class="metric-label">近期阅读<span>帖</span></div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value">{{ metrics?.likesReceived ?? 0 }}</div>
                        <div class="metric-label">收到点赞</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value">{{ metrics?.likesGiven ?? 0 }}</div>
                        <div class="metric-label">给出点赞</div>
                    </div>
                    <div class="metric-card">
                        <div class="metric-value">{{ metrics?.postsCreated ?? 0 }}</div>
                        <div class="metric-label">发布帖子</div>
                    </div>
                </div>
            </section>

            <!-- ============ 4. 等级路线（横向时间轴） ============ -->
            <section class="section">
                <div class="section-head">
                    <h2 class="section-title">等级路线</h2>
                    <span class="section-sub">TL0 → TL4</span>
                </div>
                <div class="timeline">
                    <div class="timeline-line"></div>
                    <div
                        v-for="s in TRUST_LEVELS"
                        :key="s.level"
                        class="timeline-step"
                        :class="{
                            achieved: s.level <= currentLevel,
                            current: s.level === currentLevel,
                        }"
                    >
                        <div class="step-dot" :style="{ '--dot-color': s.color }">
                            <span v-if="s.level <= currentLevel">✓</span>
                            <span v-else>{{ s.level }}</span>
                        </div>
                        <div class="step-name">{{ s.label }}</div>
                    </div>
                </div>
            </section>

            <!-- ============ 5. 变更历史 ============ -->
            <section class="section">
                <div class="section-head">
                    <h2 class="section-title">变更历史</h2>
                    <span class="section-sub">
                        <el-icon><Clock /></el-icon>
                        {{ events.length ? `${events.length} 条记录` : '' }}
                    </span>
                </div>

                <div v-if="eventsLoading" class="events-loading">
                    <el-icon class="is-loading"><Clock /></el-icon>
                    <span>加载中…</span>
                </div>

                <div v-else-if="!events.length" class="events-empty">
                    <div class="empty-icon">📋</div>
                    <div class="empty-text">暂无变更记录</div>
                    <div class="empty-hint">活跃参与社区，系统会自动评估你的信任等级</div>
                </div>

                <div v-else class="events-list">
                    <div
                        v-for="ev in events"
                        :key="ev.id"
                        class="event-card"
                    >
                        <div class="event-left">
                            <div
                                class="event-direction"
                                :class="ev.newLevel > ev.oldLevel ? 'is-up' : 'is-down'"
                            >
                                <el-icon v-if="ev.newLevel > ev.oldLevel"><ArrowUp /></el-icon>
                                <el-icon v-else><ArrowDown /></el-icon>
                            </div>
                            <div class="event-levels">
                                <span
                                    class="event-level old"
                                    :style="{ '--lv-color': trustLevelColor(ev.oldLevel) }"
                                >TL{{ ev.oldLevel }}</span>
                                <span class="event-arrow">→</span>
                                <span
                                    class="event-level new"
                                    :style="{ '--lv-color': trustLevelColor(ev.newLevel) }"
                                >TL{{ ev.newLevel }}</span>
                            </div>
                        </div>
                        <div class="event-right">
                            <div class="event-reason">{{ ev.reason || '系统自动评估' }}</div>
                            <div class="event-time">{{ formatTime(ev.createTime) }}</div>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    </MainLayout>
</template>

<style scoped>
.trust-center {
    max-width: min(100%, var(--cp-profile-page-width, 1080px));
    margin: 0 auto;
    padding: 32px 16px 60px;
}

.back-btn {
    margin-bottom: 16px;
}

/* ============ 身份卡片 ============ */
.identity-card {
    position: relative;
    overflow: hidden;
    border-radius: 24px;
    padding: 36px 32px;
    margin-bottom: 32px;
    background: linear-gradient(135deg, color-mix(in srgb, var(--identity-color) 14%, var(--el-bg-color-overlay)) 0%, var(--el-bg-color-overlay) 60%);
    border: 1px solid color-mix(in srgb, var(--identity-color) 22%, var(--el-border-color-lighter));
}

.identity-bg-orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(48px);
    opacity: 0.32;
    pointer-events: none;
}
.orb-1 {
    width: 220px;
    height: 220px;
    background: var(--identity-color);
    top: -80px;
    right: -60px;
}
.orb-2 {
    width: 160px;
    height: 160px;
    background: var(--identity-color);
    bottom: -70px;
    left: 20%;
    opacity: 0.18;
}

.identity-content {
    position: relative;
    z-index: 1;
    display: flex;
    gap: 28px;
    align-items: center;
}

/* 徽章光环 */
.badge-wrap {
    position: relative;
    width: 120px;
    height: 120px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
}
.badge-ring,
.badge-ring-2 {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    border: 2px solid var(--identity-color);
    opacity: 0.25;
}
.badge-ring-2 {
    inset: -12px;
    opacity: 0.12;
}
.badge-core {
    width: 96px;
    height: 96px;
    border-radius: 50%;
    background: var(--identity-color);
    box-shadow: 0 12px 32px color-mix(in srgb, var(--identity-color) 45%, transparent);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #fff;
}
.badge-tl {
    font-size: 28px;
    font-weight: 800;
    line-height: 1;
}
.badge-label {
    font-size: 12px;
    margin-top: 4px;
    opacity: 0.95;
}

.identity-meta {
    flex: 1;
    min-width: 0;
}
.meta-greeting {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin-bottom: 2px;
}
.meta-title {
    font-size: 22px;
    font-weight: 800;
    color: var(--el-text-color-primary);
    margin: 0 0 6px;
    line-height: 1.3;
}
.meta-honor {
    font-size: 18px;
}
.meta-desc {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
    margin: 0 0 16px;
}

.meta-progress {
    background: var(--el-fill-color-light);
    border-radius: 12px;
    padding: 12px 14px;
    margin-bottom: 10px;
}
.progress-head {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 8px;
}
.progress-label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
}
.progress-pct {
    font-size: 18px;
    font-weight: 800;
    color: var(--identity-color);
}
.progress-track {
    height: 8px;
    background: var(--el-fill-color-dark);
    border-radius: 4px;
    overflow: hidden;
}
.progress-fill {
    height: 100%;
    background: linear-gradient(90deg, var(--identity-color), color-mix(in srgb, var(--identity-color) 70%, #fff));
    border-radius: 4px;
    transition: width 0.6s ease;
}
.progress-hint {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-top: 8px;
}
.meta-max {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--identity-color);
    font-weight: 600;
}

.meta-sub {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    padding: 4px 0;
    transition: color 0.2s;
}
.meta-sub:hover {
    color: var(--el-color-primary);
}
.sub-label {
    opacity: 0.7;
}
.sub-value {
    font-weight: 600;
}
.sub-arrow {
    font-size: 12px;
}

/* ============ 通用 section ============ */
.section {
    margin-bottom: 32px;
}
.section-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 16px;
}
.section-title {
    font-size: 17px;
    font-weight: 800;
    color: var(--el-text-color-primary);
    margin: 0;
}
.section-sub {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
}

/* ============ 特权卡片网格 ============ */
.priv-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 12px;
    margin-bottom: 12px;
}
.priv-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    border-radius: 12px;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-lighter);
    transition: all 0.2s;
}
.priv-card:hover {
    border-color: var(--identity-color);
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}
.priv-check {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--identity-color) 16%, transparent);
    color: var(--identity-color);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
.priv-text {
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
}

/* 下一级预告 */
.next-preview {
    padding: 14px 16px;
    border-radius: 12px;
    border: 1px dashed var(--el-border-color);
    background: var(--el-fill-color-light);
}
.next-head {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin-bottom: 8px;
}
.next-privs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}
.next-priv {
    font-size: 12px;
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--el-fill-color);
    color: var(--el-text-color-regular);
}

/* ============ 行为画像 ============ */
.metrics-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
}
.metric-card {
    padding: 18px 14px;
    border-radius: 14px;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-lighter);
    text-align: center;
    transition: all 0.2s;
}
.metric-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.05);
}
.metric-primary {
    background: linear-gradient(135deg, color-mix(in srgb, var(--identity-color) 10%, var(--el-bg-color-overlay)), var(--el-bg-color-overlay));
    border-color: color-mix(in srgb, var(--identity-color) 25%, var(--el-border-color-lighter));
}
.metric-value {
    font-size: 26px;
    font-weight: 800;
    color: var(--el-text-color-primary);
    line-height: 1.1;
}
.metric-primary .metric-value {
    color: var(--identity-color);
}
.metric-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 6px;
}
.metric-label span {
    margin-left: 2px;
    color: var(--el-text-color-placeholder);
    font-size: 11px;
}

/* ============ 时间轴路线 ============ */
.timeline {
    position: relative;
    display: flex;
    justify-content: space-between;
    padding: 12px 8px 0;
}
.timeline-line {
    position: absolute;
    top: 24px;
    left: 12%;
    right: 12%;
    height: 2px;
    background: var(--el-border-color);
    z-index: 0;
}
.timeline-step {
    position: relative;
    z-index: 1;
    text-align: center;
    flex: 1;
}
.step-dot {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    margin: 0 auto 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    background: var(--el-fill-color-dark);
    color: var(--el-text-color-placeholder);
    border: 3px solid var(--el-bg-color);
}
.timeline-step.achieved .step-dot {
    background: var(--dot-color);
    color: #fff;
}
.timeline-step.current .step-dot {
    transform: scale(1.18);
    box-shadow: 0 0 0 4px color-mix(in srgb, var(--dot-color) 25%, transparent);
}
.step-name {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}
.timeline-step.current .step-name,
.timeline-step.achieved .step-name {
    color: var(--el-text-color-primary);
    font-weight: 600;
}

/* ============ 变更历史 ============ */
.events-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 32px;
    color: var(--el-text-color-secondary);
    font-size: 14px;
}

.events-empty {
    text-align: center;
    padding: 40px 20px;
}
.empty-icon {
    font-size: 36px;
    margin-bottom: 10px;
}
.empty-text {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    margin-bottom: 4px;
}
.empty-hint {
    font-size: 13px;
    color: var(--el-text-color-placeholder);
}

.events-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}
.event-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 14px 18px;
    border-radius: 14px;
    background: var(--el-bg-color-overlay);
    border: 1px solid var(--el-border-color-lighter);
    transition: all 0.2s;
}
.event-card:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}

.event-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
}
.event-direction {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
}
.event-direction.is-up {
    background: color-mix(in srgb, #67c23a 14%, transparent);
    color: #67c23a;
}
.event-direction.is-down {
    background: color-mix(in srgb, #e6a23c 14%, transparent);
    color: #e6a23c;
}
.event-levels {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 700;
    font-size: 14px;
}
.event-level {
    padding: 2px 8px;
    border-radius: 6px;
    background: color-mix(in srgb, var(--lv-color) 12%, transparent);
    color: var(--lv-color);
}
.event-level.new {
    font-size: 15px;
}
.event-arrow {
    color: var(--el-text-color-placeholder);
    font-size: 13px;
}

.event-right {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}
.event-reason {
    font-size: 14px;
    color: var(--el-text-color-primary);
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.event-time {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
}

/* ============ 响应式 ============ */
@media (max-width: 640px) {
    .identity-content {
        flex-direction: column;
        text-align: center;
    }
    .badge-wrap {
        margin: 0 auto;
    }
    .meta-greeting,
    .meta-title,
    .meta-desc {
        text-align: center;
    }
    .metrics-grid {
        grid-template-columns: repeat(2, 1fr);
    }
    .priv-grid {
        grid-template-columns: 1fr;
    }
    .event-card {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
    }
}
</style>
