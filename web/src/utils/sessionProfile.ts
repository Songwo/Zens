import { userApi, type UserProfile } from '@/api/user'
import { useUserStore } from '@/store/user'

const PROFILE_CACHE_TTL_MS = 60 * 1000

let cachedProfile: UserProfile | null = null
let cachedAt = 0
let cachedAccessToken: string | null = null
let profilePromise: Promise<UserProfile | null> | null = null

function getCurrentAccessToken() {
  const userStore = useUserStore()
  return userStore.accessToken || localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
}

function applyProfile(profile: UserProfile | null) {
  const userStore = useUserStore()

  if (!profile) {
    return
  }

  cachedProfile = profile
  cachedAt = Date.now()
  cachedAccessToken = getCurrentAccessToken()
  userStore.setUserInfo(profile)
  userStore.setUserId(profile.id)
}

function clearProfileCache() {
  cachedProfile = null
  cachedAt = 0
  cachedAccessToken = null
  profilePromise = null
}

export function hasAdminRole(profile?: Pick<UserProfile, 'roles'> | null) {
  const roles = profile?.roles || []
  return roles.some((role) => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
}

export function hasModeratorCapability(profile?: Pick<UserProfile, 'moderatedSectionIds'> | null) {
  return Array.isArray(profile?.moderatedSectionIds) && profile.moderatedSectionIds.length > 0
}

export function hasBackofficeAccess(profile?: Pick<UserProfile, 'roles' | 'moderatedSectionIds'> | null) {
  const roles = profile?.roles || []
  return hasAdminRole(profile) || roles.includes('ROLE_MODERATOR') || hasModeratorCapability(profile)
}

export async function ensureCurrentUserProfile(options: { force?: boolean } = {}) {
  const userStore = useUserStore()
  const accessToken = getCurrentAccessToken()

  if (!accessToken) {
    clearProfileCache()
    return null
  }

  if (cachedProfile && cachedAccessToken && cachedAccessToken !== accessToken) {
    // 登录态已经换了一轮，旧资料不能再拿来复用，避免切号后串到上一个用户。
    clearProfileCache()
  }

  if (!options.force && userStore.userInfo) {
    applyProfile(userStore.userInfo as UserProfile)
    return userStore.userInfo as UserProfile
  }

  if (!options.force && cachedProfile && Date.now() - cachedAt < PROFILE_CACHE_TTL_MS) {
    applyProfile(cachedProfile)
    return cachedProfile
  }

  if (profilePromise) {
    return profilePromise
  }

  // 用户资料是很多页面都会顺手拿的一份基础数据，这里合并成一个飞行中的请求，省得同一时间打好几次。
  profilePromise = userApi.getProfile()
    .then((res) => {
      if (res.code === 2000 && res.data) {
        applyProfile(res.data)
        return res.data
      }
      return null
    })
    .catch((error: any) => {
      const status = error?.response?.status
      if (status === 401 || status === 403) {
        clearProfileCache()
        userStore.logout()
        throw error
      }
      const fallbackProfile = cachedProfile || (userStore.userInfo as UserProfile | null)
      if (fallbackProfile) {
        applyProfile(fallbackProfile)
        return fallbackProfile
      }
      clearProfileCache()
      throw error
    })
    .finally(() => {
      profilePromise = null
    })

  return profilePromise
}

export function patchCurrentUserProfile(patch: Partial<UserProfile>) {
  const userStore = useUserStore()
  const baseProfile = (cachedProfile || userStore.userInfo) as UserProfile | null
  if (!baseProfile) {
    return null
  }
  const nextProfile = {
    ...baseProfile,
    ...patch,
  } as UserProfile
  applyProfile(nextProfile)
  return nextProfile
}

export function resetSessionProfileCache() {
  clearProfileCache()
}
