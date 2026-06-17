<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { trustLevelApi, type TrustEvent } from '@/api/trustLevel'
import { userApi } from '@/api/user'
import { TRUST_LEVELS, trustLevelColor, trustLevelLabel } from '@/utils/trustLevel'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Medal } from '@element-plus/icons-vue'

interface UserSearchResult {
    id: string
    username: string
    nickname?: string
    avatar?: string
    level?: number
    trustLevel?: number
}

// 搜索区
const searchKeyword = ref('')
const searchResults = ref<UserSearchResult[]>([])
const searching = ref(false)

// 重算
const recalculating = ref(false)

// 审计日志
const events = ref<TrustEvent[]>([])
const eventsLoading = ref(false)
const eventsPage = ref(1)

// 赋权对话框
const promoteVisible = ref(false)
const promoteTarget = ref<UserSearchResult | null>(null)
const promoteLevel = ref(0)
const promoteReason = ref('')
const promoting = ref(false)

const handleSearch = async () => {
    if (!searchKeyword.value.trim()) {
        ElMessage.warning('请输入用户名或昵称')
        return
    }
    searching.value = true
    try {
        const res = await userApi.searchUsers(searchKeyword.value.trim())
        searchResults.value = (res.data || []).map((u: any) => ({
            id: u.id,
            username: u.username,
            nickname: u.nickname,
            avatar: u.avatar,
            level: u.level,
            trustLevel: u.trustLevel ?? 0,
        }))
        if (searchResults.value.length === 0) {
            ElMessage.info('未找到匹配用户')
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '搜索失败')
    } finally {
        searching.value = false
    }
}

const openPromote = (user: UserSearchResult) => {
    promoteTarget.value = user
    promoteLevel.value = user.trustLevel ?? 0
    promoteReason.value = ''
    promoteVisible.value = true
}

const confirmPromote = async () => {
    if (!promoteTarget.value) return
    if (promoteLevel.value < 0 || promoteLevel.value > 4) {
        ElMessage.warning('信任等级必须在 0-4 之间')
        return
    }
    promoting.value = true
    try {
        await trustLevelApi.promote(promoteTarget.value.id, promoteLevel.value, promoteReason.value)
        ElMessage.success(`已将 ${promoteTarget.value.nickname || promoteTarget.value.username} 的信任等级设为 TL${promoteLevel.value}`)
        promoteVisible.value = false
        // 刷新搜索结果里的 TL
        if (promoteTarget.value) {
            promoteTarget.value.trustLevel = promoteLevel.value
        }
        fetchEvents()
    } catch (e: any) {
        ElMessage.error(e?.message || '设置失败')
    } finally {
        promoting.value = false
    }
}

const handleRecalculate = async () => {
    try {
        await ElMessageBox.confirm('将立即重算所有活跃用户的信任等级，可能影响部分用户的 TL（不活跃的 TL3 会降级）。确认继续？', '全量重算', {
            type: 'warning',
            confirmButtonText: '确认重算',
            cancelButtonText: '取消',
        })
    } catch {
        return
    }
    recalculating.value = true
    try {
        const res = await trustLevelApi.recalculate()
        ElMessage.success(`重算完成，共变更 ${res.data} 人`)
        fetchEvents()
        // 刷新搜索结果里的 TL（重新搜索）
        if (searchKeyword.value.trim()) {
            await handleSearch()
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '重算失败')
    } finally {
        recalculating.value = false
    }
}

const fetchEvents = async () => {
    eventsLoading.value = true
    try {
        const res = await trustLevelApi.events(eventsPage.value, 20)
        events.value = res.data || []
    } catch {
        events.value = []
    } finally {
        eventsLoading.value = false
    }
}

const formatDate = (s: string) => {
    if (!s) return ''
    try {
        return new Date(s).toLocaleString('zh-CN', { hour12: false })
    } catch {
        return s
    }
}

onMounted(() => {
    fetchEvents()
})
</script>

<template>
    <div class="trust-manage">
        <div class="page-head">
            <h2>信任等级管理</h2>
            <el-button type="primary" :icon="Refresh" :loading="recalculating" @click="handleRecalculate">
                全量重算
            </el-button>
        </div>
        <p class="page-desc">
            手动调整用户信任等级（主要用于授予 TL4 领袖），或触发全量重算让用户按行为指标自动晋升/降级。
        </p>

        <!-- 用户搜索 + 赋权 -->
        <el-card class="section-card" shadow="never">
            <template #header><span class="section-title">用户信任等级调整</span></template>
            <div class="search-row">
                <el-input
                    v-model="searchKeyword"
                    placeholder="输入用户名或昵称搜索"
                    :prefix-icon="Search"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                />
                <el-button type="primary" :loading="searching" @click="handleSearch">搜索</el-button>
            </div>

            <el-table
                v-if="searchResults.length > 0"
                :data="searchResults"
                stripe
                class="user-table"
            >
                <el-table-column label="用户" min-width="200">
                    <template #default="{ row }">
                        <div class="user-cell">
                            <el-avatar :size="32" :src="row.avatar">{{ (row.nickname || row.username || 'U').charAt(0) }}</el-avatar>
                            <div class="user-meta">
                                <div class="user-name">{{ row.nickname || row.username }}</div>
                                <div class="user-handle">@{{ row.username }}</div>
                            </div>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="资历等级" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" type="info">Lv.{{ row.level ?? 1 }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="信任等级" width="140" align="center">
                    <template #default="{ row }">
                        <el-tag
                            size="small"
                            effect="dark"
                            :style="{ background: trustLevelColor(row.trustLevel ?? 0) }"
                        >
                            TL{{ row.trustLevel ?? 0 }} · {{ trustLevelLabel(row.trustLevel ?? 0) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" type="primary" link :icon="Medal" @click="openPromote(row)">
                            调整
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div v-else-if="!searching" class="empty-tip">搜索用户后可在此调整其信任等级</div>
        </el-card>

        <!-- 审计日志 -->
        <el-card class="section-card" shadow="never">
            <template #header>
                <div class="events-head">
                    <span class="section-title">变更审计日志</span>
                    <el-button link :icon="Refresh" @click="fetchEvents" :loading="eventsLoading">刷新</el-button>
                </div>
            </template>
            <el-table :data="events" v-loading="eventsLoading" stripe size="small">
                <el-table-column label="时间" width="170">
                    <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
                </el-table-column>
                <el-table-column prop="userId" label="用户ID" width="180" show-overflow-tooltip />
                <el-table-column label="变更" width="140" align="center">
                    <template #default="{ row }">
                        <span class="change-cell">
                            <el-tag size="small" type="info">TL{{ row.oldLevel }}</el-tag>
                            <span class="change-arrow">→</span>
                            <el-tag size="small" type="success">TL{{ row.newLevel }}</el-tag>
                        </span>
                    </template>
                </el-table-column>
                <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
            </el-table>
            <div v-if="events.length === 0 && !eventsLoading" class="empty-tip">暂无变更记录</div>
        </el-card>

        <!-- 赋权对话框 -->
        <el-dialog v-model="promoteVisible" title="调整信任等级" width="460px">
            <div v-if="promoteTarget" class="promote-body">
                <div class="promote-user">
                    <el-avatar :size="40" :src="promoteTarget.avatar">
                        {{ (promoteTarget.nickname || promoteTarget.username || 'U').charAt(0) }}
                    </el-avatar>
                    <div>
                        <div class="promote-name">{{ promoteTarget.nickname || promoteTarget.username }}</div>
                        <div class="promote-handle">@{{ promoteTarget.username }} · 当前 TL{{ promoteTarget.trustLevel ?? 0 }}</div>
                    </div>
                </div>
                <el-form label-width="90px" class="promote-form">
                    <el-form-item label="新等级">
                        <el-select v-model="promoteLevel" class="promote-select">
                            <el-option
                                v-for="spec in TRUST_LEVELS"
                                :key="spec.level"
                                :value="spec.level"
                                :label="`TL${spec.level} · ${spec.label}`"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="原因">
                        <el-input
                            v-model="promoteReason"
                            type="textarea"
                            :rows="2"
                            placeholder="如：社区长期贡献者，授予领袖身份（可选）"
                        />
                    </el-form-item>
                </el-form>
                <div class="promote-hint">
                    <el-alert
                        v-if="promoteLevel === 4"
                        type="info"
                        :closable="false"
                        show-icon
                        title="TL4 领袖不会被自动降级，只能由管理员手动调整"
                    />
                </div>
            </div>
            <template #footer>
                <el-button @click="promoteVisible = false">取消</el-button>
                <el-button type="primary" :loading="promoting" @click="confirmPromote">确认调整</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.trust-manage {
    max-width: 1000px;
}

.page-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
}

.page-head h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
}

.page-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin: 0 0 20px;
}

.section-card {
    margin-bottom: 20px;
}

.section-title {
    font-weight: 700;
    font-size: 14px;
}

.search-row {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
}

.search-input {
    flex: 1;
}

.user-table {
    width: 100%;
}

.user-cell {
    display: flex;
    align-items: center;
    gap: 10px;
}

.user-name {
    font-weight: 600;
    font-size: 13px;
}

.user-handle {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.empty-tip {
    text-align: center;
    color: var(--el-text-color-placeholder);
    font-size: 13px;
    padding: 24px 0;
}

.events-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.change-cell {
    display: inline-flex;
    align-items: center;
    gap: 4px;
}

.change-arrow {
    color: var(--el-text-color-secondary);
}

.promote-body {
    padding: 0 4px;
}

.promote-user {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
}

.promote-name {
    font-weight: 700;
    font-size: 15px;
}

.promote-handle {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.promote-form {
    margin-top: 8px;
}

.promote-select {
    width: 100%;
}

.promote-hint {
    margin-top: 8px;
}
</style>
