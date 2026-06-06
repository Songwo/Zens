# 个人资料页系统性重设计 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development(推荐)或 superpowers:executing-plans 按任务逐步执行。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 把 `/me` 与 `/user/:id` 重做成单栏 Editorial(无卡片)风格,信息架构从 10 标签精简到 4,补齐校园社区信息,通知中心迁出到独立页。

**Architecture:** 抽出一个无状态、数据靠 props 的 `ProfileHeader.vue` 给两页共用;`/me` 内容拆成 `ProfilePostList` / `RelationsPanel` / `CreatorPanel` 三个面板;通知中心迁到新 `/notifications` 页。后端仅给公开资料接口补两个字段。

**Tech Stack:** Vue 3 `<script setup lang="ts">` + Element Plus 2.13 + Pinia + Vue Router + Vite;后端 Spring Boot(record DTO)。

---

## 执行约定(全任务通用)

- **无前端单测框架。** 每个前端任务的"验证"= `cd web && npm run build`(等价 `vue-tsc -b && vite build`,类型检查 + 构建通过)+ 浏览器手动验证(每任务给出具体检查点)+ 提交。
- **后端验证** = `mvn -q -DskipTests compile` 通过 + 手测接口。
- **分支:** 当前 `main` 上有大量 WIP。执行前先开分支:`git switch -c feat/profile-redesign`(若用 worktree 则由 using-git-worktrees 处理)。
- **提交粒度:** 每个 Task 末尾提交一次;只 `git add` 本任务涉及的文件,**勿 `git add -A`**(避免卷入无关 WIP)。
- **Zens-Yellow:** 一律用 `var(--el-color-primary)`(= `var(--cp-primary)` = `#F4B400`)。文本/边框/背景统一用 EP 变量(`--el-text-color-primary/regular/secondary/placeholder`、`--el-border-color-lighter`、`--el-bg-color`、`--el-fill-color-*`),**勿硬编码 `#fff/#000`**,以兼容既有暗色(`html.dark`)。
- **短 ID:** 路由跳用户用 `encodeUserId`(`@/utils/shortId`),跳帖子用 `encodePostId`。

## 规范修正(相对 spec)

- spec 提到给后端补 `grade`,但 `User` 实体**无 `grade` 字段**,只有 `enrollmentYear: Integer`。**改为只补 `enrollmentYear` + `interestTags`**;"年级"在前端按 `${enrollmentYear}级` 展示。
- `moderatedSectionIds` 为 `@TableField(exist=false)`,`userService.getById()` 不填充 → **版主徽章 v1 延后**;v1 徽章只做 `roles` 的"管理"(复用 `UserRoleBadge`)。

## 文件结构总览

| 动作 | 文件 | 职责 |
|---|---|---|
| 改 | `src/main/java/.../dto/response/UserProfileResp.java` | record 增 `enrollmentYear`、`interestTags` |
| 改 | `src/main/java/.../controller/UserController.java:71` | 构造时回填两字段 |
| 改 | `web/src/api/user.ts` | `UserPublicProfile` 增 `enrollmentYear?`、`interestTags?` |
| 建 | `web/src/components/profile/ProfileHeader.vue` | 统一 Editorial 头部(无状态,props 驱动) |
| 建 | `web/src/components/profile/ProfilePostList.vue` | 贴文列表(PostCard + 无限滚动 + 空态),fetcher 参数化 |
| 建 | `web/src/components/profile/RelationsPanel.vue` | 关注/粉丝/话题 分段(仅自己) |
| 建 | `web/src/components/profile/CreatorPanel.vue` | 草稿/打回/回收站/浏览记录 分段(仅自己) |
| 建 | `web/src/pages/NotificationsPage.vue` | 迁移后的通知中心 |
| 改 | `web/src/router/index.ts` | 增 `/notifications` 路由 |
| 改 | `web/src/components/layout/AppTopbar.vue` | 铃铛"查看全部"跳 `/notifications` |
| 改 | `web/src/pages/UserProfilePage.vue` | 接入 ProfileHeader + ProfilePostList |
| 改 | `web/src/pages/MePage.vue` | 接入 ProfileHeader + 4 标签(动态/收藏/关系/创作管理) |

---

## Task 1: 后端公开资料补字段

**Files:**
- Modify: `src/main/java/com/campus/trend/campus_pulse/dto/response/UserProfileResp.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/controller/UserController.java:71-88`

- [ ] **Step 1: 给 record 增两个字段**

`UserProfileResp.java` 在 `quickCardBgUrl` 后追加两个组件(注意 record 是位置参数,放末尾以减少改动):

```java
public record UserProfileResp(
        String id,
        String username,
        String nickname,
        String avatar,
        String bio,
        String school,
        String major,
        Integer level,
        List<String> roles,
        long postCount,
        long followingCount,
        long followerCount,
        String profileCardTheme,
        String quickCardTheme,
        String profileCardBgUrl,
        String quickCardBgUrl,
        Integer enrollmentYear,
        String interestTags
) {
}
```

- [ ] **Step 2: 唯一构造点回填**(`new UserProfileResp(` 全仓仅此一处)

`UserController.java` 第 71 行的构造,在 `normalizeCardBgUrl(user.getQuickCardBgUrl())` 后补两个实参:

```java
        UserProfileResp resp = new UserProfileResp(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                user.getAvatar(),
                user.getBio(),
                user.getSchool(),
                user.getMajor(),
                user.getLevel(),
                List.of(StringUtils.hasText(user.getRole()) ? user.getRole() : "ROLE_USER"),
                postCount,
                followingCount,
                followerCount,
                StringUtils.hasText(user.getProfileCardTheme()) ? user.getProfileCardTheme() : "sunset",
                StringUtils.hasText(user.getQuickCardTheme()) ? user.getQuickCardTheme() : "ocean",
                normalizeCardBgUrl(user.getProfileCardBgUrl()),
                normalizeCardBgUrl(user.getQuickCardBgUrl()),
                user.getEnrollmentYear(),
                user.getInterestTags()
        );
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS(若报构造器参数不匹配,说明漏改了某个 `new UserProfileResp` —— 全仓搜索确认仅 UserController 一处)。

- [ ] **Step 4: 接口手测**

启动后请求 `GET /user/public/{某用户id}`,响应 JSON 含 `enrollmentYear`、`interestTags` 字段。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/campus/trend/campus_pulse/dto/response/UserProfileResp.java src/main/java/com/campus/trend/campus_pulse/controller/UserController.java
git commit -m "feat(user): expose enrollmentYear & interestTags in public profile"
```

---

## Task 2: 前端公开资料类型同步

**Files:**
- Modify: `web/src/api/user.ts:38-55`(`UserPublicProfile` 接口)

- [ ] **Step 1: 接口加字段**

`UserPublicProfile` 内补两行(`roles?` 已存在):

```ts
export interface UserPublicProfile {
    id: string
    username: string
    nickname: string
    avatar?: string
    bio?: string
    school?: string
    major?: string
    level?: number
    roles?: string[]
    enrollmentYear?: number
    interestTags?: string
    profileCardTheme?: string
    quickCardTheme?: string
    profileCardBgUrl?: string
    quickCardBgUrl?: string
    postCount: number
    followingCount: number
    followerCount: number
}
```

- [ ] **Step 2: 验证 + 提交**

Run: `cd web && npm run build` → 通过。

```bash
git add web/src/api/user.ts
git commit -m "feat(user-api): add enrollmentYear & interestTags to UserPublicProfile"
```

---

## Task 3: ProfileHeader 组件 + 接入他人主页

先在更简单的 `/user/:id` 上落地、验证头部,再用于 `/me`。

**Files:**
- Create: `web/src/components/profile/ProfileHeader.vue`
- Modify: `web/src/pages/UserProfilePage.vue`

- [ ] **Step 1: 创建 `ProfileHeader.vue`**(无状态,props 驱动,两页共用)

```vue
<script setup lang="ts">
import { computed } from 'vue'
import Avatar from '@/components/common/Avatar.vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import { EditPen, Connection, ChatDotRound } from '@element-plus/icons-vue'

export interface ProfileHeaderData {
  id: string
  username: string
  nickname?: string
  avatar?: string
  bio?: string
  school?: string
  major?: string
  enrollmentYear?: number
  interestTags?: string
  level?: number
  roles?: string[]
  profileCardBgUrl?: string
  postCount?: number
  followingCount?: number
  followerCount?: number
}

const props = withDefaults(defineProps<{
  profile: ProfileHeaderData
  variant: 'self' | 'other'
  isFollowing?: boolean
  followLoading?: boolean
  levelProgress?: number | null   // 0-100，仅 self
  levelHint?: string               // 如 “距 Lv.6 还差 380 经验”，仅 self
}>(), {
  isFollowing: false,
  followLoading: false,
  levelProgress: null,
  levelHint: '',
})

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'compose'): void
  (e: 'follow'): void
  (e: 'message'): void
  (e: 'stat-click', type: 'following' | 'followers'): void
}>()

const SUNSET = 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)'
const coverStyle = computed(() => {
  const url = String(props.profile.profileCardBgUrl || '').trim()
  const ok = /^https?:\/\/[^"'\s]+$/.test(url) || /^\/uploads\/[^"'\s]+$/.test(url)
  return ok
    ? { background: `url("${url}") center/cover no-repeat` }
    : { background: SUNSET }
})

const tags = computed(() =>
  String(props.profile.interestTags || '')
    .split(',').map(s => s.trim()).filter(Boolean)
)
const gradeText = computed(() =>
  props.profile.enrollmentYear ? `${props.profile.enrollmentYear}级` : ''
)
const campusMeta = computed(() =>
  [props.profile.school, props.profile.major, gradeText.value].filter(Boolean).join(' · ')
)
const isSelf = computed(() => props.variant === 'self')
const clickableStats = computed(() => isSelf.value)
</script>

<template>
  <header class="profile-header">
    <div class="ph-cover" :style="coverStyle"></div>

    <div class="ph-body">
      <Avatar :src="profile.avatar ?? undefined" :size="72" class="ph-avatar" />

      <div class="ph-namerow">
        <h1 class="ph-name">{{ profile.nickname || profile.username }}</h1>
        <UserRoleBadge :roles="profile.roles || []" />
        <span v-if="profile.level != null" class="ph-level-pill">Lv.{{ profile.level }}</span>
      </div>
      <div class="ph-handle">@{{ profile.username }}</div>

      <p v-if="profile.bio" class="ph-bio">{{ profile.bio }}</p>
      <p v-else-if="isSelf" class="ph-bio ph-bio-empty" @click="emit('edit')">写点介绍吧 ›</p>

      <div class="ph-stats">
        <span class="ph-stat"><b>{{ profile.postCount ?? 0 }}</b> 动态</span>
        <span class="ph-stat" :class="{ clickable: clickableStats }"
              @click="clickableStats && emit('stat-click','following')">
          <b>{{ profile.followingCount ?? 0 }}</b> 关注
        </span>
        <span class="ph-stat" :class="{ clickable: clickableStats }"
              @click="clickableStats && emit('stat-click','followers')">
          <b>{{ profile.followerCount ?? 0 }}</b> 粉丝
        </span>
      </div>

      <div v-if="isSelf && levelProgress != null" class="ph-level">
        <span class="ph-level-bar"><span class="ph-level-fill" :style="{ width: levelProgress + '%' }"></span></span>
        <span class="ph-level-hint">{{ levelHint }}</span>
      </div>

      <div v-if="campusMeta" class="ph-campus">🎓 {{ campusMeta }}</div>

      <div v-if="tags.length" class="ph-tags">
        <span v-for="t in tags" :key="t" class="ph-tag">{{ t }}</span>
      </div>
      <div v-else-if="isSelf" class="ph-tags">
        <span class="ph-tag ph-tag-add" @click="emit('edit')">＋ 添加兴趣</span>
      </div>

      <div class="ph-actions">
        <template v-if="isSelf">
          <el-button type="primary" :icon="EditPen" @click="emit('compose')">发布动态</el-button>
          <el-button @click="emit('edit')">编辑资料</el-button>
        </template>
        <template v-else>
          <el-button :type="isFollowing ? 'default' : 'primary'"
                     :icon="isFollowing ? undefined : Connection"
                     :loading="followLoading" @click="emit('follow')">
            {{ isFollowing ? '已关注' : '关注' }}
          </el-button>
          <el-button :icon="ChatDotRound" @click="emit('message')">私信</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.profile-header { background: var(--el-bg-color); }
.ph-cover { height: 96px; border-radius: 10px 10px 0 0; }
.ph-body { padding: 0 8px; }
.ph-avatar { margin-top: -36px; border: 4px solid var(--el-bg-color); border-radius: 50%; }
.ph-namerow { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.ph-name { margin: 0; font-size: 22px; font-weight: 800; color: var(--el-text-color-primary); }
.ph-level-pill { font-size: 11px; font-weight: 700; color: var(--el-color-primary);
  border: 1px solid var(--el-border-color); border-radius: 10px; padding: 1px 8px; }
.ph-handle { font-size: 13px; font-weight: 600; color: var(--el-color-primary); margin-top: 2px; }
.ph-bio { margin: 10px 0; font-size: 13px; line-height: 1.55; color: var(--el-text-color-regular); }
.ph-bio-empty { color: var(--el-text-color-placeholder); cursor: pointer; }
.ph-stats { display: flex; gap: 16px; font-size: 14px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.ph-stat b { color: var(--el-text-color-primary); }
.ph-stat.clickable { cursor: pointer; }
.ph-stat.clickable:hover b { color: var(--el-color-primary); }
.ph-level { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.ph-level-bar { flex: 1; max-width: 220px; height: 5px; background: var(--el-fill-color); border-radius: 3px; overflow: hidden; }
.ph-level-fill { display: block; height: 100%; background: var(--el-color-primary); }
.ph-level-hint { font-size: 11px; color: var(--el-text-color-placeholder); }
.ph-campus { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.ph-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.ph-tag { font-size: 12px; color: var(--el-text-color-secondary); border: 1px solid var(--el-border-color); border-radius: 14px; padding: 3px 10px; }
.ph-tag-add { cursor: pointer; border-style: dashed; }
.ph-actions { display: flex; gap: 10px; margin-bottom: 16px; }
@media (max-width: 640px) { .ph-cover { border-radius: 0; } }
</style>
```

- [ ] **Step 2: 接入 `UserProfilePage.vue`**

把现有 `el-card` 头部(模板 254-297 行)整体替换为 `ProfileHeader`;删除该页 `profileCardStyle`、`getCardThemePalette` 引入(头部不再需要)。保留取数逻辑(`fetchProfile`/`toggleFollow`/`isSelf`)。私信跳转用 query。

模板里(`<template v-else-if="profile">` 内,`PageBackButton` 之后)替换为:

```vue
<PageBackButton class="profile-back-button" fallback="/" />
<ProfileHeader
  v-if="!isSelf"
  :profile="profile"
  variant="other"
  :is-following="isFollowing"
  :follow-loading="followLoading"
  @follow="toggleFollow"
  @message="goMessage"
/>
<ProfileHeader
  v-else
  :profile="profile"
  variant="self"
  @edit="router.push('/settings')"
  @compose="composerStore.open()"
  @stat-click="() => router.push('/me')"
/>

<div class="posts-list">
  <PostCard v-for="post in posts" :key="post.id" :post="post" />
  <PostListSkeleton v-if="postsLoading && posts.length === 0" :count="3" />
  <EmptyState v-if="!postsLoading && posts.length === 0" title="暂无帖子" description="该用户还没有发布任何内容" />
  <div ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
  <div v-if="!hasMore && posts.length > 0" class="no-more">没有更多了</div>
</div>
```

`<script setup>` 增补:

```ts
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import { usePostComposerStore } from '@/store/postComposer'
import { encodeUserId } from '@/utils/shortId'   // 若已引入则复用
const composerStore = usePostComposerStore()

const goMessage = () => {
  if (!profile.value) return
  router.push({ path: '/messages', query: {
    peerId: profile.value.id,
    peerName: profile.value.nickname || profile.value.username,
    peerAvatar: profile.value.avatar || '',
  }})
}
```

> 注:`/messages` 已支持 `?peerId&peerName&peerAvatar` 直开会话(见 `MessagesPage.syncActiveConversationByRoute`),无需改私信页。

- [ ] **Step 3: 构建验证**

Run: `cd web && npm run build` → 通过。

- [ ] **Step 4: 手动验证**

打开他人主页 `/user/:id`:头部为 Editorial 风(封面/头像/昵称/@/简介/统计/校园信息/兴趣/关注+私信);点"关注"可切换;点"私信"跳 `/messages` 并定位到该用户;自己访问自己 `/user/:id` 显示 self 变体。暗色模式不破版。

- [ ] **Step 5: 提交**

```bash
git add web/src/components/profile/ProfileHeader.vue web/src/pages/UserProfilePage.vue
git commit -m "feat(profile): editorial ProfileHeader, adopt in user profile page"
```

---

## Task 4: 通知中心迁移到 `/notifications`

把 `MePage` 通知 tab 的全部逻辑搬到独立页,顶栏"查看全部"指过去。**本任务先不动 MePage 的通知 tab(Task 8 再删)**,先确保新页可用。

**Files:**
- Create: `web/src/pages/NotificationsPage.vue`
- Modify: `web/src/router/index.ts`(`/messages` 路由后增 `/notifications`)
- Modify: `web/src/components/layout/AppTopbar.vue:209-212`

- [ ] **Step 1: 创建 `NotificationsPage.vue`**

包一层 `MainLayout`,把 `MePage.vue` 中通知相关的 `<script>` 逻辑与 `notifications` tab 模板整体迁入。需迁移的源(均在 `MePage.vue`):
- 状态:`notifications/notifLoading/notifUnread/notifTypeFilter/notifOnlyUnread/notifSelectedIds`(67-72 行)
- 常量/计算:`notifTypeLabelMap`(104-120)、`notificationTypeOptions`(122-131)、`filteredNotifications`(133-141)、`groupedNotifications`(143-172)、`filteredNotificationIdSet`(174-176)、`hasNotificationSelection`/`allFilteredSelected`(178-184)
- 方法:`fetchNotifications`(328-343,**删掉其中的 `console.log`**)、`syncTopbarNotificationUnread`(345-351)、`markRead`/`markAllRead`/`openNotification`/选择与批量/删除(353-437)
- 依赖引入:`notificationApi` 及类型、`emitNotificationUnreadSync`、`resolveNotificationRoute`、`clearRequestCache`、`timeAgo`、`useUserStore`、`useRouter`、相关 EP 图标(`Check/Delete/Notification/ChatDotRound`)
- 模板:`notifications` tab 内的 `notif-header` + `groupedNotifications` 渲染(965-1054 行),外层换成页面容器(非 `el-tab-pane`)
- `onMounted`:登录校验 + `fetchNotifications()`
- 样式:迁移 `.notif-*` 相关 scoped 样式(1283-1350 行)

- [ ] **Step 2: 注册路由**

`web/src/router/index.ts` 在 `/messages`(72-73 行附近)之后插入:

```ts
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/pages/NotificationsPage.vue'),
      meta: { requiresAuth: true, title: '消息通知' },
    },
```

(`meta` 字段与同文件既有受保护路由保持一致;参照 `/me` 的 meta 写法。)

- [ ] **Step 3: 顶栏铃铛指向新页**

`AppTopbar.vue` 的 `goNotificationCenter`(209-212 行):

```ts
const goNotificationCenter = async () => {
  notifPopoverVisible.value = false
  await router.push('/notifications')
}
```

- [ ] **Step 4: 构建 + 手动验证**

Run: `cd web && npm run build` → 通过。
手测:顶栏铃铛 popover 底部"查看全部通知" → 跳 `/notifications`;该页筛选/分组/批量已读/批量删除/单条删除均工作;未读数与顶栏同步。

- [ ] **Step 5: 提交**

```bash
git add web/src/pages/NotificationsPage.vue web/src/router/index.ts web/src/components/layout/AppTopbar.vue
git commit -m "feat(notifications): dedicated /notifications page; topbar links to it"
```

---

## Task 5: ProfilePostList 共享列表组件

**Files:**
- Create: `web/src/components/profile/ProfilePostList.vue`

供 动态/收藏/草稿/打回/回收站 复用:接收一个 `fetcher(page)` 与空态配置,内部管分页 + 无限滚动 + 骨架/空态,并把 `PostCard` 的 `deleted/restored` 事件透传(用于草稿/回收站)。

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { ref, watch, onMounted, type Component } from 'vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PostListSkeleton from '@/components/common/PostListSkeleton.vue'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import type { Post } from '@/types'

const props = defineProps<{
  // 给定页码，返回该页记录；约定每页 10 条
  fetcher: (page: number) => Promise<Post[]>
  emptyTitle: string
  emptyDescription?: string
  emptyIcon?: Component
  // 任一 reloadKey 变化即重置重拉（如切换二级分段）
  reloadKey?: string | number
}>()

const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

const load = async (reset = false) => {
  if (reset) { page.value = 1; posts.value = []; hasMore.value = true }
  if (!hasMore.value || loading.value) return
  loading.value = true
  try {
    const records = await props.fetcher(page.value)
    if (records.length > 0) { posts.value.push(...records); page.value++ }
    if (records.length < 10) hasMore.value = false
  } finally { loading.value = false }
}

const remove = (id: string) => { posts.value = posts.value.filter(p => p.id !== id) }

const { sentinel } = useInfiniteScroll(() => load(false), {
  canLoadMore: () => hasMore.value && !loading.value && posts.value.length > 0,
})

onMounted(() => load(true))
watch(() => props.reloadKey, () => load(true))
defineExpose({ reload: () => load(true) })
</script>

<template>
  <div class="profile-post-list">
    <PostCard v-for="post in posts" :key="post.id" :post="post" @deleted="remove" @restored="remove" />
    <PostListSkeleton v-if="loading && posts.length === 0" :count="3" />
    <EmptyState v-if="!loading && posts.length === 0"
      :icon="emptyIcon" :title="emptyTitle" :description="emptyDescription" />
    <div ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
    <div v-if="!hasMore && posts.length > 0" class="no-more">没有更多了</div>
  </div>
</template>

<style scoped>
.profile-post-list { display: flex; flex-direction: column; gap: 16px; }
.infinite-sentinel { height: 1px; width: 100%; }
.no-more { text-align: center; padding: 16px 0; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
```

- [ ] **Step 2: 验证 + 提交**

Run: `cd web && npm run build` → 通过(组件未被引用也应类型通过)。

```bash
git add web/src/components/profile/ProfilePostList.vue
git commit -m "feat(profile): reusable ProfilePostList with infinite scroll"
```

---

## Task 6: RelationsPanel(关注/粉丝/话题)

**Files:**
- Create: `web/src/components/profile/RelationsPanel.vue`

- [ ] **Step 1: 创建组件**(`el-segmented` 切换三段,数据用现有 my-接口)

```vue
<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Avatar from '@/components/common/Avatar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { followApi } from '@/api/follow'
import { tagApi, type Tag } from '@/api/tag'
import { encodeUserId } from '@/utils/shortId'
import { ElMessage } from 'element-plus'
import { User, Connection, PriceTag } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{ initialSub?: 'following' | 'followers' | 'tags' }>(), {
  initialSub: 'following',
})
const router = useRouter()
const sub = ref<'following' | 'followers' | 'tags'>(props.initialSub)
const options = [
  { label: '关注', value: 'following' },
  { label: '粉丝', value: 'followers' },
  { label: '话题', value: 'tags' },
]

const following = ref<any[]>([])
const followers = ref<any[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)

const loadFollowing = async () => { loading.value = true; try { following.value = (await followApi.getMyFollowing()).data ?? [] } finally { loading.value = false } }
const loadFollowers = async () => { loading.value = true; try { followers.value = (await followApi.getMyFollowers()).data ?? [] } finally { loading.value = false } }
const loadTags = async () => { loading.value = true; try { tags.value = (await tagApi.getMyFollowing()).data ?? [] } finally { loading.value = false } }

const loadCurrent = () => {
  if (sub.value === 'following') return loadFollowing()
  if (sub.value === 'followers') return loadFollowers()
  return loadTags()
}
watch(sub, loadCurrent)
watch(() => props.initialSub, v => { if (v && v !== sub.value) sub.value = v })
onMounted(loadCurrent)

const goUser = (id: string) => router.push(`/user/${encodeUserId(id)}`)
const unfollowUser = async (id: string) => { await followApi.unfollow(id); following.value = following.value.filter(u => u.id !== id) }
const unfollowTag = async (t: Tag) => { try { await tagApi.unfollow(t.id); tags.value = tags.value.filter(x => x.id !== t.id); ElMessage.success(`已取消关注 #${t.name}`) } catch { ElMessage.error('取消关注失败') } }
</script>

<template>
  <div class="relations-panel">
    <el-segmented v-model="sub" :options="options" />
    <div v-loading="loading" class="rel-list">
      <!-- 关注 -->
      <template v-if="sub === 'following'">
        <div v-for="u in following" :key="u.id" class="rel-row">
          <div class="rel-user" @click="goUser(u.id)">
            <Avatar :src="u.avatar ?? undefined" size="md" />
            <div class="rel-meta"><span class="rel-name">{{ u.nickname || u.username }}</span><span class="rel-sub">@{{ u.username }}</span></div>
          </div>
          <el-button size="small" plain @click="unfollowUser(u.id)">取消关注</el-button>
        </div>
        <EmptyState v-if="!loading && !following.length" :icon="User" title="还没有关注任何人" description="关注有趣的人，第一时间看到动态" />
      </template>
      <!-- 粉丝 -->
      <template v-else-if="sub === 'followers'">
        <div v-for="u in followers" :key="u.id" class="rel-row">
          <div class="rel-user" @click="goUser(u.id)">
            <Avatar :src="u.avatar ?? undefined" size="md" />
            <div class="rel-meta"><span class="rel-name">{{ u.nickname || u.username }}</span><span class="rel-sub">@{{ u.username }}</span></div>
          </div>
          <el-button size="small" type="primary" plain @click="goUser(u.id)">主页</el-button>
        </div>
        <EmptyState v-if="!loading && !followers.length" :icon="Connection" title="还没有粉丝" description="发布优质内容，吸引更多人关注你" />
      </template>
      <!-- 话题 -->
      <template v-else>
        <div v-for="t in tags" :key="t.id" class="rel-row">
          <div class="rel-user" @click="router.push(`/tag/${t.name}`)">
            <span class="rel-tag">#{{ t.name }}</span><span class="rel-sub">{{ t.postCount ?? 0 }} 篇</span>
          </div>
          <el-button size="small" plain @click="unfollowTag(t)">取消关注</el-button>
        </div>
        <EmptyState v-if="!loading && !tags.length" :icon="PriceTag" title="还没有关注话题" description="在话题页点「关注」，有新帖会通知你" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.rel-list { margin-top: 12px; }
.rel-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.rel-user { display: flex; align-items: center; gap: 10px; min-width: 0; cursor: pointer; flex: 1; }
.rel-meta { display: flex; flex-direction: column; min-width: 0; }
.rel-name { font-weight: 600; color: var(--el-text-color-primary); }
.rel-sub { font-size: 12px; color: var(--el-text-color-secondary); }
.rel-tag { font-weight: 700; color: var(--el-color-primary); }
</style>
```

- [ ] **Step 2: 验证 + 提交**

Run: `cd web && npm run build` → 通过。

```bash
git add web/src/components/profile/RelationsPanel.vue
git commit -m "feat(profile): RelationsPanel (following/followers/tags)"
```

---

## Task 7: CreatorPanel(草稿/打回/回收站/浏览记录)

**Files:**
- Create: `web/src/components/profile/CreatorPanel.vue`

- [ ] **Step 1: 创建组件**(`el-segmented` 四段;前三段用 `ProfilePostList`,浏览记录单独列表)

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ProfilePostList from '@/components/profile/ProfilePostList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { viewLogApi, type ViewLog } from '@/api/viewLog'
import { useUserStore } from '@/store/user'
import { encodePostId } from '@/utils/shortId'
import { EditPen, Warning, Delete, View } from '@element-plus/icons-vue'
import type { Post } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const uid = () => userStore.userId || userStore.userInfo?.id || ''
const sub = ref<'drafts' | 'rejected' | 'trash' | 'history'>('drafts')
const options = [
  { label: '草稿', value: 'drafts' },
  { label: '打回修改', value: 'rejected' },
  { label: '回收站', value: 'trash' },
  { label: '浏览记录', value: 'history' },
]

const draftsFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), status: 0, auditStatus: 'DRAFT' }).then(r => r.data.records as Post[])
const rejectedFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), status: 0, auditStatus: 'REJECTED' }).then(r => r.data.records as Post[])
const trashFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), auditStatus: 'DELETED' }).then(r => r.data.records as Post[])

// 浏览记录
const history = ref<ViewLog[]>([])
const histLoading = ref(false)
const loadHistory = async () => {
  histLoading.value = true
  try { history.value = (await viewLogApi.getUserHistoryPaged(uid(), 1, 20)).data?.records || [] }
  finally { histLoading.value = false }
}
const goPost = (id: string) => id && router.push(`/t/${encodePostId(id)}`)
</script>

<template>
  <div class="creator-panel">
    <el-segmented v-model="sub" :options="options" @change="sub === 'history' && loadHistory()" />
    <div class="creator-body">
      <ProfilePostList v-if="sub === 'drafts'" :fetcher="draftsFetcher" :empty-icon="EditPen" empty-title="暂无草稿" empty-description="保存未提交的草稿在这里" />
      <ProfilePostList v-else-if="sub === 'rejected'" :fetcher="rejectedFetcher" :empty-icon="Warning" empty-title="暂无打回帖子" empty-description="被打回的帖子在这里，可编辑后重新提交" />
      <ProfilePostList v-else-if="sub === 'trash'" :fetcher="trashFetcher" :empty-icon="Delete" empty-title="回收站为空" empty-description="删除的帖子保留 7 天，可在此恢复" />
      <div v-else v-loading="histLoading">
        <div v-for="h in history" :key="h.postId" class="hist-row" @click="goPost(h.postId)">
          <span class="hist-title">{{ h.title || '该帖子已删除' }}</span>
          <span class="hist-time">{{ h.viewTime }}</span>
        </div>
        <EmptyState v-if="!histLoading && !history.length" :icon="View" title="暂无浏览记录" description="浏览过的帖子会展示在这里" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.creator-body { margin-top: 12px; }
.hist-row { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); cursor: pointer; }
.hist-title { font-weight: 600; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hist-time { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
</style>
```

> 注:`postApi.searchList` 的参数形态与现 `MePage.fetchPosts`(212-286 行)一致;`history` 首段为 `drafts` 时不会自动加载,切到"浏览记录"时由 `@change` 触发 `loadHistory`。若希望初始就在 history 段,另行调用。

- [ ] **Step 2: 验证 + 提交**

Run: `cd web && npm run build` → 通过。

```bash
git add web/src/components/profile/CreatorPanel.vue
git commit -m "feat(profile): CreatorPanel (drafts/rejected/trash/history)"
```

---

## Task 8: 重构 MePage(头部 + 4 标签)

**Files:**
- Modify: `web/src/pages/MePage.vue`(整页重写)

- [ ] **Step 1: 重写 `MePage.vue`**

`<script setup>` 收敛为:取登录用户资料/统计/等级 + 组合组件。核心如下(删除已迁出的通知逻辑、抽屉、假兴趣标签、手动加载更多):

```ts
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import MainLayout from '@/layouts/MainLayout.vue'
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import ProfilePostList from '@/components/profile/ProfilePostList.vue'
import RelationsPanel from '@/components/profile/RelationsPanel.vue'
import CreatorPanel from '@/components/profile/CreatorPanel.vue'
import { postApi } from '@/api/post'
import { userApi } from '@/api/user'
import { levelApi, type LevelInfo } from '@/api/level'
import { usePostComposerStore } from '@/store/postComposer'
import { ElMessage } from 'element-plus'
import type { Post } from '@/types'

const router = useRouter(); const route = useRoute()
const userStore = useUserStore(); const composerStore = usePostComposerStore()

const activeTab = ref<'posts' | 'favorites' | 'relations' | 'creator'>(
  (route.query.tab as any) || 'posts')
const relationsInitialSub = ref<'following' | 'followers' | 'tags'>(
  (route.query.sub as any) || 'following')

const stats = ref<{ postCount: number; followingCount: number; followerCount: number }>(
  { postCount: 0, followingCount: 0, followerCount: 0 })
const levelInfo = ref<LevelInfo | null>(null)

const headerProfile = computed(() => ({
  id: String(userStore.userInfo?.id ?? userStore.userId ?? ''),
  username: userStore.userInfo?.username ?? '',
  nickname: userStore.userInfo?.nickname,
  avatar: userStore.userInfo?.avatar,
  bio: userStore.userInfo?.bio,
  school: userStore.userInfo?.school,
  major: userStore.userInfo?.major,
  enrollmentYear: userStore.userInfo?.enrollmentYear,
  interestTags: userStore.userInfo?.interestTags,
  level: levelInfo.value?.level ?? userStore.userInfo?.level,
  roles: userStore.userInfo?.roles,
  profileCardBgUrl: userStore.userInfo?.profileCardBgUrl,
  postCount: stats.value.postCount,
  followingCount: stats.value.followingCount,
  followerCount: stats.value.followerCount,
}))
const levelHint = computed(() => levelInfo.value
  ? `距 Lv.${levelInfo.value.level + 1} 还差 ${Math.max(0, levelInfo.value.nextLevelExp - levelInfo.value.experience)} 经验`
  : '')

const postsFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: headerProfile.value.id, status: 1 }).then(r => r.data.records as Post[])
const favFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, collectedBy: headerProfile.value.id, status: 1 }).then(r => r.data.records as Post[])

const onTab = (name: string | number) => { router.replace({ query: { ...route.query, tab: String(name) } }) }
const onStatClick = (type: 'following' | 'followers') => {
  relationsInitialSub.value = type
  activeTab.value = 'relations'
  router.replace({ query: { ...route.query, tab: 'relations', sub: type } })
}

const fetchStats = async () => { try { const r = await userApi.getProfileStats(); if (r.data) stats.value = { postCount: r.data.postCount ?? 0, followingCount: r.data.followingCount ?? 0, followerCount: r.data.followerCount ?? 0 } } catch { ElMessage.error('获取统计失败') } }
const fetchLevel = async () => { try { levelInfo.value = (await levelApi.getInfo()).data } catch { /* ignore */ } }

onMounted(() => {
  if (!userStore.accessToken) { ElMessage.error('请先登录'); router.push('/auth/login'); return }
  fetchStats(); fetchLevel()
})
watch(() => route.query.tab, t => { if (t && typeof t === 'string') activeTab.value = t as any })
```

模板:

```vue
<template>
  <MainLayout>
    <div class="me-container">
      <ProfileHeader
        :profile="headerProfile"
        variant="self"
        :level-progress="levelInfo?.progress ?? null"
        :level-hint="levelHint"
        @edit="router.push('/settings')"
        @compose="composerStore.open()"
        @stat-click="onStatClick"
      />

      <el-tabs v-model="activeTab" class="me-tabs" @tab-change="onTab">
        <el-tab-pane name="posts" label="动态">
          <ProfilePostList :fetcher="postsFetcher" empty-title="还没有发布动态" empty-description="快去分享你的第一篇校园见闻吧！" />
        </el-tab-pane>
        <el-tab-pane name="favorites" label="收藏">
          <ProfilePostList :fetcher="favFetcher" empty-title="暂无收藏" empty-description="去发现感兴趣的内容并收藏吧！" />
        </el-tab-pane>
        <el-tab-pane name="relations" label="关系">
          <RelationsPanel v-if="activeTab === 'relations'" :initial-sub="relationsInitialSub" />
        </el-tab-pane>
        <el-tab-pane name="creator" label="创作管理">
          <CreatorPanel v-if="activeTab === 'creator'" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </MainLayout>
</template>

<style scoped>
.me-container { max-width: min(100%, 720px); margin: 0 auto; }
.me-tabs { margin-top: 8px; }
.me-tabs :deep(.el-tabs__active-bar) { background-color: var(--el-color-primary); }
@media (max-width: 640px) {
  .me-tabs :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
  .me-tabs :deep(.el-tabs__nav) { white-space: nowrap; }
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd web && npm run build` → 通过(确认无对已删除变量/方法的引用残留)。

- [ ] **Step 3: 手动验证**

`/me`:头部 Editorial;4 个标签;动态/收藏 无限滚动;点头部"关注/粉丝"数字 → 切到"关系"对应分段且 URL 带 `?tab=relations&sub=...`;"关系"内分段切换正常;"创作管理"四段(草稿/打回/回收站可删可恢复、浏览记录可点);兴趣空时显示"＋添加兴趣"(非假数据);刷新保持当前 tab;移动端标签可横滑;暗色不破版;`/me?tab=notifications` 不再有内容(下一步移除入口已在 Task 4 完成)。

- [ ] **Step 4: 提交**

```bash
git add web/src/pages/MePage.vue
git commit -m "feat(me): editorial redesign with 4 tabs (posts/favorites/relations/creator)"
```

---

## Task 9: 视觉/暗色/响应式收尾与清理

**Files:**
- Modify: 上述新组件的 scoped 样式(按需)
- 核对:`web/src/pages/MePage.vue`、`UserProfilePage.vue` 无残留死代码

- [ ] **Step 1: 暗色与响应式走查**

切到 `html.dark`,逐页检查 ProfileHeader/各面板:文字对比度、分割线、封面兜底渐变、Lv 进度条颜色均正常(全部用 `--el-*`/`--cp-primary`)。窄屏(≤640px)封面无圆角、标签横滑、按钮换行正常。

- [ ] **Step 2: 死代码清理核查**

确认以下已无引用残留:`getCardThemePalette`/`profileCardStyle`(MePage 已不需要)、通知相关状态/方法(已迁出)、硬编码兴趣兜底(Java/Spring Boot/Vue3/Docker)、`console.log('通知列表响应:', res)`。`UserProfilePage` 若不再用 `getCardThemePalette` 也一并移除引入。

Run: `cd web && npm run build` → 通过。

- [ ] **Step 3: 最终手动回归**

按 spec §12 验证矩阵过一遍(普通/版主-管理/无简介-无兴趣;他人主页关注+私信;通知中心;暗色;移动端)。

- [ ] **Step 4: 提交**

```bash
git add web/src/components/profile web/src/pages/MePage.vue web/src/pages/UserProfilePage.vue
git commit -m "polish(profile): dark-mode/responsive pass and dead-code cleanup"
```

---

## 自检(写完计划后,对照 spec)

- **覆盖:** ❶无卡片→Task3/5/6/7/8/9 的 Editorial 样式;❷假兴趣→Task8/9;❸可点统计→Task3/8(`stat-click`);❹10→4 标签→Task8;❺通知迁出→Task4;❻校园信息+学校 bug→Task3/8(改读 `userInfo`)。`/user/:id` 统一→Task3。后端字段→Task1/2。**均有对应任务。**
- **类型一致性:** `ProfileHeaderData`(Task3)字段与 MePage `headerProfile`(Task8)、`UserPublicProfile`(Task2)对齐;`ProfilePostList` 的 `fetcher: (page)=>Promise<Post[]>` 在 Task5 定义、Task7/8 一致使用;`emit('stat-click', type)` 签名 Task3 定义、Task8 一致消费。
- **占位符:** 无 TODO/TBD;面板迁移给出了确切源行号与改造点,非空泛描述。
- **已知取舍:** 贴文仍用 `PostCard`(spec §11 默认);版主徽章 v1 延后(规范修正);他人主页不加关系/收藏页(避免新接口)。

## 执行交接

计划已保存。两种执行方式:
1. **Subagent-Driven(推荐)** —— 每个 Task 派新子代理执行 + 两段式评审,迭代快。
2. **Inline 执行** —— 本会话内用 executing-plans 批量执行 + 检查点。

选哪种?
