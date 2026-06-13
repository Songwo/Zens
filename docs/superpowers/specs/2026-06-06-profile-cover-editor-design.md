# 个人资料封面自助编辑 · 设计

> **For agentic workers:** 设计获批后用 superpowers:writing-plans 出实现计划。

**Goal:** 让用户在自己的 `/me` 主页上**内联(WYSIWYG)**调节资料卡封面的**显示方式(填满/完整)**、**焦点位置**、**高度**,保存到后端;本人与他人(`/user/:id`)看到一致的封面效果。

**Background:** 用户已可在「设置」上传 `profileCardBgUrl`。当前 `ProfileHeader` 封面以固定方式渲染(contain + 同图模糊打底,高 320px),展示方式由代码写死。本特性把"展示方式"的决定权交给用户并持久化。

**Approach:** A —— 内联 WYSIWYG 编辑 + 单 JSON 字段持久化。

**Tech Stack:** Vue 3 `<script setup lang="ts">` + Element Plus + Pinia + Vue Router;后端 Spring Boot(record DTO)+ MyBatis-Plus + MySQL。

---

## 范围

**In:**
- 后端:`user.cover_config`(JSON 字符串)读写;新增 `PUT /user/cover` 写接口;`getProfile`(自己)/`getPublicProfile`(他人)读返回该字段。
- 前端:抽出 `ProfileCover.vue` 承载封面渲染 + 编辑;`ProfileHeader` 改用它;`/me` 自己可进入编辑模式(拖动定位 / 拖高度 / 填充开关 / 保存取消);`api/user.ts` 加 `updateCover` + 类型字段。

**Out(非目标):**
- 背景图**上传**仍在「设置」页,本特性不改上传流程,只调"展示"。
- 不做多图/相册、滤镜、贴纸。
- 他人主页封面**只读**,不可编辑。
- contain 模式不提供焦点定位(整图已全显,无需定位)。

---

## 数据模型 / 后端

**字段:** `user` 表新增可空列 `cover_config VARCHAR(255) NULL`,存 JSON 字符串:
```json
{"fit":"cover","x":50,"y":35,"height":320}
```
- `fit`: `"cover" | "contain"`
- `x`,`y`: 焦点百分比 `0–100`(仅 cover 模式有意义)
- `height`: 封面高度 px

**默认(config 为 null / 解析失败时):** `{ fit: "cover", x: 50, y: 50, height: 320 }`(前端兜底解析,不依赖后端回填默认)。

**读:**
- `UserProfileResp`(record)末尾新增 `String coverConfig`;`UserController` 唯一构造点回填 `user.getCoverConfig()`(与 enrollmentYear 同样的位置参数追加方式)。
- 自己资料读取路径(登录返回 / `getProfile` 填充 `userStore.userInfo` 的那个 DTO,与现有 `profileCardBgUrl` 同源)一并带 `coverConfig`。
- 前端 `UserPublicProfile` 加 `coverConfig?: string`。

**写:** 新增 `PUT /user/cover`,body `{ fit, x, y, height }`:
- 鉴权:当前登录用户,仅改自己。
- 校验:`fit ∈ {cover,contain}`;`x,y` clamp `[0,100]`;`height` clamp `[120,600]`;序列化为 JSON 存 `cover_config`。非法直接 clamp/取默认,不抛 500。
- 返回:更新后的 `coverConfig` 字符串(或 200 ok)。
- **独立小接口**,不复用整份资料更新接口,避免内联保存把其它字段(昵称/简介等)误清空。

**迁移:** `campus_pulse_schema.sql` 给 user 表加列;新增 `sql/migrations/2026-06-06-add-user-cover-config.sql`。

---

## 前端显示:`ProfileCover.vue`(自己/他人同款渲染)

新建 `web/src/components/profile/ProfileCover.vue`,`ProfileHeader` 用它替换当前内联封面 div。

- **Props:** `imageUrl?: string`、`config: { fit, x, y, height }`(父层把 `coverConfig` 解析+套默认后传入)、`editable?: boolean`(默认 false)。
- **渲染:**
  - `fit=cover`:背景填满,`background-position: {x}% {y}%`、`background-size: cover`,高度 = `config.height`。
  - `fit=contain`:双层(模糊 cover 打底 + contain 清晰图居中),即现状效果,高度 = `config.height`。
  - 无 `imageUrl`:落日渐变兜底。
- **暗色/响应式:** 沿用现有 `--el-*` 变量;移动端(≤640)`height` 上限 clamp(如 260)。

---

## 前端编辑(仅自己,在 `/me` 内联)

`editable=true` 时,`ProfileCover` 右下角显示 `✎ 编辑封面`:

- **进入编辑模式:**
  - **拖动封面图** → 改 `x/y`(仅 cover 模式;contain 模式拖动禁用并灰显提示"完整模式无需定位")。
  - **底边手柄拖动** → 改 `height`(clamp `[120,600]`,移动端更紧)。
  - **填充开关** 填满(cover)/ 完整(contain)。
  - **保存 / 取消**。
- 编辑态即实时预览(所见即所得)。
- **保存:** `ProfileCover` `emit('save', config)`;`MePage` 调 `userApi.updateCover(config)` → 成功后更新 `userStore.userInfo.coverConfig` → 立即生效;失败 `ElMessage` 报错并保留编辑态。
- **取消:** 回滚到进入编辑前的 config。
- **无背景图:** 编辑入口提示"先到设置上传背景图"(或隐藏入口),避免对空图调位置。

**组件边界:** `ProfileCover` 自含渲染 + 拖拽/缩放交互,props 入、`save` 事件出;**持久化由 `MePage` 负责**(它持有 `userStore` 与接口)。`UserProfilePage`(他人页)传 `editable=false`,纯展示。`ProfileHeader` 仅负责把 `coverConfig` 解析成 `config` 并透传 `editable`。

---

## 前端 API(`web/src/api/user.ts`)

- `UserPublicProfile` 接口加 `coverConfig?: string`。
- 新增 `updateCover(payload: { fit: 'cover' | 'contain'; x: number; y: number; height: number })` → `api.put('/user/cover', payload)`。
- 新增解析工具(可放 `ProfileCover` 内或 `utils`):`parseCoverConfig(raw?: string): { fit, x, y, height }`,坏值回默认。

---

## 边界与默认

- 老用户/无 config → 默认 `cover` / 居中 / 320px。
- 非法 JSON → 用默认,不报错(前端 parse 容错)。
- 移动端 `height` 上限收紧。
- contain 模式下 `x/y` 不生效,定位 UI 灰显。
- 编辑仅 `/me`(自己);`/user/:id` 永远只读。

---

## 测试 / 验证

- **前端无单测框架:** `npm run build:check`(类型检查,**不得在我改的文件新增错误**,基线约 60 既有)+ `npm run build`(vite 出包)+ 手测。
- **后端:** `mvn -q -DskipTests compile` + 手测 `PUT /user/cover` 及两读接口返回含 `coverConfig`。
- **手测矩阵:** 自己编辑(拖位置/拖高度/切填充)→保存→刷新保持;**他人 `/user/:id` 看到你调过的封面**;cover↔contain 切换;移动端;无图时入口处理;暗色不破版;非法/空 config 走默认。

---

## 非目标 / 取舍

- 拖拽定位仅 cover 模式(contain 全图无需定位)。
- **单 JSON 列**而非离散多列:迁移小、易扩展(以后加滤镜/遮罩不必再迁移);代价是不支持 SQL 维度查询(本特性不需要)。
- 图片上传维持在设置页,不并入本特性。
- 默认 `fit=cover` 而非沿用当前 contain+模糊:让"焦点定位"开箱即用;用户可一键切回"完整"。

---

## 文件清单总览

| 动作 | 文件 | 职责 |
|---|---|---|
| 改 | `src/main/.../entity/User.java` | 加 `coverConfig` 字段(列 `cover_config`) |
| 改 | `src/main/.../dto/response/UserProfileResp.java` | record 末尾加 `String coverConfig` |
| 改 | `src/main/.../controller/UserController.java` | 唯一构造点回填;新增 `PUT /user/cover` |
| 改 | (自己资料 DTO / service,与 profileCardBgUrl 同源) | 回填 `coverConfig` |
| 改 | `src/main/resources/sql/campus_pulse_schema.sql` | user 表加列 |
| 建 | `src/main/resources/sql/migrations/2026-06-06-add-user-cover-config.sql` | 迁移脚本 |
| 改 | `web/src/api/user.ts` | `UserPublicProfile` 加字段;`updateCover` |
| 建 | `web/src/components/profile/ProfileCover.vue` | 封面渲染 + 编辑(props 入 / save 出) |
| 改 | `web/src/components/profile/ProfileHeader.vue` | 用 ProfileCover 替换内联封面;解析 config、透传 editable |
| 改 | `web/src/pages/MePage.vue` | `editable` + 保存(updateCover + 更新 store) |
| 改 | `web/src/pages/UserProfilePage.vue` | 传 `editable=false`(只读) |
