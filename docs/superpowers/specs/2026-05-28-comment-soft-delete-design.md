# 评论软删除 + 三天物理清理 + 前端 action 菜单 设计文档

- 日期：2026-05-28
- 作者：SongWo
- 范围：`sys_comment` 数据模型 / `CommentService` / 定时任务 / 前端 `CommentList`、`PostDetailPage`
- 状态：待实施

## 1. 背景与动机

当前 `CommentServiceImpl.deleteComment` 直接执行 `removeById`（硬删除），与帖子模块"软删 + 7 天物理清理"的策略不一致：

- 评论一旦误删无法恢复
- 后台审计无回溯
- 前端 `CommentList.vue` 没有 action 菜单，缺少删除 / 举报 / 复制链接入口
- 评论深链 (`?commentId=xxx`) 已工作，但无前端入口生成

本次改造把评论对齐到帖子的软删模式，并在前端补齐 action 菜单与软删占位。

## 2. 整体策略

- **数据模型**：与 `Post` 对齐，使用 `audit_status` + `update_time`
- **保留期**：3 天（短于帖子的 7 天，因为评论体量更大、运营关注度更低）
- **权限模型**：评论作者 ∨ 帖子作者 ∨ 板块版主 ∨ ROLE_ADMIN/SUPER_ADMIN
- **可见性策略**：已删评论若有子评论则保留为占位节点，否则在列表中过滤；评论数立即 -1
- **物理清理**：独立的 `CommentCleanupTask`，每天 04:30 跑，扫描 `audit_status='DELETED' AND update_time <= now() - 3d` 的评论 → `deleteBatchIds`

## 3. Phase 1 ｜ 后端：软删数据模型

### 3.1 SQL 迁移 (`campus_pulse_schema.sql` + 单独 patch SQL)

```sql
ALTER TABLE `sys_comment`
  ADD COLUMN `audit_status` varchar(16) NOT NULL DEFAULT 'APPROVED'
      COMMENT '审核/删除状态: APPROVED|DELETED'
      AFTER `like_count`,
  ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP
      COMMENT '最后更新时间(软删 3 天倒计时基准)'
      AFTER `create_time`,
  ADD INDEX `idx_comment_audit_update` (`audit_status`, `update_time`);
```

注意：
- `audit_status` 默认值 `APPROVED`，存量评论无需回填
- `update_time` 默认 `CURRENT_TIMESTAMP` + `ON UPDATE CURRENT_TIMESTAMP`，存量评论 `update_time` 初始化为本次 ALTER 时间（可接受，因为只用作软删期判断）
- 主 schema 文件 `campus_pulse_schema.sql` 同步更新 `CREATE TABLE` 语句以保持新部署一致

### 3.2 实体类 (`Comment.java`)

```java
private String auditStatus;          // APPROVED | DELETED
private LocalDateTime updateTime;
```

### 3.3 响应 DTO (`CommentResp.java`)

```java
private String auditStatus;          // 前端用以决定渲染占位
```

## 4. Phase 2 ｜ 后端：软删服务层

### 4.1 `CommentServiceImpl.deleteComment` 重写

```java
@Transactional
public void deleteComment(String commentId, String userId) {
    Comment comment = getById(commentId);
    if (comment == null) {
        throw new BusinessException(ResultCode.FAILED, "评论不存在");
    }
    // 幂等：已软删直接返回
    if ("DELETED".equalsIgnoreCase(comment.getAuditStatus())) {
        return;
    }
    Post post = sysPostMapper.selectById(comment.getPostId());

    if (!canManageComment(comment, post, userId)) {
        throw new BusinessException(ResultCode.NO_PERMISSION, "无权删除该评论");
    }

    // 软删
    LocalDateTime now = LocalDateTime.now();
    lambdaUpdate()
        .set(Comment::getAuditStatus, "DELETED")
        .set(Comment::getUpdateTime, now)
        .eq(Comment::getId, commentId)
        .update();

    // post.comment_count -= 1，刷新 Redis 版本
    if (post != null) {
        post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
        sysPostMapper.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getId());
    }
}

private boolean canManageComment(Comment comment, Post post, String userId) {
    if (userId == null) return false;
    if (userId.equals(comment.getUserId())) return true;                      // 评论作者
    if (post != null && userId.equals(post.getUserId())) return true;         // 帖子作者
    if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId)) {
        return true;                                                          // ROLE_ADMIN/SUPER_ADMIN/全局版主
    }
    if (post != null && post.getSectionId() != null
            && sectionModeratorService.canModerateSection(userId, post.getSectionId())) {
        return true;                                                          // 板块版主
    }
    return false;
}
```

### 4.2 评论列表过滤 (`getCommentsByPostId`)

**核心难点**：已删评论若有子评论需要保留为占位节点，否则直接过滤。

实现策略：

1. 一次性拉取该帖**全部**评论（含已删，本帖体量可控）
2. 构建 `childrenByParentId` Map
3. 构建 `parentIdsWithChildren` Set —— 哪些已删评论需要保留
4. 过滤可见集合：
   - 未删评论 → 全部保留
   - 已删评论 → 仅当其 id ∈ `parentIdsWithChildren` 时保留，且 `content = null`、`replyUserNickname` 保留
5. 在保留集合中重建根/子结构，渲染时把"子全是已删 + 自身已删"的子树整体丢弃

```java
List<Comment> all = list(Wrappers.<Comment>lambdaQuery()
    .eq(Comment::getPostId, postId)
    .orderByAsc(Comment::getCreateTime));

Set<String> parentIdsThatHaveChildren = all.stream()
    .map(Comment::getParentId)
    .filter(pid -> pid != null && !"0".equals(pid))
    .collect(Collectors.toSet());

List<Comment> visible = all.stream()
    .filter(c -> !"DELETED".equalsIgnoreCase(c.getAuditStatus())
              || parentIdsThatHaveChildren.contains(c.getId()))
    .peek(c -> {
        if ("DELETED".equalsIgnoreCase(c.getAuditStatus())) {
            c.setContent(null);   // 不下发已删内容
        }
    })
    .collect(Collectors.toList());

// 后续 attachChildrenTree / mapToResponse 基于 visible 列表
```

**根评论分页**：仅按"未删根评论"分页 (`audit_status != 'DELETED' AND parent_id = '0'`)，子评论一次性挂载。

### 4.3 通知 / 深链兼容

- `notificationService.sendCommentNotification` 当前已生成 `relatedId = postId + '#comment-' + commentId`，无需改动
- 评论 3 天后物理删除，旧通知点开 → 前端 `?commentId=xxx` 找不到 anchor → 复用现有重试逻辑（最多 4 次 180ms）→ 静默 fallback 到帖子顶部，行为可接受

## 5. Phase 3 ｜ 后端：3 天自动物理删除任务

### 5.1 新建 `CommentCleanupTask`

文件：`src/main/java/com/campus/trend/campus_pulse/scheduled/CommentCleanupTask.java`

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentCleanupTask {

    private final CommentMapper commentMapper;

    @Scheduled(cron = "0 30 4 * * ?")   // 04:30，与 PostCleanupTask 错开 30 分钟
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredDeletedComments() {
        log.info("[定时任务] 扫描软删除超过 3 天的评论...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);

        List<Comment> expired = commentMapper.selectList(
            Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getAuditStatus, "DELETED")
                .le(Comment::getUpdateTime, threshold)
        );
        if (expired.isEmpty()) {
            log.info("[定时任务] 无需清理的过期评论");
            return;
        }

        List<String> ids = expired.stream().map(Comment::getId).collect(Collectors.toList());
        commentMapper.deleteBatchIds(ids);
        log.info("[定时任务] 物理删除 {} 条过期评论", ids.size());
    }
}
```

### 5.2 范围限定

- **不**清理评论点赞 Redis key (`comment:like:{id}:{uid}`)：这些 key 无 TTL，是历史包袱，本期不动；可后续在另一个任务中扫描清理孤儿 key
- **不**和 `PostCleanupTask` 冲突：`PostCleanupTask` 在帖子 7 天后会硬删该帖下所有评论（含未到 3 天的软删评论），这是合理的：帖子整个没了，评论也没意义保留

## 6. Phase 4 ｜ 前端：评论 action 菜单 + 深链 + 软删占位

### 6.1 `CommentList.vue` 改造

#### 6.1.1 props 扩展

```ts
const props = defineProps<{
  comments: any[]
  activeCommentId?: string | null
  postId: string                  // 用于复制深链
  postShortId?: string            // 短链 ID（encodePostId 后）
  postAuthorId?: string | null    // 用于权限判断
  currentUserId?: string | null
  isAdmin?: boolean
  canModerateSection?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', comment: any): void
  (e: 'reply', comment: any): void
  (e: 'delete', comment: any): void
  (e: 'report', comment: any): void
}>()
```

#### 6.1.2 权限计算

```ts
const canDelete = (c: any) => {
  if (!props.currentUserId) return false
  if (c.auditStatus === 'DELETED') return false
  return c.userId === props.currentUserId
      || props.postAuthorId === props.currentUserId
      || props.isAdmin === true
      || props.canModerateSection === true
}
const canReport = (c: any) => {
  if (!props.currentUserId) return false
  if (c.auditStatus === 'DELETED') return false
  return c.userId !== props.currentUserId
}
```

#### 6.1.3 模板增量

在每个 `comment-meta` / `reply-meta` 的 `meta-right` 处加 dropdown：

```vue
<el-dropdown trigger="click" @command="(cmd) => handleAction(cmd, comment)">
  <el-button link size="small" :icon="More" class="action-trigger"/>
  <template #dropdown>
    <el-dropdown-menu>
      <el-dropdown-item command="copy">
        <el-icon><Link/></el-icon> 复制评论链接
      </el-dropdown-item>
      <el-dropdown-item v-if="canReport(comment)" command="report">
        <el-icon><Warning/></el-icon> 举报
      </el-dropdown-item>
      <el-dropdown-item v-if="canDelete(comment)" command="delete" divided>
        <el-icon><Delete/></el-icon> 删除
      </el-dropdown-item>
    </el-dropdown-menu>
  </template>
</el-dropdown>
```

`handleAction(cmd, c)`:
- `copy` → `navigator.clipboard.writeText(\`${location.origin}/t/${props.postShortId || props.postId}?commentId=${c.id}\`)` + `ElMessage.success('链接已复制')`
- `delete` → `ElMessageBox.confirm('确认删除该评论吗？删除后 3 天内可由管理员恢复。', '删除评论', ...)` → `emit('delete', c)`
- `report` → `emit('report', c)`（父组件复用现有 report dialog）

#### 6.1.4 软删占位渲染

```vue
<div v-if="comment.auditStatus === 'DELETED'" class="comment-deleted-placeholder">
  <el-icon><Delete/></el-icon>
  <span>该评论已删除</span>
</div>
<template v-else>
  <div class="comment-text markdown-body" v-html="sanitizeCommentHtml(comment.content)"></div>
  <!-- 折叠/展开等原有逻辑 -->
</template>
```

样式（追加到 `<style scoped>`）：

```css
.comment-deleted-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  margin-bottom: 12px;
}
.action-trigger {
  opacity: 0.5;
  transition: opacity 0.2s;
}
.comment-item:hover .action-trigger,
.reply-item:hover .action-trigger {
  opacity: 1;
}
```

#### 6.1.5 已删评论隐藏点赞 / 回复入口

```vue
<div v-if="comment.auditStatus !== 'DELETED'" class="comment-actions">
  <!-- 原 like/reply 按钮 -->
</div>
```

### 6.2 `PostDetailPage.vue` 联动

```ts
// 计算注入 CommentList 的 props
const isCommentAdmin = computed(() => isAdmin.value || isGlobalModerator.value)
const canModerateForComment = computed(() => canModerateCurrentSection.value)

// 删除评论
const handleCommentDelete = async (comment: any) => {
  try {
    await commentApi.delete(comment.id)
    comment.auditStatus = 'DELETED'
    comment.content = null
    if (post.value) {
      const cur = toFiniteMetric(post.value.commentCount) ?? comments.value.length
      post.value.commentCount = Math.max(0, cur - 1)
      syncPostMetricsToLists()
    }
    pulseNotification.info('评论已删除')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// 举报评论：复用 reportVisible / reportForm，targetType 改为动态
const reportTarget = ref<{ type: 'post' | 'comment', id: string }>({ type: 'post', id: '' })
const handleCommentReport = (comment: any) => {
  if (!userStore.accessToken) { ElMessage.warning('请先登录'); return }
  reportTarget.value = { type: 'comment', id: comment.id }
  reportForm.value.reason = ''
  reportForm.value.details = ''
  reportVisible.value = true
}
// submitReport 改造：targetType / targetId 从 reportTarget 取
```

模板：

```vue
<CommentList
  :comments="(comments as any)"
  :active-comment-id="activeCommentId"
  :post-id="postId"
  :post-short-id="encodePostId(postId)"
  :post-author-id="post?.userId"
  :current-user-id="userStore.userId"
  :is-admin="isCommentAdmin"
  :can-moderate-section="canModerateForComment"
  @like="handleCommentLike"
  @reply="handleReply"
  @delete="handleCommentDelete"
  @report="handleCommentReport"
/>
```

### 6.3 API 层

`web/src/api/comment.ts` 已有 `delete(id)`，无需改。Types 中给 `Comment` 添加可选字段 `auditStatus?: string`（若 `web/src/types` 中有定义则更新）。

## 7. Phase 5 ｜ 验证

### 7.1 后端单元（最小可行）

- 软删后 `getById` 仍能取到，且 `auditStatus = DELETED`、`updateTime` 已更新
- 普通用户调 `deleteComment` 对非自己评论 → 抛 `NO_PERMISSION`
- 帖子作者删评论 → 成功，且 `sys_post.comment_count -= 1`
- 重复软删 → 幂等，`comment_count` 不会重复 -1

### 7.2 手工 E2E

1. 启动后端、前端：
   - 后端：IDEA Run / `mvn spring-boot:run`
   - 前端：`cd web && npm run dev`
2. 三身份测试：
   - 评论作者本人：看到删除项 → 删除 → 占位出现 + 评论数 -1
   - 帖子作者：看到删除项（对他人评论）
   - 其他登录用户：看不到删除项，能看到举报项；自己评论看不到举报项
3. 复制评论链接 → 新开标签页粘贴 → 跳转到 `/t/<shortId>?commentId=<id>` → 评论高亮 (`is-active` 动画)
4. 在已删评论下回复 → 仍可见占位 + 自己回复
5. 子评论全是已删的根评论：根评论看不到（被过滤）

### 7.3 SQL 校验

```sql
-- 软删一条评论后
SELECT id, audit_status, update_time FROM sys_comment WHERE id = '<id>';
-- 预期 audit_status='DELETED', update_time 为软删瞬间

-- 模拟过期：手动改老的 update_time
UPDATE sys_comment SET update_time = DATE_SUB(NOW(), INTERVAL 4 DAY) WHERE id = '<id>';
-- 然后手工触发 CommentCleanupTask（IDE 调试 / 改 cron 为近期）
```

### 7.4 回归检查

- 普通发评论、回复、点赞 → 不受影响
- 帖子软删 → 7 天后 `PostCleanupTask` 仍可硬删该帖下所有评论（含软删与未删的）
- 评论通知点击 → 深链跳转正常；评论物理删除后 → 深链 anchor 找不到，前端静默 fallback

## 8. 范围外 / 后续工作

- **评论编辑功能**：本期不实现。预留 UI 入口在 spec 中讨论过但未排期
- **评论恢复**：本期不实现（与帖子恢复对齐需要管理员后台）。若需要：添加 `POST /comment/{id}/restore`，校验角色后 set `audit_status='APPROVED'`
- **评论点赞 Redis 孤儿 key 清理**：作为独立任务，本期不动
- **审计日志**：删除操作未写日志表，后续按需补充

## 9. 风险与回滚

- **风险 1**：`getCommentsByPostId` 分页与可见集合不一致 → "显示条数 < 分页 total"。
  - **对策**：分页 SQL `rootPage` 严格只统计 `audit_status='APPROVED' AND parent_id='0'`，分页 total 等于"可见根评论数"。
  - "有子评论的已删父节点"占位**不是分页根**，而是出现在某条可见根评论的子树中（作为子评论的父）；当它本身就是 `parent_id='0'` 的根时，按下面规则处理：若它有任何未删的后代 → 仍把它作为占位根加入当前页结果尾部；若它整个子树都已删 → 直接丢弃。这是"赠送项"，不计入分页 total。
  - 最坏情况：某页"赠送项"较多导致前端展示条数 > size，可接受（评论场景下视觉差异极小）。
- **风险 2**：`comment_count` 在并发软删时可能竞争。**对策**：当前 `Post.commentCount` 已通过 `updateById` 走全字段更新，本次保持一致；若需强一致可改为 `UPDATE sys_post SET comment_count = comment_count - 1 WHERE id = ? AND comment_count > 0`。
- **回滚**：SQL 变更可逆（DROP COLUMN audit_status, update_time）；服务层若需回退到硬删，从 git 历史还原 `CommentServiceImpl.deleteComment` 即可。

## 10. 文件改动清单

后端：
- `src/main/resources/sql/campus_pulse_schema.sql` — 更新 `sys_comment` CREATE
- 新增 `src/main/resources/sql/migrations/2026-05-28-add-comment-soft-delete.sql`
- `src/main/java/com/campus/trend/campus_pulse/entity/Comment.java` — 新增字段
- `src/main/java/com/campus/trend/campus_pulse/dto/response/CommentResp.java` — 新增字段
- `src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java` — 重写 `deleteComment`、改 `getCommentsByPostId`
- 新增 `src/main/java/com/campus/trend/campus_pulse/scheduled/CommentCleanupTask.java`

前端：
- `web/src/components/comment/CommentList.vue` — props / 模板 / 样式 / 权限计算
- `web/src/pages/PostDetailPage.vue` — handler / props 注入 / report dialog 改造
- `web/src/types/*.ts` — `Comment.auditStatus?: string`（若类型集中维护）
