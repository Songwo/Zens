<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, UploadFilled } from '@element-plus/icons-vue'
import {
  supporterVoucherAdminApi,
  type SupporterVoucherImportResult,
  type SupporterVoucherInventory,
} from '@/api/supporterVoucherAdmin'

const inventory = ref<SupporterVoucherInventory[]>([])
const loading = ref(false)
const loadError = ref('')
const importing = ref(false)
const quota = ref<30 | 50>(30)
const rawCodes = ref('')
const lastResult = ref<SupporterVoucherImportResult | null>(null)

const normalizedCodes = computed(() => rawCodes.value
  .split(/\r?\n/)
  .map(code => code.trim())
  .filter(Boolean))
const uniqueCount = computed(() => new Set(normalizedCodes.value).size)
const duplicateInInput = computed(() => normalizedCodes.value.length - uniqueCount.value)
const tooMany = computed(() => normalizedCodes.value.length > 500)

const inventoryFor = (target: 30 | 50) => inventory.value.find(item => item.quota === target) || {
  quota: target,
  available: 0,
  assigned: 0,
  pendingGrants: 0,
}

const fetchInventory = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await supporterVoucherAdminApi.inventory()
    inventory.value = res.data || []
  } catch (error: any) {
    loadError.value = error?.message || '库存加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const importCodes = async () => {
  if (!normalizedCodes.value.length) {
    ElMessage.warning('请先粘贴兑换码，每行一个')
    return
  }
  if (tooMany.value) {
    ElMessage.warning('单次最多导入 500 个兑换码')
    return
  }

  await ElMessageBox.confirm(
    `确认向 ${quota.value} 额度库存导入 ${normalizedCodes.value.length} 个兑换码？导入后将自动补发等待中的权益。`,
    '确认导入',
    { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '再检查一下' },
  )

  importing.value = true
  try {
    const codes = [...normalizedCodes.value]
    const res = await supporterVoucherAdminApi.importCodes(quota.value, codes)
    lastResult.value = res.data
    rawCodes.value = ''
    ElMessage.success(`已导入 ${res.data?.imported || 0} 个兑换码`)
    await fetchInventory()
  } catch (error: any) {
    ElMessage.error(error?.message || '导入失败，请检查兑换码后重试')
  } finally {
    importing.value = false
  }
}

onMounted(fetchInventory)
</script>

<template>
  <div class="voucher-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">SUPPORTER OPERATIONS</p>
        <h1>支持者兑换码</h1>
        <p>维护公益站 30 / 50 额度库存。系统只展示库存数量，不回显已导入的真实兑换码。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchInventory">刷新库存</el-button>
    </header>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon class="state-alert">
      <template #title>库存暂时无法加载</template>
      <template #default>
        <span>{{ loadError }}</span>
        <el-button link type="primary" @click="fetchInventory">重新加载</el-button>
      </template>
    </el-alert>

    <div v-if="loading && !inventory.length" class="inventory-grid" aria-label="库存加载中">
      <el-skeleton v-for="item in 2" :key="item" animated :rows="3" class="inventory-card" />
    </div>
    <div v-else class="inventory-grid">
      <article v-for="target in ([30, 50] as const)" :key="target" class="inventory-card">
        <div class="quota-heading"><strong>{{ target }}</strong><span>公益站月额度</span></div>
        <dl>
          <div><dt>可用库存</dt><dd class="available">{{ inventoryFor(target).available }}</dd></div>
          <div><dt>已分配</dt><dd>{{ inventoryFor(target).assigned }}</dd></div>
          <div><dt>待发放</dt><dd :class="{ warning: inventoryFor(target).pendingGrants > 0 }">{{ inventoryFor(target).pendingGrants }}</dd></div>
        </dl>
      </article>
    </div>

    <section class="import-panel">
      <div class="section-heading">
        <div><h2>导入真实兑换码</h2><p>选择额度后粘贴兑换码，每行一个。请勿在备注、截图或工单中传递兑换码。</p></div>
        <el-segmented v-model="quota" :options="[30, 50]" />
      </div>

      <label for="voucher-codes">兑换码列表</label>
      <el-input
        id="voucher-codes"
        v-model="rawCodes"
        type="textarea"
        :rows="10"
        resize="vertical"
        maxlength="128500"
        autocomplete="off"
        spellcheck="false"
        placeholder="每行粘贴一个兑换码"
      />
      <div class="input-meta" aria-live="polite">
        <span>已识别 {{ normalizedCodes.length }} 条</span>
        <span v-if="duplicateInInput" class="warning">其中 {{ duplicateInInput }} 条在本次输入中重复</span>
        <span v-if="tooMany" class="danger">超过单次 500 条限制</span>
      </div>
      <el-button
        type="primary"
        :icon="UploadFilled"
        :loading="importing"
        :disabled="!normalizedCodes.length || tooMany"
        @click="importCodes"
      >确认导入 {{ quota }} 额度库存</el-button>
    </section>

    <el-alert v-if="lastResult" type="success" :closable="false" show-icon class="result-alert">
      <template #title>最近一次导入完成</template>
      <template #default>
        {{ lastResult.quota }} 额度：提交 {{ lastResult.submitted }}，成功 {{ lastResult.imported }}，重复 {{ lastResult.duplicates }}，自动补发 {{ lastResult.pendingIssued }}。
      </template>
    </el-alert>
  </div>
</template>

<style scoped>
.voucher-page { max-width: 1080px; padding: 8px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.page-header h1 { margin: 2px 0 8px; font-size: clamp(24px, 3vw, 34px); letter-spacing: -.03em; }
.page-header p { margin: 0; max-width: 680px; color: var(--el-text-color-secondary); line-height: 1.65; }
.eyebrow { font-size: 11px; font-weight: 800; letter-spacing: .14em; color: var(--el-color-primary) !important; }
.state-alert, .result-alert { margin-bottom: 20px; }
.inventory-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.inventory-card { padding: 22px; border: 1px solid var(--cp-border); border-radius: 14px; background: var(--cp-bg-card); }
.quota-heading { display: flex; align-items: baseline; gap: 10px; padding-bottom: 18px; border-bottom: 1px solid var(--cp-divider); }
.quota-heading strong { font-size: 38px; line-height: 1; color: var(--el-color-primary); }
.quota-heading span { color: var(--el-text-color-secondary); }
.inventory-card dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin: 18px 0 0; }
.inventory-card dl div { min-width: 0; }
.inventory-card dt { font-size: 12px; color: var(--el-text-color-secondary); }
.inventory-card dd { margin: 5px 0 0; font-size: 22px; font-weight: 800; }
.inventory-card dd.available { color: var(--el-color-success); }
.warning { color: var(--el-color-warning); }
.danger { color: var(--el-color-danger); }
.import-panel { padding: 24px; border: 1px solid var(--cp-border); border-radius: 14px; background: var(--cp-bg-card); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.section-heading h2 { margin: 0 0 7px; font-size: 20px; }
.section-heading p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.55; }
.import-panel label { display: block; margin-bottom: 8px; font-size: 13px; font-weight: 700; }
.input-meta { display: flex; flex-wrap: wrap; gap: 14px; min-height: 22px; margin: 8px 0 16px; font-size: 12px; color: var(--el-text-color-secondary); }
.result-alert { margin-top: 20px; }
:deep(.el-button:focus-visible), :deep(.el-textarea__inner:focus-visible) { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
@media (max-width: 720px) {
  .page-header, .section-heading { flex-direction: column; }
  .inventory-grid { grid-template-columns: 1fr; }
  .inventory-card dl { gap: 8px; }
  .import-panel { padding: 18px; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; } }
</style>
