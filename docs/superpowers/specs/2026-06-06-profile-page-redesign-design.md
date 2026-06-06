# 个人资料页系统性重设计 · 设计文档

- 日期:2026-06-06
- 范围:`/me`(我的主页)、`/user/:id`(他人主页)的系统性优化
- 技术栈:Vue 3 + Element Plus 2.13 + Pinia + TypeScript + Vite(`web/`)
- 状态:已与用户逐项确认(改造力度 C / IA 精简 A / 单栏 Editorial A / 封面 banner A)

---

## 1. 背景与目标

当前 `/me`(`web/src/pages/MePage.vue`,约 1430 行)把太多职责堆在一页:顶部主题色卡片 + **10 个横向标签**(动态/草稿/打回/回收站/收藏/关注/粉丝/话题/浏览记录/通知)+ 详细资料抽屉。存在 6 个明确问题:

| # | 问题 |
|---|---|
| ❶ | 整页卡片 + 主题色背景,与用户偏好的 Editorial(无卡片/大留白/分割线/Zens-Yellow)不一致 |
| ❷ | 兴趣标签未填时硬编码假数据(Java/Spring Boot/Vue3/Docker),人人相同 |
| ❸ | 头部"关注/粉丝"统计不可点,却又有同名标签页,重复且不一致 |
| ❹ | 10 个标签平铺一行,混了「内容/社交/私密活动/通知」四类,移动端横向滚动 |
| ❺ | 通知中心整套塞进资料页(MePage 的通知 tab 才是"通知中心"本体) |
| ❻ | 学院/专业/年级藏在抽屉;且"学校"字段是 bug(`fetchUserProfile` 从未回填,恒显"未填写") |

**目标:** 让个人主页"更贴合校园社区"——清爽的信息架构、Editorial 视觉、把校园社区相关信息(角色徽章、等级、学院专业年级、真实兴趣)提到台前,并让 `/me` 与 `/user/:id` 共用一套设计。

---

## 2. 范围

**纳入:**
- `/me` 全量重设计:Editorial 单栏 + 信息架构精简(10 → 4 标签)。
- `/user/:id` 共用同一套头部与视觉(他人主页隐藏私密项)。
- 通知中心从 `/me` 抽到独立 `/notifications` 页(不丢功能)。
- 一处小后端改动:扩展 `/user/public/{id}` 返回字段,使他人主页有信息对等。

**不纳入(非目标):**
- 设置页 `/settings` 不改。
- **不改全站共享的 `PostCard` 组件**:`动态/收藏` 列表仍用现有 `PostCard` 渲染(保留贴文操作、与首页/板块页一致)。"无卡片 Editorial"主要作用于**主页框架**(头部、社交/创作列表、空态、分割线、留白),而非贴文卡本身。**(此点见 §11 待确认)**
- 不为他人主页新增"他人的关注/粉丝/话题列表"接口(避免后端扩面);他人主页统计为展示态、不可点。
- 旧的卡片主题调色板(`getCardThemePalette` / `profileCardTheme`)在新头部不再用于卡片背景;`profileCardBgUrl` 改作封面图。数据字段保留。

---

## 3. 已确认的设计决策

1. **改造力度:** 系统性重设计(信息架构 + 视觉)。
2. **信息架构:** 精简重组,10 → 4 个顶级标签。
3. **布局:** 单栏 Editorial 阅读流(桌面/移动一套)。
4. **视觉:** 无卡片、大留白、发丝级分割线、Zens-Yellow 点缀;支持暗色模式。
5. **封面:** 复用 `profileCardBgUrl` 做细长封面 banner,未设置时用淡 Zens-Yellow 渐变兜底。
6. **头部信息:** 角色徽章、等级进度、学院·专业·年级、真实兴趣标签、可点击关注/粉丝;**性别默认不展示**(隐私)。
7. **后端:** 做最小扩展,使他人主页信息对等。

---

## 4. 信息架构(IA)

### `/me` —— 4 个顶级标签

| 顶级标签 | 二级(分段)| 数据来源(均为现有接口)| 可见性 |
|---|---|---|---|
| **动态** | —— | `postApi.searchList({userId, status:1})` | 公开 |
| **收藏** | —— | `postApi.searchList({collectedBy, status:1})` | 仅自己 |
| **关系** | 关注 / 粉丝 / 话题 | `followApi.getMyFollowing` / `getMyFollowers` / `tagApi.getMyFollowing` | 仅自己 |
| **创作管理** | 草稿 / 打回修改 / 回收站 / 浏览记录 | `searchList`(status/auditStatus 区分)/ `viewLogApi.getUserHistoryPaged` | 仅自己 |

- 头部"关注/粉丝"数字 **可点击** → 切到「关系」并定位对应分段(通过 query,如 `?tab=relations&sub=followers`)。
- 「通知」**移出**(见 §6)。

### `/user/:id` —— 仅内容 + 统一头部

- 标签:**动态**(沿用现有 `posts` 列表 + 无限滚动)。
- 不展示 收藏/关系/创作管理/通知(私密或需新接口)。
- 头部:与 `/me` 同款(隐藏自己专属操作);操作 = **关注/已关注 + 私信**。
- `isSelf` 时仍提供"我的主页"入口跳 `/me`(沿用现状)。

---

## 5. 视觉设计系统

- **无卡片:** 去掉 `el-card` 包裹;区块用 `1px var(--el-border-color-lighter)` 分割线 + 充足留白分隔。
- **Zens-Yellow:** 用于激活态标签下划线、`@handle`、等级进度条、关键强调。**实现时确认 `web/src/index.css` 中既有品牌色变量名**(占位回退 `#f4b400`);统一用 CSS 变量,勿硬编码。
- **暗色模式:** 一律走 `--el-*` 变量与品牌色变量,保证既有自定义暗色生效;避免写死 `#fff/#000`。
- **阅读宽度:** 居中,正文列宽建议 `min(100%, ~680–720px)`(现状 `--cp-profile-page-width: 1080px`,Editorial 收窄更聚焦)。
- **封面 banner:** 高约 96px;背景图 = `profileCardBgUrl`(校验过的 http(s)/`/uploads/` 才用),否则 Zens-Yellow 渐变;头像(72px)压在封面下缘、白色描边。
- **内容流:** 列表项之间用分割线,无卡片阴影;贴文项仍由 `PostCard` 承载(见 §2 非目标)。

---

## 6. 通知中心迁移(❺)

现状:`AppTopbar.vue` 铃铛 = 20 条预览 popover,"查看全部通知"跳 `/me?tab=notifications`;真正的通知中心(类型筛选/分组/批量已读/批量删除)在 `MePage` 的 `notifications` tab。

**方案(纯前端,不丢功能):**
1. 新建路由 `/notifications` 与页面 `web/src/pages/NotificationsPage.vue`,把 `MePage` 通知 tab 的全部逻辑/模板迁过去(筛选、分组、批量、单条操作)。
2. 修改 `AppTopbar.vue` 的 `goNotificationCenter()`:`/me?tab=notifications` → `/notifications`。
3. 从 `MePage` 移除 `notifications` tab 及相关状态/方法。
4. 顺手清理迁移中的调试代码 `console.log('通知列表响应:', res)`。
5. DM 铃铛 → `/messages` 维持不变。

---

## 7. 头部规格

**信息(从上到下):**
封面 banner → 头像 → 昵称 + 角色徽章(`UserRoleBadge`,复用)+ 等级胶囊(Lv.N)→ `@username` → 简介(空态:"写点介绍吧")→ 统计(动态 / 关注 / 粉丝)→ 等级进度条(**仅自己**;→ `/connect` 等级中心)→ 校园信息(🎓 学院 · 专业 · 年级)→ 兴趣标签 → 操作区。

**数据来源:**
- `/me`:`userStore.userInfo`(`UserProfile`:含 `school/major/grade/enrollmentYear/interestTags/level/role(s)/profileCardBgUrl`)+ `userApi.getProfileStats()`(三项计数)+ `levelApi.getInfo()`(等级进度)。→ 直接修掉 ❻ 的学校 bug(改读 `userStore.userInfo.school`)。
- `/user/:id`:`userApi.getPublicProfile(id)`(扩展后,见 §9);等级仅显示公开的 `level` 数字(Lv 胶囊),无进度条(`levelApi.getInfo()` 仅返回当前登录用户的经验进度)。

**徽章规则:** `roles[]` → `UserRoleBadge`;`moderatedSectionIds` 非空 → "板块版主"徽章(若后端获取成本高,见 §11 可延后)。

**兴趣标签(修 ❷):** 读真实 `interestTags`(逗号分隔渲染);为空显示"＋ 添加兴趣"引导至 `/settings`;**删除硬编码 Java/Spring Boot/Vue3/Docker 兜底**。

**操作区:**
- 自己:`编辑资料`(→ `/settings`)、`发布动态`(`composerStore.open()`)。
- 他人:`关注/已关注`(`followApi`)、`私信`(`dmApi`/跳 `/messages`)。

---

## 8. 组件拆分(降低 MePage 体量)

当前 `MePage.vue` 过大、职责过多。借本次重构拆分(最终边界在实现计划中定):

- `web/src/components/profile/ProfileHeader.vue` —— 统一头部。
  Props:`profile`(归一化)、`variant: 'self' | 'other'`;Emits:`follow` / `message` / `edit` / `stat-click(type)`。**`/me` 与 `/user/:id` 共用**。
- `web/src/components/profile/RelationsPanel.vue` —— 关注/粉丝/话题分段列表(替换现有大量内联样式)。
- `web/src/components/profile/CreatorPanel.vue` —— 草稿/打回/回收站/浏览记录分段。
- `web/src/components/profile/ProfilePostList.vue`(或复用现有列表)—— 包裹 `PostCard` + `useInfiniteScroll` + `EmptyState`,供 动态/收藏/草稿/打回/回收站 参数化复用。
- `web/src/pages/NotificationsPage.vue` —— 迁移后的通知中心(见 §6)。
- 复用:`Avatar`、`PostCard`、`EmptyState`、`PostListSkeleton`、`UserRoleBadge`、`useInfiniteScroll`。

`MePage.vue` / `UserProfilePage.vue` 收敛为"组装页":取数 + 组合 `ProfileHeader` 与各面板。

---

## 9. 数据与接口

**前端(`web/src/api/user.ts`):** 给 `UserPublicProfile` 增加:`grade?`、`enrollmentYear?`、`interestTags?`、`moderatedSectionIds?`(`roles?` 已有)。

**后端(最小改动):**
- DTO `UserProfileResp`(`/user/public/{id}` 的返回体)增加字段:`grade`、`enrollmentYear`、`interestTags`(`roles` 已有;`moderatedSections` 视成本决定)。
- `UserController.getPublicProfile()`(`src/main/java/.../controller/UserController.java:58`)在构造 `UserProfileResp` 时回填 `user.getGrade()` / `getEnrollmentYear()` / `getInterestTags()`。
- 不改其它接口;`/me` 所需数据全部已存在。

**统一无限滚动:** 全部列表改用 `useInfiniteScroll`(`/user/:id` 已用),替换 `MePage` 的手动"加载更多"。

---

## 10. 行为与交互细节

- **可点击统计:** `/me` 头部 关注/粉丝 → 切「关系」对应分段(query 驱动,保持可分享/可刷新)。`/user/:id` 为展示态。
- **标签 ↔ 路由:** 沿用现有 `?tab=` 同步;新增 `?sub=` 表达二级分段;刷新/前进后退保持。
- **等级:** 进度条 + "距 Lv.N 还差 X 经验",点击 → `/connect`。
- **空态:** 沿用 `EmptyState`,文案区分(无动态/无收藏/无关注/无草稿等),Editorial 风格。
- **加载:** 沿用 `PostListSkeleton` 与骨架屏。
- **校园信息:** 读 `userStore.userInfo`;缺字段则该项不渲染(不显示"未填写"占位)。性别不展示。

---

## 11. 待确认问题(用户审阅时定)

1. **贴文表现形式:** 推荐 `动态/收藏` 继续用共享 `PostCard`(一致性 + 保留草稿/回收站等操作)。若坚持贴文也走"无卡片 Editorial 行",则需新建主页专用列表项并放弃部分操作 —— 工作量与全站一致性代价更大。**默认:保留 PostCard。**
2. **通知中心去向:** 推荐新建独立 `/notifications` 页。**默认:新建。**
3. **板块版主徽章:** 若后端取 `moderatedSections` 成本低则纳入;否则首版先只做 `roles` 徽章,版主徽章延后。

---

## 12. 测试策略

- **类型/构建:** `cd web && npm run build`(`vue-tsc -b`)通过。
- **手动验证矩阵:**
  - `/me`:有/无简介、有/无兴趣、普通用户/版主/管理员;4 标签与二级分段切换;可点击统计跳转;无限滚动;空态。
  - `/user/:id`:他人主页头部信息对等(扩展字段生效);`isSelf` 跳 `/me`;关注/私信。
  - 通知:`/notifications` 功能完整(筛选/分组/批量);铃铛"查看全部"跳转正确;`/me` 不再有通知 tab。
  - 暗色模式不破版;移动端(≤640px)单栏正常;封面有图/无图兜底。
- 项目未见前端单测框架,遵循现状以类型检查 + 手动验证为主。

---

## 13. 实现顺序建议(详见后续实现计划)

1. 后端:`UserProfileResp` + `UserController.getPublicProfile` 扩展字段;前端 `UserPublicProfile` 接口同步。
2. `ProfileHeader.vue`(含封面/徽章/统计/等级/校园信息/兴趣/操作),先接入 `/user/:id` 验证。
3. 通知中心迁移到 `/notifications` + 顶栏链接更新 + `MePage` 移除通知 tab。
4. `/me` 重构:接入 `ProfileHeader` + 4 标签(动态/收藏/关系/创作管理)+ 各面板组件 + 统一无限滚动。
5. 视觉打磨:无卡片化、分割线、Zens-Yellow、暗色与响应式。
6. 清理:删假兴趣兜底、调试 `console.log`、内联样式收敛。
