# 个人资料封面自助编辑 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development(推荐)或 superpowers:executing-plans 按任务逐步执行。步骤用 `- [ ]` 复选框跟踪。

**Goal:** 让用户在自己的 `/me` 主页内联调节资料卡封面(填满/完整、焦点位置、高度),保存到后端,本人与他人(`/user/:id`)看到一致效果。

**Architecture:** 后端在 `sys_user` 加一个 `cover_config`(JSON 字符串)列,经两个读 DTO 返回、经新 `PUT /user/cover` 写入;前端抽出 `ProfileCover.vue` 同时负责封面渲染与(仅自己的)拖拽编辑,`ProfileHeader` 委托给它,`MePage` 负责持久化。

**Tech Stack:** Spring Boot + MyBatis-Plus + MySQL;Vue 3 `<script setup lang="ts">` + Element Plus + Pinia。

## 执行约定(全任务通用)

- **无前端单测框架。** 前端验证 = `cd web && npm run build:check`(类型检查;当前**基线约 60 个既有错误**,目标是**我改的文件不出现在报错里、总数不超过 60**)+ `cd web && npm run build`(vite 出包,EXIT 0)+ 手测。
- **后端验证** = 仓库根 `mvn -q -DskipTests compile` BUILD SUCCESS + 手测接口。
- **分支:** 当前 `feat/profile-redesign`,继续在此分支提交。
- **提交粒度:** 每个 Task 末尾提交一次;`git add` 仅本任务文件(**勿 `git add -A`**)。`docs/` 已被 .gitignore,提交本计划/spec 需 `git add -f`。
- **MyBatis-Plus:** `updateById` 默认只更新非 null 字段,故 `new User().setId(id).setCoverConfig(json)` 不会清空其它列(参照 `UserController.updateNotificationSettings`)。

## 文件结构总览

| 动作 | 文件 | 职责 |
|---|---|---|
| 改 | `src/main/java/com/campus/trend/campus_pulse/entity/User.java` | 加 `coverConfig`(列 `cover_config`) |
| 改 | `src/main/java/com/campus/trend/campus_pulse/dto/response/UserProfileResp.java` | record 末尾加 `String coverConfig` |
| 改 | `src/main/java/com/campus/trend/campus_pulse/dto/response/UserDetailResp.java` | 加 `String coverConfig` |
| 改 | `src/main/java/com/campus/trend/campus_pulse/service/impl/UserServiceImpl.java` | `getProfile()` 回填 coverConfig |
| 改 | `src/main/java/com/campus/trend/campus_pulse/controller/UserController.java` | `getPublicProfile` 回填;新增 `PUT /user/cover` |
| 建 | `src/main/java/com/campus/trend/campus_pulse/dto/request/CoverConfigUpdateReq.java` | 写接口入参 |
| 改 | `src/main/resources/sql/campus_pulse_schema.sql` | sys_user 加列 |
| 建 | `src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql` | 迁移脚本 |
| 建 | `web/src/utils/coverConfig.ts` | `CoverConfig` 类型 + `parseCoverConfig` |
| 改 | `web/src/api/user.ts` | `UserProfile`/`UserPublicProfile` 加字段;`updateCover` |
| 建 | `web/src/components/profile/ProfileCover.vue` | 封面渲染 + 编辑(props 入 / onSave 回调) |
| 改 | `web/src/components/profile/ProfileHeader.vue` | 用 ProfileCover 替换内联封面;透传 editable/onSaveCover |
| 改 | `web/src/pages/MePage.vue` | editable=true + handleSaveCover + headerProfile.coverConfig |
| 改 | `web/src/pages/UserProfilePage.vue` | 只读(editable 默认 false),确认 coverConfig 透传 |

---

## Task 1: 后端持久化字段 + 两个读接口

**Files:**
- Modify: `src/main/java/com/campus/trend/campus_pulse/entity/User.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/dto/response/UserProfileResp.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/dto/response/UserDetailResp.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/controller/UserController.java`
- Modify: `src/main/resources/sql/campus_pulse_schema.sql`
- Create: `src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql`

- [ ] **Step 1: 实体加字段**

`User.java` 在 `private String quickCardBgUrl;`(第 102 行)后追加:

```java
    private String quickCardBgUrl;
    /**
     * 封面展示配置 (JSON: fit/x/y/height)
     */
    private String coverConfig;
```

- [ ] **Step 2: 公开资料 DTO 加字段**

`UserProfileResp.java` record 末尾(`String interestTags` 后)加一个组件:

```java
        Integer enrollmentYear,
        String interestTags,
        String coverConfig
) {
}
```

- [ ] **Step 3: 公开资料构造点回填**

`UserController.getPublicProfile`,在 `user.getInterestTags()`(第 89 行)后补一个实参:

```java
                user.getEnrollmentYear(),
                user.getInterestTags(),
                user.getCoverConfig()
        );
```

- [ ] **Step 4: 自己资料 DTO 加字段**

`UserDetailResp.java` 在 `private String quickCardBgUrl;` 后加:

```java
    private String quickCardBgUrl;
    private String coverConfig;
```

- [ ] **Step 5: 自己资料回填**

`UserServiceImpl.getProfile()`,在 `proFileResponse.setQuickCardBgUrl(...)`(第 101 行)后加:

```java
        proFileResponse.setQuickCardBgUrl(resolveCardBgUrl(sysUser.getQuickCardBgUrl(), null));
        proFileResponse.setCoverConfig(sysUser.getCoverConfig());
```

- [ ] **Step 6: schema 加列**

`campus_pulse_schema.sql` 在 `quick_card_bg_url`(第 54 行)后插入:

```sql
  `quick_card_bg_url`    varchar(500)  DEFAULT NULL COMMENT '头像预览卡片背景图URL',
  `cover_config`         varchar(255)  DEFAULT NULL COMMENT '封面展示配置(JSON: fit/x/y/height)',
```

- [ ] **Step 7: 迁移脚本**

新建 `src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql`:

```sql
-- ============================================================
-- 用户封面展示配置字段迁移：2026-06-06
-- 给 sys_user 加 cover_config(JSON: fit/x/y/height),供资料卡封面自助调节
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_user`
  ADD COLUMN `cover_config` varchar(255) DEFAULT NULL
      COMMENT '封面展示配置(JSON: fit/x/y/height)'
      AFTER `quick_card_bg_url`;
```

- [ ] **Step 8: 编译验证**

Run(仓库根): `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。若报 `UserProfileResp` 构造参数不匹配,说明 Step 3 漏改或参数顺序错位 —— 全仓 `new UserProfileResp(` 仅 `UserController` 一处。

- [ ] **Step 9: 手测(可选,需本地库已执行迁移)**

`GET /user/public/{id}` 与 `GET /user/profile` 响应 JSON 含 `coverConfig`(老用户为 null)。

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/campus/trend/campus_pulse/entity/User.java src/main/java/com/campus/trend/campus_pulse/dto/response/UserProfileResp.java src/main/java/com/campus/trend/campus_pulse/dto/response/UserDetailResp.java src/main/java/com/campus/trend/campus_pulse/service/impl/UserServiceImpl.java src/main/java/com/campus/trend/campus_pulse/controller/UserController.java src/main/resources/sql/campus_pulse_schema.sql
git add -f src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql 2>/dev/null || git add src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql
git commit -m "feat(user): persist & expose cover_config on profile reads"
```

> 注:`src/main/resources/sql/` 不在 .gitignore(只有 `docs/` 被忽略),迁移脚本正常 `git add` 即可;上面的 `-f` 兜底无害。

---

## Task 2: 后端写接口 `PUT /user/cover`

**Files:**
- Create: `src/main/java/com/campus/trend/campus_pulse/dto/request/CoverConfigUpdateReq.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/controller/UserController.java`

- [ ] **Step 1: 新建入参 DTO**

`CoverConfigUpdateReq.java`:

```java
package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CoverConfigUpdateReq {

    @Pattern(regexp = "^(cover|contain)$", message = "fit 仅支持 cover/contain")
    private String fit;

    private Integer x;
    private Integer y;
    private Integer height;
}
```

- [ ] **Step 2: 控制器加写接口 + clamp 辅助**

`UserController.java` 加 import:

```java
import com.campus.trend.campus_pulse.dto.request.CoverConfigUpdateReq;
```

在 `getProfileStats()` 方法后(第 206 行附近)加入接口与私有辅助:

```java
    @PutMapping("/cover")
    public Result<?> updateCover(@Valid @RequestBody CoverConfigUpdateReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        String fit = "contain".equals(req.getFit()) ? "contain" : "cover";
        int x = clampInt(req.getX(), 0, 100, 50);
        int y = clampInt(req.getY(), 0, 100, 50);
        int height = clampInt(req.getHeight(), 120, 600, 320);
        String json = String.format("{\"fit\":\"%s\",\"x\":%d,\"y\":%d,\"height\":%d}", fit, x, y, height);

        User update = new User();
        update.setId(userId);
        update.setCoverConfig(json);
        userService.updateById(update);
        return Result.success(json);
    }

    private static int clampInt(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
```

> `fit` 已由 `@Pattern` 限定 cover/contain,且这里再兜底,故 `String.format` 拼 JSON 安全;x/y/height 为整数 clamp,无注入风险。

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: 手测**

登录后 `PUT /user/cover` body `{"fit":"cover","x":50,"y":30,"height":360}` → 返回该 JSON;再 `GET /user/profile` 的 `coverConfig` 已更新;传越界(如 height:9999)→ 被 clamp 到 600;传非法 fit → 400 校验失败。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/campus/trend/campus_pulse/dto/request/CoverConfigUpdateReq.java src/main/java/com/campus/trend/campus_pulse/controller/UserController.java
git commit -m "feat(user): add PUT /user/cover to save cover display config"
```

---

## Task 3: 前端解析工具 + API

**Files:**
- Create: `web/src/utils/coverConfig.ts`
- Modify: `web/src/api/user.ts`

- [ ] **Step 1: 解析工具**

`web/src/utils/coverConfig.ts`:

```ts
export interface CoverConfig {
  fit: 'cover' | 'contain'
  x: number
  y: number
  height: number
}

export const DEFAULT_COVER_CONFIG: CoverConfig = { fit: 'cover', x: 50, y: 50, height: 320 }

const clamp = (n: number, min: number, max: number, fallback: number) =>
  Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback

export function parseCoverConfig(raw?: string | null): CoverConfig {
  if (!raw) return { ...DEFAULT_COVER_CONFIG }
  try {
    const o = JSON.parse(raw) as Partial<CoverConfig>
    return {
      fit: o.fit === 'contain' ? 'contain' : 'cover',
      x: clamp(Number(o.x), 0, 100, 50),
      y: clamp(Number(o.y), 0, 100, 50),
      height: clamp(Number(o.height), 120, 600, 320),
    }
  } catch {
    return { ...DEFAULT_COVER_CONFIG }
  }
}
```

- [ ] **Step 2: API 类型 + 方法**

`web/src/api/user.ts`:
1. `UserProfile` 接口在 `quickCardBgUrl?: string` 后加 `coverConfig?: string`。
2. `UserPublicProfile` 接口在 `quickCardBgUrl?: string` 后加 `coverConfig?: string`。
3. `userApi` 对象内新增方法:

```ts
    updateCover: (payload: { fit: 'cover' | 'contain'; x: number; y: number; height: number }) =>
        api.put<any, Result<string>>('/user/cover', payload),
```

- [ ] **Step 3: 验证**

Run: `cd web && npm run build:check`
Expected: `coverConfig.ts` / `user.ts` 不出现在错误里;总错误数 ≤ 60(基线)。
Run: `cd web && npm run build`
Expected: EXIT 0。

- [ ] **Step 4: 提交**

```bash
git add web/src/utils/coverConfig.ts web/src/api/user.ts
git commit -m "feat(user-api): coverConfig type/parser and updateCover endpoint"
```

---

## Task 4: `ProfileCover.vue` 渲染 + 编辑组件

**Files:**
- Create: `web/src/components/profile/ProfileCover.vue`

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { EditPen, Check, Close } from '@element-plus/icons-vue'
import type { CoverConfig } from '@/utils/coverConfig'

const props = withDefaults(defineProps<{
  imageUrl?: string
  config: CoverConfig
  editable?: boolean
  onSave?: (config: CoverConfig) => Promise<void>
}>(), {
  imageUrl: '',
  editable: false,
})

const SUNSET = 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)'

const editing = ref(false)
const saving = ref(false)
const draft = reactive<CoverConfig>({ fit: 'cover', x: 50, y: 50, height: 320 })

const active = computed<CoverConfig>(() => (editing.value ? draft : props.config))
const hasImage = computed(() => !!props.imageUrl)
const coverRef = ref<HTMLElement | null>(null)

const clampNum = (n: number, min: number, max: number) => Math.max(min, Math.min(max, n))

const photoStyle = computed(() => ({ backgroundImage: `url("${props.imageUrl}")` }))
const fillStyle = computed(() => ({
  backgroundImage: `url("${props.imageUrl}")`,
  backgroundPosition: `${active.value.x}% ${active.value.y}%`,
}))
const containerStyle = computed(() => ({
  height: `${active.value.height}px`,
  background: hasImage.value ? 'var(--el-fill-color-light)' : SUNSET,
}))

const emitInfo = (msg: string) => emit('notice', msg)
const emit = defineEmits<{ (e: 'notice', msg: string): void }>()

const enterEdit = () => {
  if (!hasImage.value) { emitInfo('请先到「设置」上传资料卡背景图'); return }
  draft.fit = props.config.fit
  draft.x = props.config.x
  draft.y = props.config.y
  draft.height = props.config.height
  editing.value = true
}
const cancelEdit = () => { editing.value = false }
const toggleFit = () => { draft.fit = draft.fit === 'cover' ? 'contain' : 'cover' }
const saveEdit = async () => {
  if (!props.onSave) { editing.value = false; return }
  saving.value = true
  try {
    await props.onSave({ ...draft })
    editing.value = false
  } catch {
    // 失败保留编辑态;错误提示由父层负责
  } finally {
    saving.value = false
  }
}

let posDrag: { px: number; py: number; x: number; y: number } | null = null
const onCoverPointerDown = (e: PointerEvent) => {
  if (!editing.value || draft.fit !== 'cover' || !hasImage.value) return
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  posDrag = { px: e.clientX, py: e.clientY, x: draft.x, y: draft.y }
}
const onCoverPointerMove = (e: PointerEvent) => {
  if (!posDrag) return
  const box = coverRef.value
  if (!box) return
  const dx = e.clientX - posDrag.px
  const dy = e.clientY - posDrag.py
  draft.x = clampNum(posDrag.x - (dx / (box.clientWidth || 1)) * 100, 0, 100)
  draft.y = clampNum(posDrag.y - (dy / (box.clientHeight || 1)) * 100, 0, 100)
}
const onCoverPointerUp = () => { posDrag = null }

let hDrag: { py: number; h: number } | null = null
const onHeightPointerDown = (e: PointerEvent) => {
  if (!editing.value) return
  e.stopPropagation()
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  hDrag = { py: e.clientY, h: draft.height }
}
const onHeightPointerMove = (e: PointerEvent) => {
  if (!hDrag) return
  draft.height = clampNum(hDrag.h + (e.clientY - hDrag.py), 120, 600)
}
const onHeightPointerUp = () => { hDrag = null }
</script>

<template>
  <div
    ref="coverRef"
    class="cover"
    :style="containerStyle"
    :class="{ 'is-editing': editing, 'is-grab': editing && active.fit === 'cover' && hasImage }"
    @pointerdown="onCoverPointerDown"
    @pointermove="onCoverPointerMove"
    @pointerup="onCoverPointerUp"
    @pointercancel="onCoverPointerUp"
  >
    <div v-if="hasImage && active.fit === 'cover'" class="cover-fill" :style="fillStyle"></div>
    <template v-else-if="hasImage">
      <div class="cover-blur" :style="photoStyle"></div>
      <div class="cover-photo" :style="photoStyle"></div>
    </template>

    <button v-if="editable && !editing" type="button" class="cover-edit-btn" @click.stop="enterEdit">
      <el-icon><EditPen /></el-icon><span>编辑封面</span>
    </button>

    <div v-if="editing" class="cover-toolbar" @pointerdown.stop>
      <el-button size="small" @click="toggleFit">{{ draft.fit === 'cover' ? '填满' : '完整' }}</el-button>
      <span class="cover-hint">{{ draft.fit === 'cover' ? '拖动图片调整位置' : '完整模式无需定位' }}</span>
      <el-button size="small" :icon="Close" @click="cancelEdit">取消</el-button>
      <el-button size="small" type="primary" :icon="Check" :loading="saving" @click="saveEdit">保存</el-button>
    </div>

    <div
      v-if="editing"
      class="cover-resize"
      @pointerdown="onHeightPointerDown"
      @pointermove="onHeightPointerMove"
      @pointerup="onHeightPointerUp"
      @pointercancel="onHeightPointerUp"
    ></div>
  </div>
</template>

<style scoped>
.cover { position: relative; border-radius: 10px 10px 0 0; overflow: hidden; }
.cover-fill { position: absolute; inset: 0; background-size: cover; background-repeat: no-repeat; }
.cover-blur, .cover-photo { position: absolute; inset: 0; background-position: center; background-repeat: no-repeat; }
.cover-blur { background-size: cover; filter: blur(22px) brightness(0.92); transform: scale(1.12); }
.cover-photo { background-size: contain; }
.cover.is-grab .cover-fill { cursor: grab; }
.cover.is-editing { outline: 2px dashed var(--el-color-primary); outline-offset: -2px; }
.cover-edit-btn {
  position: absolute; right: 12px; bottom: 12px; z-index: 2;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: none; border-radius: 999px; cursor: pointer;
  background: rgba(0, 0, 0, 0.45); color: #fff; font-size: 12px; font-weight: 600;
}
.cover-edit-btn:hover { background: rgba(0, 0, 0, 0.6); }
.cover-toolbar {
  position: absolute; left: 12px; right: 12px; bottom: 12px; z-index: 3;
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 10px; border-radius: 10px; background: rgba(0, 0, 0, 0.5);
}
.cover-hint { color: #fff; font-size: 12px; margin-right: auto; }
.cover-resize {
  position: absolute; left: 0; right: 0; bottom: 0; height: 12px; z-index: 3;
  cursor: ns-resize; background: linear-gradient(transparent, rgba(0, 0, 0, 0.25));
}
@media (max-width: 640px) {
  .cover { border-radius: 0; max-height: 260px; }
}
</style>
```

> 设计说明:`onSave` 是异步回调(由父层做 API + store 更新);成功才退出编辑,失败保留编辑态。`notice` 事件用于"未上传图片"提示(父层 toast)。`updateById` 只更新非 null 列,无需担心清空其它字段。

- [ ] **Step 2: 验证**

Run: `cd web && npm run build:check`
Expected: `ProfileCover.vue` 不在错误里;总数 ≤ 60。
Run: `cd web && npm run build`
Expected: EXIT 0(组件暂未被引用也应类型通过)。

- [ ] **Step 3: 提交**

```bash
git add web/src/components/profile/ProfileCover.vue
git commit -m "feat(profile): ProfileCover render + inline cover editor"
```

---

## Task 5: 接入 ProfileHeader / MePage / UserProfilePage

**Files:**
- Modify: `web/src/components/profile/ProfileHeader.vue`
- Modify: `web/src/pages/MePage.vue`
- Modify: `web/src/pages/UserProfilePage.vue`

- [ ] **Step 1: ProfileHeader 用 ProfileCover 替换内联封面**

`ProfileHeader.vue` `<script setup>`:
1. 顶部加 import:

```ts
import ProfileCover from '@/components/profile/ProfileCover.vue'
import { parseCoverConfig, type CoverConfig } from '@/utils/coverConfig'
```

2. `ProfileHeaderData` 接口加一行(在 `profileCardBgUrl?: string` 后):

```ts
  profileCardBgUrl?: string
  coverConfig?: string
```

3. props 增加 `editable` 与 `onSaveCover`(在 `levelHint?` 后,defaults 里补默认):

```ts
const props = withDefaults(defineProps<{
  profile: ProfileHeaderData
  variant: 'self' | 'other'
  isFollowing?: boolean
  followLoading?: boolean
  levelProgress?: number | null
  levelHint?: string
  editable?: boolean
  onSaveCover?: (config: CoverConfig) => Promise<void>
}>(), {
  isFollowing: false,
  followLoading: false,
  levelProgress: null,
  levelHint: '',
  editable: false,
})
```

4. 删除旧封面计算属性 `coverUrl` / `coverBaseStyle` / `coverImageStyle` 和常量 `SUNSET`;替换为:

```ts
const coverImageUrl = computed(() => {
  const url = String(props.profile.profileCardBgUrl || '').trim()
  const ok = /^https?:\/\/[^"'\s]+$/.test(url) || /^\/uploads\/[^"'\s]+$/.test(url)
  return ok ? url : ''
})
const parsedCover = computed(() => parseCoverConfig(props.profile.coverConfig))
const onCoverNotice = (msg: string) => ElMessage.info(msg)
```

5. 顶部确保引入 `ElMessage`(若未引入):

```ts
import { ElMessage } from 'element-plus'
```

`ProfileHeader.vue` `<template>`:把原来的封面块

```vue
    <div class="ph-cover" :style="coverBaseStyle">
      <template v-if="coverUrl">
        <div class="ph-cover-blur" :style="coverImageStyle"></div>
        <div class="ph-cover-photo" :style="coverImageStyle"></div>
      </template>
    </div>
```

整体替换为:

```vue
    <ProfileCover
      :image-url="coverImageUrl"
      :config="parsedCover"
      :editable="editable"
      :on-save="onSaveCover"
      @notice="onCoverNotice"
    />
```

`ProfileHeader.vue` `<style scoped>`:删除已迁移到 ProfileCover 的封面样式 `.ph-cover`、`.ph-cover-blur, .ph-cover-photo`、`.ph-cover-blur`、`.ph-cover-photo` 四条,以及底部 `@media (max-width: 640px) { .ph-cover { height: 220px; border-radius: 0; } }` 这条(ProfileCover 自带圆角与移动端处理)。其余样式保留。

- [ ] **Step 2: MePage 接入 editable + 保存**

`MePage.vue` `<script setup>`:
1. import 增补:

```ts
import { userApi } from '@/api/user'
import type { CoverConfig } from '@/utils/coverConfig'
```
(`userApi` 多数已引入;若已存在勿重复。)

2. `headerProfile` computed 内补一行:

```ts
  profileCardBgUrl: userStore.userInfo?.profileCardBgUrl,
  coverConfig: userStore.userInfo?.coverConfig,
```

3. 新增保存处理(放在 `fetchLevel` 附近):

```ts
const handleSaveCover = async (cfg: CoverConfig) => {
  try {
    const res = await userApi.updateCover(cfg)
    if (userStore.userInfo) {
      userStore.userInfo.coverConfig = res.data ?? JSON.stringify(cfg)
    }
    ElMessage.success('封面已更新')
  } catch (e) {
    ElMessage.error('封面保存失败')
    throw e
  }
}
```
(`ElMessage` 已在 MePage 引入。)

`MePage.vue` `<template>`:给自己页的 `ProfileHeader` 补两个属性:

```vue
      <ProfileHeader
        :profile="headerProfile"
        variant="self"
        :level-progress="levelInfo?.progress ?? null"
        :level-hint="levelHint"
        :editable="true"
        :on-save-cover="handleSaveCover"
        @edit="router.push('/settings')"
        @compose="composerStore.open()"
        @stat-click="onStatClick"
      />
```

- [ ] **Step 3: UserProfilePage 确认只读**

`UserProfilePage.vue` 无需改动:两处 `ProfileHeader` 未传 `editable`(默认 false),`profile`(`UserPublicProfile`)在 Task 3 后已含 `coverConfig`,会自动透传给 ProfileCover 渲染。确认 `<ProfileHeader>` 调用处未误加 editable 即可。

- [ ] **Step 4: 验证**

Run: `cd web && npm run build:check`
Expected: ProfileHeader/MePage/UserProfilePage 均不在错误里;总数 ≤ 60。
Run: `cd web && npm run build`
Expected: EXIT 0。

- [ ] **Step 5: 手测**

`/me`:封面右下「编辑封面」→ 进入编辑(虚线框 + 工具条)→ 拖动图片改焦点(填满模式)→ 拖底边改高度 → 切「完整/填满」→ 保存:toast「封面已更新」、立即生效、刷新保持;取消:回滚。无背景图时点编辑 → 提示去设置上传。`/user/:id`:看到对方调过的封面,但**无编辑入口**。

- [ ] **Step 6: 提交**

```bash
git add web/src/components/profile/ProfileHeader.vue web/src/pages/MePage.vue web/src/pages/UserProfilePage.vue
git commit -m "feat(profile): inline cover editing on /me via ProfileCover"
```

---

## Task 6: 暗色/响应式/回归收尾

**Files:**
- Modify: 上述组件 scoped 样式(按需)

- [ ] **Step 1: 暗色与响应式走查**

`html.dark` 下:编辑工具条/按钮(半透明黑底 + 白字)对比度正常;`var(--el-fill-color-light)` letterbox 兜底在暗色下不刺眼;虚线框用 `--el-color-primary`。窄屏(≤640)封面 `max-height:260`、无圆角、工具条 `flex-wrap` 不溢出。如有问题就地调样式。

- [ ] **Step 2: 回归**

`cd web && npm run build:check`(总数 ≤ 60、改动文件零报错)+ `cd web && npm run build`(EXIT 0)+ `mvn -q -DskipTests compile`(SUCCESS)。手测矩阵:自己编辑保存/取消/刷新保持;他人只读看到效果;cover↔contain;无图提示;暗色;移动端;非法/空 config 走默认不报错。

- [ ] **Step 3: 提交(若有改动)**

```bash
git add web/src/components/profile/ProfileCover.vue web/src/components/profile/ProfileHeader.vue
git commit -m "polish(profile): cover editor dark-mode/responsive pass"
```

---

## 自检(对照 spec)

- **覆盖:** 持久化字段→T1;读两接口→T1;写接口→T2;前端类型/解析/API→T3;渲染+编辑组件→T4;接入三页→T5;暗色/响应式→T6。**spec 各节均有对应任务。**
- **类型一致性:** `CoverConfig`(T3)被 ProfileCover(T4)、ProfileHeader/MePage(T5)一致消费;`updateCover` 入参 `{fit,x,y,height}`(T3)与后端 `CoverConfigUpdateReq`(T2)、`handleSaveCover`(T5)一致;`coverConfig` 字段名贯穿实体/两 DTO/两前端接口一致。
- **占位符:** 无 TODO;每处给出确切文件、锚点行与完整代码。
- **已知取舍:** 拖动定位仅 cover 模式;单 JSON 列;图片上传仍在设置页;默认 `fit=cover` 居中 320。
