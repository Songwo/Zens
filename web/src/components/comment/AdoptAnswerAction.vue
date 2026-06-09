<template>
    <!-- 采纳按钮：作者、版主、管理员可见（黄色主题） -->
    <div v-if="canAdopt && !isAdopted" class="adopt-action">
        <el-button
            class="accept-answer-btn"
            size="small"
            round
            :icon="Check"
            @click="handleAdopt"
            :loading="adopting"
        >
            采纳为最佳答案
        </el-button>
    </div>

    <!-- 已采纳：轻量操作条（“最佳答案”徽章由评论项统一展示，避免重复） -->
    <div v-else-if="isAdopted" class="accepted-action-bar">
        <span class="accepted-reward">采纳奖励 +15 声望 · +20 经验</span>

        <!-- 取消采纳：作者、管理员可操作，文字按钮不刺眼 -->
        <button
            v-if="canCancel"
            type="button"
            class="cancel-accept-btn"
            :disabled="canceling"
            @click="handleCancelAdoption"
        >
            {{ canceling ? '取消中…' : '取消采纳' }}
        </button>
    </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { answerAdoptionApi } from '@/api/enhancement'
import { useUserStore } from '@/store/user'

const props = defineProps<{
    postId: string
    postAuthorId: string
    commentId: string
    commentAuthorId: string
    isAdopted: boolean
    hasAdoption: boolean
}>()

const emit = defineEmits<{
    adopted: []
    canceled: []
}>()

const userStore = useUserStore()
const adopting = ref(false)
const canceling = ref(false)

/**
 * 判断当前用户是否可以采纳答案
 * 规则：
 * 1. 必须登录
 * 2. 帖子还没有采纳答案
 * 3. 满足以下任一条件：
 *    - 是帖子作者
 *    - 是管理员
 *    - 是版主
 * 4. 不能采纳自己的回答
 */
const canAdopt = computed(() => {
    // 必须登录
    if (!userStore.userId) {
        return false
    }

    // 帖子已有采纳答案
    if (props.hasAdoption) {
        return false
    }

    // 不能采纳自己的回答
    if (userStore.userId === props.commentAuthorId) {
        return false
    }

    // 权限检查：作者 OR 管理员 OR 版主
    const isAuthor = userStore.userId === props.postAuthorId

    // 从 userInfo 中读取角色信息
    const userInfo = userStore.userInfo
    const roles = userInfo?.roles || userInfo?.role || []
    const rolesStr = Array.isArray(roles) ? roles.join(',') : String(roles)

    const isAdmin = rolesStr.includes('ADMIN') || rolesStr.includes('admin')
    const isModerator = rolesStr.includes('MODERATOR') || rolesStr.includes('moderator') || rolesStr.includes('版主')

    return isAuthor || isAdmin || isModerator
})

/**
 * 判断当前用户是否可以取消采纳
 * 规则：
 * 1. 回答已被采纳
 * 2. 满足以下任一条件：
 *    - 是帖子作者
 *    - 是管理员
 */
const canCancel = computed(() => {
    if (!userStore.userId) {
        return false
    }

    const isAuthor = userStore.userId === props.postAuthorId

    // 从 userInfo 中读取角色信息
    const userInfo = userStore.userInfo
    const roles = userInfo?.roles || userInfo?.role || []
    const rolesStr = Array.isArray(roles) ? roles.join(',') : String(roles)

    const isAdmin = rolesStr.includes('ADMIN') || rolesStr.includes('admin')

    return isAuthor || isAdmin
})

/**
 * 采纳答案
 */
const handleAdopt = async () => {
    try {
        await ElMessageBox.confirm(
            '确认采纳该回答为最佳答案吗？被采纳者将获得 +15 声望和 +20 经验。',
            '采纳确认',
            {
                confirmButtonText: '确认采纳',
                cancelButtonText: '取消',
                type: 'warning',
            }
        )

        adopting.value = true

        await answerAdoptionApi.adopt(props.postId, props.commentId)

        ElMessage.success('采纳成功！')
        emit('adopted')
    } catch (error: any) {
        if (error !== 'cancel') {
            const msg = error?.response?.data?.message || error?.message || '采纳失败'
            ElMessage.error(msg)
        }
    } finally {
        adopting.value = false
    }
}

/**
 * 取消采纳
 */
const handleCancelAdoption = async () => {
    try {
        await ElMessageBox.confirm(
            '确认取消采纳吗？被采纳者将失去相应的声望和经验。',
            '取消采纳确认',
            {
                confirmButtonText: '确认取消',
                cancelButtonText: '返回',
                type: 'warning',
            }
        )

        canceling.value = true

        await answerAdoptionApi.cancel(props.postId)

        ElMessage.success('已取消采纳')
        emit('canceled')
    } catch (error: any) {
        if (error !== 'cancel') {
            const msg = error?.response?.data?.message || error?.message || '取消采纳失败'
            ElMessage.error(msg)
        }
    } finally {
        canceling.value = false
    }
}
</script>

<style scoped lang="scss">
.adopt-action {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);
}

/* 采纳按钮：黄色主题（复用 el-button CSS 变量，保留 loading/disabled 行为） */
.accept-answer-btn {
    --el-button-bg-color: var(--accept-bg);
    --el-button-border-color: var(--accept-primary);
    --el-button-text-color: var(--accept-text);
    --el-button-hover-bg-color: var(--accept-primary);
    --el-button-hover-border-color: var(--accept-primary);
    --el-button-hover-text-color: #fff;
    --el-button-active-bg-color: var(--accept-primary-hover);
    --el-button-active-border-color: var(--accept-primary-hover);
    --el-button-active-text-color: #fff;
    font-weight: 600;
}

/* 已采纳：轻量操作条，不使用大块背景 */
.accepted-action-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 12px;
}

.accepted-reward {
    flex: 1;
    font-size: 13px;
    color: var(--accept-text);
    font-weight: 500;
}

/* 取消采纳：文字按钮，不刺眼 */
.cancel-accept-btn {
    border: none;
    background: transparent;
    color: var(--accept-action);
    cursor: pointer;
    font-size: 13px;
    padding: 0;
    transition: color 0.2s ease;
}

.cancel-accept-btn:hover:not(:disabled) {
    color: var(--accept-primary-hover);
    text-decoration: underline;
}

.cancel-accept-btn:disabled {
    cursor: not-allowed;
    opacity: 0.6;
}
</style>
