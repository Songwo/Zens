<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ssoApi, type SsoClientPublicInfo } from '@/api/sso'

const route = useRoute()
const router = useRouter()

const clientId = computed(() => (route.query.client_id as string) || '')
const redirectUri = computed(() => (route.query.redirect_uri as string) || '')

const loading = ref(true)
const authorizing = ref(false)
const autoEntering = ref(false)
const error = ref('')
const appInfo = ref<SsoClientPublicInfo | null>(null)

onMounted(async () => {
    if (!clientId.value || !redirectUri.value) {
        error.value = '缺少必要参数 (client_id, redirect_uri)'
        loading.value = false
        return
    }

    // 检查登录状态
    const accessToken = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
    if (!accessToken) {
        // 未登录，跳转到登录页，登录完成后再回来
        const returnPath = `/sso/authorize?client_id=${encodeURIComponent(clientId.value)}&redirect_uri=${encodeURIComponent(redirectUri.value)}`
        router.replace({ path: '/auth', query: { type: 'login', redirect: returnPath } })
        return
    }

    // 加载应用信息
    try {
        const res = await ssoApi.getPublicClientInfo(clientId.value)
        appInfo.value = res.data
    } catch (e: any) {
        error.value = e.message || '应用信息加载失败'
        loading.value = false
        return
    }
    loading.value = false

    // 第一方可信客户端：已登录用户自动授权、跳过同意页（OAuth 第一方免同意惯例）。
    // 授权失败（如 redirect_uri 不匹配）会落回手动同意 UI。
    if (appInfo.value?.trusted) {
        autoEntering.value = true
        await handleAuthorize()
    }
})

async function handleAuthorize() {
    authorizing.value = true
    error.value = ''
    try {
        const res = await ssoApi.authorize({
            clientId: clientId.value,
            redirectUri: redirectUri.value,
        })
        const ssoToken = res.data?.ssoToken
        if (!ssoToken) {
            throw new Error('SSO Token 生成失败')
        }
        // 拼接回调地址
        const separator = redirectUri.value.includes('?') ? '&' : '?'
        const callbackUrl = `${redirectUri.value}${separator}sso_token=${encodeURIComponent(ssoToken)}`
        window.location.href = callbackUrl
    } catch (e: any) {
        error.value = e.message || '授权失败'
        authorizing.value = false
    }
}

function handleCancel() {
    // 取消授权，重定向回去并带上 error
    if (redirectUri.value) {
        const separator = redirectUri.value.includes('?') ? '&' : '?'
        window.location.href = `${redirectUri.value}${separator}error=access_denied`
    } else {
        router.push('/')
    }
}
</script>

<template>
    <div class="sso-authorize-layout">
        <div class="sso-authorize-card">
            <!-- Loading -->
            <div v-if="loading" class="sso-loading">
                <div class="spinner"></div>
                <p>正在加载应用信息...</p>
            </div>

            <!-- Error -->
            <div v-else-if="error && !appInfo" class="sso-error">
                <div class="error-icon">✕</div>
                <h3>授权失败</h3>
                <p>{{ error }}</p>
                <button class="sso-btn sso-btn--secondary" @click="router.push('/')">返回首页</button>
            </div>

            <!-- 第一方可信客户端：自动授权过渡态（无错误时不显示同意按钮） -->
            <div v-else-if="autoEntering && !error" class="sso-loading">
                <div class="spinner"></div>
                <p>正在进入 {{ appInfo?.clientName || '应用' }}…</p>
            </div>

            <!-- 授权确认 -->
            <div v-else-if="appInfo" class="sso-content">
                <div class="sso-brand">
                    <img src="/logo.png" alt="Zens" class="sso-logo" />
                    <span class="sso-arrow">→</span>
                    <div v-if="appInfo.logoUrl" class="sso-app-logo">
                        <img :src="appInfo.logoUrl" alt="" />
                    </div>
                    <div v-else class="sso-app-logo sso-app-logo--placeholder">
                        {{ (appInfo.clientName || '?')[0] }}
                    </div>
                </div>

                <h2>授权登录</h2>
                <p class="sso-desc">
                    <strong>{{ appInfo.clientName }}</strong> 请求使用你的 Zens 社区账号登录
                </p>

                <div class="sso-permissions">
                    <div class="sso-perm-title">该应用将获取你的以下信息：</div>
                    <ul>
                        <li>✓ 用户名和昵称</li>
                        <li>✓ 头像</li>
                        <li>✓ 邮箱</li>
                        <li>✓ 角色和等级</li>
                    </ul>
                </div>

                <div v-if="error" class="sso-inline-error">{{ error }}</div>

                <div class="sso-actions">
                    <button
                        class="sso-btn sso-btn--primary"
                        :disabled="authorizing"
                        @click="handleAuthorize"
                    >
                        {{ authorizing ? '授权中...' : '确认授权' }}
                    </button>
                    <button class="sso-btn sso-btn--secondary" :disabled="authorizing" @click="handleCancel">
                        拒绝
                    </button>
                </div>

                <p class="sso-footer">
                    授权后将跳转到 <code>{{ redirectUri }}</code>
                </p>
            </div>
        </div>
    </div>
</template>

<style scoped>
.sso-authorize-layout {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-bg-color-page, #F5F7FA);
    padding: 24px;
}

.sso-authorize-card {
    width: 100%;
    max-width: 440px;
    background: #fff;
    border-radius: 16px;
    box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
    padding: 40px 36px;
}

.sso-loading {
    text-align: center;
    padding: 40px 0;
}

.spinner {
    width: 36px;
    height: 36px;
    border: 3px solid #e8e8e8;
    border-top: 3px solid var(--el-color-primary, #409eff);
    border-radius: 50%;
    margin: 0 auto 16px;
    animation: spin 0.8s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

.sso-error {
    text-align: center;
    padding: 20px 0;
}

.error-icon {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: #fef2f2;
    color: #ef4444;
    font-size: 24px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 16px;
}

.sso-error h3 {
    margin: 0 0 8px;
    color: #1a1a1a;
}

.sso-error p {
    color: #666;
    margin: 0 0 24px;
    font-size: 14px;
}

.sso-content h2 {
    margin: 0 0 8px;
    font-size: 22px;
    text-align: center;
    color: #1a1a1a;
}

.sso-desc {
    text-align: center;
    color: #555;
    font-size: 15px;
    margin: 0 0 24px;
    line-height: 1.5;
}

.sso-brand {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    margin-bottom: 24px;
}

.sso-logo {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    object-fit: contain;
}

.sso-arrow {
    font-size: 24px;
    color: #999;
    font-weight: 300;
}

.sso-app-logo {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    overflow: hidden;
}

.sso-app-logo img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.sso-app-logo--placeholder {
    background: var(--el-color-primary, #F4B400);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 20px;
}

.sso-permissions {
    background: #f8fafc;
    border-radius: 10px;
    padding: 16px 20px;
    margin-bottom: 24px;
}

.sso-perm-title {
    font-size: 13px;
    font-weight: 600;
    color: #555;
    margin-bottom: 8px;
}

.sso-permissions ul {
    margin: 0;
    padding: 0;
    list-style: none;
}

.sso-permissions li {
    font-size: 14px;
    color: #333;
    padding: 4px 0;
}

.sso-inline-error {
    background: #fef2f2;
    color: #dc2626;
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 14px;
    margin-bottom: 16px;
    text-align: center;
}

.sso-actions {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.sso-btn {
    width: 100%;
    padding: 12px;
    border: none;
    border-radius: 10px;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s;
}

.sso-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.sso-btn--primary {
    background: var(--el-color-primary, #409eff);
    color: #fff;
}

.sso-btn--primary:hover:not(:disabled) {
    filter: brightness(1.08);
}

.sso-btn--secondary {
    background: #f1f5f9;
    color: #64748b;
}

.sso-btn--secondary:hover:not(:disabled) {
    background: #e2e8f0;
}

.sso-footer {
    margin: 20px 0 0;
    text-align: center;
    font-size: 12px;
    color: #999;
}

.sso-footer code {
    font-size: 11px;
    background: #f1f5f9;
    padding: 2px 6px;
    border-radius: 4px;
    word-break: break-all;
}
</style>
