<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Delete, Edit, Refresh, Plus, Connection } from '@element-plus/icons-vue'
import { ssoApi, type SsoClientItem } from '@/api/sso'

type SsoPreset = {
    id: string
    clientId: string
    clientName: string
    redirectUris: string[]
    description: string
    logoUrl: string
}

const clients = ref<SsoClientItem[]>([])
const loading = ref(false)
const repairingPresetId = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref('')

const form = ref({
    clientId: '',
    clientName: '',
    redirectUri: '',
    description: '',
    logoUrl: '',
})

const newSecret = ref('')
const showSecretDialog = ref(false)

const cdkSsoPreset: SsoPreset = {
    id: 'cdk-airdrop',
    clientId: 'cdk-airdrop',
    clientName: 'CDK 空投台',
    redirectUris: [
        'https://cdk.allinsong.top/login/callback',
        'http://localhost:5174/login/callback',
        'http://127.0.0.1:5174/login/callback',
    ],
    description: 'ZensPulse 社区 CDK 节点空投平台',
    logoUrl: '',
}

const pointShopSsoPreset: SsoPreset = {
    id: 'zdc-shop',
    clientId: 'zdc-shop',
    clientName: 'Zens 积分商城',
    redirectUris: [
        'https://shop.allinsong.top/login/callback',
        'https://mall.allinsong.top/login/callback',
        'https://points.allinsong.top/login/callback',
        'https://zdc-shop.allinsong.top/login/callback',
        'http://localhost:3000/login/callback',
        'http://127.0.0.1:3000/login/callback',
        'http://localhost:3001/login/callback',
        'http://127.0.0.1:3001/login/callback',
    ],
    description: 'Zens 社区积分商城，使用主站账号单点登录并同步积分权益。',
    logoUrl: '/logo.png',
}

function presetToForm(preset: SsoPreset) {
    return {
        clientId: preset.clientId,
        clientName: preset.clientName,
        redirectUri: preset.redirectUris.join(', '),
        description: preset.description,
        logoUrl: preset.logoUrl,
    }
}

async function fetchClients() {
    const res = await ssoApi.listClients()
    return res.data || []
}

async function loadClients() {
    loading.value = true
    try {
        clients.value = await fetchClients()
    } catch (e: any) {
        ElMessage.error(e.message || '加载失败')
    } finally {
        loading.value = false
    }
}

function openCreateDialog(preset: SsoPreset = cdkSsoPreset) {
    dialogMode.value = 'create'
    editingId.value = ''
    form.value = presetToForm(preset)
    dialogVisible.value = true
}

function openEditDialog(client: SsoClientItem) {
    dialogMode.value = 'edit'
    editingId.value = client.id
    form.value = {
        clientId: client.clientId,
        clientName: client.clientName,
        redirectUri: client.redirectUri,
        description: client.description || '',
        logoUrl: client.logoUrl || '',
    }
    dialogVisible.value = true
}

function openPresetDialog(preset: SsoPreset) {
    const existing = clients.value.find(client => client.clientId === preset.clientId)
    if (existing) {
        openEditDialog(existing)
        return
    }
    openCreateDialog(preset)
}

async function submitForm() {
    if (!form.value.clientId || !form.value.clientName || !form.value.redirectUri) {
        ElMessage.warning('请填写必填项')
        return
    }
    try {
        if (dialogMode.value === 'create') {
            const res = await ssoApi.createClient(form.value)
            const created = res.data
            if (created?.clientSecret) {
                newSecret.value = created.clientSecret
                showSecretDialog.value = true
            }
            ElMessage.success('创建成功')
        } else {
            await ssoApi.updateClient(editingId.value, {
                clientName: form.value.clientName,
                redirectUri: form.value.redirectUri,
                description: form.value.description,
                logoUrl: form.value.logoUrl,
            })
            ElMessage.success('更新成功')
        }
        dialogVisible.value = false
        loadClients()
    } catch (e: any) {
        ElMessage.error(e.message || '操作失败')
    }
}

async function repairPresetClient(preset: SsoPreset) {
    repairingPresetId.value = preset.id
    try {
        if (preset.id === pointShopSsoPreset.id) {
            const res = await ssoApi.upsertPointShopClient()
            const repaired = res.data
            if (repaired?.clientSecret) {
                newSecret.value = repaired.clientSecret
                showSecretDialog.value = true
            }
            ElMessage.success(`${preset.clientName} SSO 已创建/修复并启用`)
        } else {
            const res = await ssoApi.createClient(presetToForm(preset))
            const created = res.data
            if (created?.clientSecret) {
                newSecret.value = created.clientSecret
                showSecretDialog.value = true
            }
            ElMessage.success(`${preset.clientName} SSO 已创建`)
        }

        await loadClients()
    } catch (e: any) {
        ElMessage.error(e.message || `${preset.clientName} SSO 修复失败`)
    } finally {
        repairingPresetId.value = ''
    }
}

async function handleToggle(client: SsoClientItem) {
    try {
        await ssoApi.toggleClient(client.id, !client.enabled)
        ElMessage.success(client.enabled ? '已禁用' : '已启用')
        loadClients()
    } catch (e: any) {
        ElMessage.error(e.message || '操作失败')
    }
}

async function handleDelete(client: SsoClientItem) {
    try {
        await ElMessageBox.confirm(`确定删除应用「${client.clientName}」？此操作不可撤销。`, '确认删除', {
            type: 'warning',
            confirmButtonText: '确认删除',
            cancelButtonText: '取消',
        })
        await ssoApi.deleteClient(client.id)
        ElMessage.success('已删除')
        loadClients()
    } catch (e: any) {
        if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
    }
}

async function handleResetSecret(client: SsoClientItem) {
    try {
        await ElMessageBox.confirm(`确定重置应用「${client.clientName}」的密钥？旧密钥将立即失效。`, '重置密钥', {
            type: 'warning',
            confirmButtonText: '确认重置',
            cancelButtonText: '取消',
        })
        const res = await ssoApi.resetSecret(client.id)
        newSecret.value = res.data?.clientSecret || ''
        showSecretDialog.value = true
        loadClients()
    } catch (e: any) {
        if (e !== 'cancel') ElMessage.error(e.message || '重置失败')
    }
}

function copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).then(() => {
        ElMessage.success('已复制到剪贴板')
    }).catch(() => {
        ElMessage.warning('复制失败，请手动复制')
    })
}

onMounted(loadClients)
</script>

<template>
    <div class="sso-manage">
        <div class="page-header">
            <div class="header-info">
                <h2>
                    <el-icon style="margin-right: 8px; vertical-align: middle;"><Connection /></el-icon>
                    SSO 应用管理
                </h2>
                <p class="header-desc">管理第三方应用的单点登录接入配置，允许外部应用使用社区账号快速登录。</p>
            </div>
            <div class="header-actions">
                <el-button
                    :icon="Refresh"
                    :loading="repairingPresetId === pointShopSsoPreset.id"
                    @click="repairPresetClient(pointShopSsoPreset)"
                >
                    一键修复积分商城 SSO
                </el-button>
                <el-button type="primary" :icon="Plus" @click="openCreateDialog()">新建应用</el-button>
            </div>
        </div>

        <div class="preset-panel">
            <div>
                <div class="preset-title">积分商城接入检查</div>
                <div class="preset-desc">
                    client_id 固定为 <code>zdc-shop</code>，已覆盖本地 3000/3001 与常用生产子域名回调地址。
                </div>
            </div>
            <el-button link type="primary" @click="openPresetDialog(pointShopSsoPreset)">查看预置配置</el-button>
        </div>

        <el-table :data="clients" v-loading="loading" stripe style="width: 100%; margin-top: 20px;">
            <el-table-column label="应用名称" min-width="160">
                <template #default="{ row }">
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <img
                            v-if="row.logoUrl"
                            :src="row.logoUrl"
                            style="width: 32px; height: 32px; border-radius: 6px; object-fit: cover;"
                        />
                        <div
                            v-else
                            style="width: 32px; height: 32px; border-radius: 6px; background: var(--el-color-primary, #F4B400); display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: 14px;"
                        >
                            {{ (row.clientName || '?')[0] }}
                        </div>
                        <div>
                            <div style="font-weight: 600;">{{ row.clientName }}</div>
                            <div style="font-size: 12px; color: var(--el-text-color-secondary);">{{ row.description || '—' }}</div>
                        </div>
                    </div>
                </template>
            </el-table-column>

            <el-table-column label="Client ID" min-width="140">
                <template #default="{ row }">
                    <code style="font-size: 13px; background: var(--el-fill-color-light); padding: 2px 8px; border-radius: 4px;">
                        {{ row.clientId }}
                    </code>
                </template>
            </el-table-column>

            <el-table-column label="密钥" min-width="140">
                <template #default="{ row }">
                    <span style="font-size: 13px; color: var(--el-text-color-secondary);">{{ row.clientSecretMasked }}</span>
                </template>
            </el-table-column>

            <el-table-column label="回调地址" min-width="200">
                <template #default="{ row }">
                    <div style="font-size: 13px; word-break: break-all; color: var(--el-text-color-regular);">{{ row.redirectUri }}</div>
                </template>
            </el-table-column>

            <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                    <el-switch
                        :model-value="row.enabled"
                        @change="() => handleToggle(row)"
                        active-text="启用"
                        inactive-text="禁用"
                        inline-prompt
                    />
                </template>
            </el-table-column>

            <el-table-column label="操作" width="200" align="center">
                <template #default="{ row }">
                    <el-button link type="primary" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
                    <el-button link type="warning" :icon="Refresh" @click="handleResetSecret(row)">重置密钥</el-button>
                    <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <div v-if="!loading && clients.length === 0" class="empty-state">
            <el-icon :size="48" color="var(--el-text-color-placeholder)"><Connection /></el-icon>
            <p>暂无 SSO 应用</p>
            <p style="font-size: 13px; color: var(--el-text-color-secondary);">点击「新建应用」接入第三方系统的单点登录</p>
        </div>

        <!-- 新建/编辑弹窗 -->
        <el-dialog
            :title="dialogMode === 'create' ? '新建 SSO 应用' : '编辑 SSO 应用'"
            v-model="dialogVisible"
            width="520px"
            destroy-on-close
        >
            <el-form :model="form" label-width="100px" style="padding: 10px 20px 0;">
                <el-form-item label="应用标识" required>
                    <el-input
                        v-model="form.clientId"
                        placeholder="如 cdk-airdrop（唯一标识，创建后不可更改）"
                        :disabled="dialogMode === 'edit'"
                    />
                </el-form-item>
                <el-form-item label="应用名称" required>
                    <el-input v-model="form.clientName" placeholder="如 CDK 空投台" />
                </el-form-item>
                <el-form-item label="回调地址" required>
                    <el-input v-model="form.redirectUri" placeholder="如 http://localhost:5174/login/callback" />
                    <div style="font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px;">
                        多个地址可用逗号分隔
                    </div>
                </el-form-item>
                <el-form-item label="描述">
                    <el-input v-model="form.description" type="textarea" :rows="2" placeholder="应用描述（可选）" />
                </el-form-item>
                <el-form-item label="Logo URL">
                    <el-input v-model="form.logoUrl" placeholder="应用 Logo 地址（可选）" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitForm">{{ dialogMode === 'create' ? '创建' : '保存' }}</el-button>
            </template>
        </el-dialog>

        <!-- 密钥展示弹窗 -->
        <el-dialog
            title="应用密钥"
            v-model="showSecretDialog"
            width="480px"
            :close-on-click-modal="false"
        >
            <el-alert type="warning" :closable="false" style="margin-bottom: 16px;">
                <template #title>
                    <strong>请立即保存此密钥！</strong> 弹窗关闭后将无法再次查看完整密钥。
                </template>
            </el-alert>
            <div style="background: var(--el-fill-color-light); border-radius: 8px; padding: 16px; display: flex; align-items: center; gap: 12px;">
                <code style="flex: 1; word-break: break-all; font-size: 14px;">{{ newSecret }}</code>
                <el-button type="primary" :icon="CopyDocument" size="small" @click="copyToClipboard(newSecret)">复制</el-button>
            </div>
            <template #footer>
                <el-button type="primary" @click="showSecretDialog = false">我已保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.sso-manage {
    padding: 4px;
}

.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
}

.page-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
    display: flex;
    align-items: center;
}

.header-desc {
    margin: 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
}

.header-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 10px;
}

.preset-panel {
    margin-top: 18px;
    padding: 14px 16px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 12px;
    background: linear-gradient(135deg, rgba(246, 168, 0, 0.08), rgba(255, 255, 255, 0.92));
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.preset-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--el-text-color-primary);
}

.preset-desc {
    margin-top: 4px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
}

.preset-desc code {
    padding: 1px 6px;
    border-radius: 5px;
    background: rgba(246, 168, 0, 0.12);
    color: #9a6700;
}

.empty-state {
    text-align: center;
    padding: 60px 20px;
    color: var(--el-text-color-placeholder);
}

.empty-state p {
    margin: 8px 0 0;
}

@media (max-width: 768px) {
    .page-header,
    .preset-panel {
        flex-direction: column;
        align-items: stretch;
    }

    .header-actions {
        justify-content: flex-start;
    }
}
</style>
