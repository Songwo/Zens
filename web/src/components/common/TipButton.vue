<template>
    <div class="tip-button-wrapper">
        <el-button
            :icon="CoffeeCup"
            :loading="loading"
            size="small"
            type="warning"
            plain
            @click="showTipDialog = true"
        >
            打赏 {{ tipSum > 0 ? `(${tipSum})` : '' }}
        </el-button>

        <el-dialog
            v-model="showTipDialog"
            title="打赏"
            width="400px"
            :close-on-click-modal="false"
        >
            <el-form :model="form" label-width="80px">
                <el-form-item label="打赏积分">
                    <el-input-number
                        v-model="form.amount"
                        :min="1"
                        :max="100"
                        :step="5"
                    />
                    <div class="quick-amounts">
                        <el-button
                            v-for="amount in [5, 10, 20, 50]"
                            :key="amount"
                            size="small"
                            @click="form.amount = amount"
                        >
                            {{ amount }}
                        </el-button>
                    </div>
                </el-form-item>

                <el-form-item label="留言">
                    <el-input
                        v-model="form.message"
                        type="textarea"
                        :rows="3"
                        placeholder="写下你的感谢（可选）"
                        maxlength="200"
                        show-word-limit
                    />
                </el-form-item>

                <el-form-item>
                    <div class="tip-info">
                        <el-icon><InfoFilled /></el-icon>
                        你当前有 <strong>{{ userPoints }}</strong> 积分
                    </div>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="showTipDialog = false">取消</el-button>
                <el-button
                    type="primary"
                    :loading="submitting"
                    @click="handleTip"
                >
                    确认打赏
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CoffeeCup, InfoFilled } from '@element-plus/icons-vue'
import { tipApi } from '@/api/enhancement'
import { useUserStore } from '@/store/user'

const props = defineProps<{
    targetType: string
    targetId: string
}>()

const userStore = useUserStore()
const showTipDialog = ref(false)
const loading = ref(false)
const submitting = ref(false)
const tipSum = ref(0)
const userPoints = ref(0)

const form = ref({
    amount: 10,
    message: ''
})

const loadTipSum = async () => {
    try {
        loading.value = true
        const res = await tipApi.getSum(props.targetType, props.targetId)
        if (res.code === 200) {
            tipSum.value = res.data.totalAmount
        }
    } catch (error) {
        console.error('加载打赏统计失败', error)
    } finally {
        loading.value = false
    }
}

const handleTip = async () => {
    if (form.value.amount > userPoints.value) {
        ElMessage.warning('积分不足')
        return
    }

    try {
        submitting.value = true
        const res = await tipApi.send({
            targetType: props.targetType,
            targetId: props.targetId,
            amount: form.value.amount,
            message: form.value.message
        })

        if (res.code === 200) {
            ElMessage.success('打赏成功！')
            showTipDialog.value = false
            form.value.message = ''

            // 刷新积分和统计
            userPoints.value -= form.value.amount
            await loadTipSum()
        }
    } catch (error: any) {
        ElMessage.error(error.message || '打赏失败')
    } finally {
        submitting.value = false
    }
}

onMounted(() => {
    loadTipSum()
    userPoints.value = userStore.user?.points || 0
})
</script>

<style scoped lang="scss">
.tip-button-wrapper {
    display: inline-block;
}

.quick-amounts {
    display: flex;
    gap: 8px;
    margin-top: 8px;
}

.tip-info {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    font-size: 14px;
    color: var(--el-text-color-secondary);

    strong {
        color: var(--el-color-warning);
        font-weight: 600;
    }
}
</style>
