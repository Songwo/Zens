# 评论软删除 + 三天物理清理 + 前端 action 菜单 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `sys_comment` 从硬删除改为"软删 + 3 天物理清理"，并在前端补齐评论 action 菜单（删除/举报/复制链接）与软删占位渲染。

**Architecture:** 后端复用 `Post` 软删模式（`audit_status='DELETED'` + `update_time`），独立 `CommentCleanupTask` 在每日 04:30 物理清理过期评论；前端在 `CommentList.vue` 增加 dropdown action 菜单与占位 UI，`PostDetailPage.vue` 注入权限 prop 并响应 emit 事件。

**Tech Stack:** Spring Boot 3 / MyBatis-Plus / Spring Scheduling / Vue 3 + TypeScript / Element Plus

**Spec:** `docs/superpowers/specs/2026-05-28-comment-soft-delete-design.md`

---

## 文件改动清单

**新增：**
- `src/main/resources/sql/migrations/2026-05-28-add-comment-soft-delete.sql`
- `src/main/java/com/campus/trend/campus_pulse/scheduled/CommentCleanupTask.java`

**修改：**
- `src/main/resources/sql/campus_pulse_schema.sql` — `sys_comment` CREATE 语句新增两列
- `src/main/java/com/campus/trend/campus_pulse/entity/Comment.java` — 新增 `auditStatus`、`updateTime`
- `src/main/java/com/campus/trend/campus_pulse/dto/response/CommentResp.java` — 新增 `auditStatus`
- `src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java` — 重写 `deleteComment`、改 `getCommentsByPostId`
- `web/src/types/index.ts` — `Comment.auditStatus?: string`
- `web/src/components/comment/CommentList.vue` — props、action dropdown、占位、样式
- `web/src/pages/PostDetailPage.vue` — handler、props 注入、举报 dialog 兼容评论

---

## Phase 1 ｜ 后端：软删数据模型

### Task 1: 创建数据库迁移 SQL

**Files:**
- Create: `src/main/resources/sql/migrations/2026-05-28-add-comment-soft-delete.sql`

- [ ] **Step 1: 创建迁移文件**

文件内容：

```sql
-- ============================================================
-- 评论软删除字段迁移：2026-05-28
-- 给 sys_comment 加 audit_status + update_time，对齐 Post 软删模式
-- ============================================================

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

- [ ] **Step 2: 在本地数据库执行迁移**

```bash
# 用户在自己的 MySQL 客户端执行（或通过 IDEA Database 工具）
# mysql -u <user> -p <db_name> < src/main/resources/sql/migrations/2026-05-28-add-comment-soft-delete.sql
```

- [ ] **Step 3: 验证表结构**

```sql
DESC sys_comment;
-- 期望看到新增的 audit_status / update_time 两列，以及 idx_comment_audit_update 索引
SHOW INDEX FROM sys_comment WHERE Key_name = 'idx_comment_audit_update';
```

- [ ] **Step 4: 同步更新 schema 主文件**

修改 `src/main/resources/sql/campus_pulse_schema.sql` 中 `CREATE TABLE sys_comment` 的定义，把列与索引补齐：

```sql
CREATE TABLE `sys_comment` (
  `id`            varchar(64)    NOT NULL,
  `post_id`       varchar(64)    NOT NULL COMMENT '帖子ID',
  `user_id`       varchar(64)    NOT NULL COMMENT '评论用户ID',
  `content`       varchar(1000)  NOT NULL COMMENT '评论内容',
  `parent_id`     varchar(64)    DEFAULT '0' COMMENT '父评论ID，0表示顶层评论',
  `reply_user_id` varchar(64)    DEFAULT NULL COMMENT '被回复用户ID',
  `is_anonymous`  tinyint(1)     DEFAULT 0 COMMENT '是否匿名',
  `like_count`    int            DEFAULT 0 COMMENT '点赞数',
  `audit_status`  varchar(16)    NOT NULL DEFAULT 'APPROVED' COMMENT '审核/删除状态: APPROVED|DELETED',
  `create_time`   datetime       DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间(软删 3 天倒计时基准)',
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`, `create_time`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_post_parent_time` (`post_id`, `parent_id`, `create_time`),
  KEY `idx_comment_user_time` (`user_id`, `create_time`),
  KEY `idx_comment_reply_user_time` (`reply_user_id`, `create_time`),
  KEY `idx_comment_audit_update` (`audit_status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';
```

> 找到现有 `CREATE TABLE sys_comment` (`campus_pulse_schema.sql:253-268`)，整段替换；
> `reply_user_id` 之后/`is_anonymous` 之前的内容保持不动。

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/sql/migrations/2026-05-28-add-comment-soft-delete.sql src/main/resources/sql/campus_pulse_schema.sql
git commit -m "feat(sql): add audit_status + update_time to sys_comment for soft delete"
```

---

### Task 2: 扩展 Comment 实体与响应 DTO

**Files:**
- Modify: `src/main/java/com/campus/trend/campus_pulse/entity/Comment.java`
- Modify: `src/main/java/com/campus/trend/campus_pulse/dto/response/CommentResp.java`

- [ ] **Step 1: 给 Comment 实体加字段**

在 `Comment.java` 现有 `private Integer likeCount;` 行之后、`private LocalDateTime createTime;` 之前插入：

```java
    /**
     * 审核/删除状态: APPROVED | DELETED
     */
    private String auditStatus;
```

在 `private LocalDateTime createTime;` 之后插入：

```java
    /**
     * 最后更新时间(软删 3 天倒计时基准)
     */
    private LocalDateTime updateTime;
```

- [ ] **Step 2: 给 CommentResp 加字段**

在 `CommentResp.java` 的 `private Integer likeCount;` 之后插入：

```java
    private String auditStatus;
```

- [ ] **Step 3: 编译通过**

```bash
mvn -q -DskipTests compile
```

期望：BUILD SUCCESS，无编译错误。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/campus/trend/campus_pulse/entity/Comment.java src/main/java/com/campus/trend/campus_pulse/dto/response/CommentResp.java
git commit -m "feat(comment): add auditStatus/updateTime to entity and response DTO"
```

---

## Phase 2 ｜ 后端：软删服务层

### Task 3: 重写 deleteComment 为软删

**Files:**
- Modify: `src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java`

- [ ] **Step 1: 在 CommentServiceImpl 顶部 import 增加 `LambdaUpdateWrapper`**（如果尚未导入）

确认这两个 import 已存在或添加：

```java
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
```

- [ ] **Step 2: 新增私有权限判定方法 `canManageComment`**

在 `CommentServiceImpl` 类内部（建议放在 `isCommentablePost` 上方）新增：

```java
    private boolean canManageComment(Comment comment, Post post, String userId) {
        if (comment == null || userId == null || userId.isBlank()) {
            return false;
        }
        if (userId.equals(comment.getUserId())) {
            return true;                                                      // 评论作者
        }
        if (post != null && userId.equals(post.getUserId())) {
            return true;                                                      // 帖子作者
        }
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId)) {
            return true;                                                      // 管理员/超管/全局版主
        }
        if (post != null && post.getSectionId() != null
                && sectionModeratorService.canModerateSection(userId, post.getSectionId())) {
            return true;                                                      // 板块版主
        }
        return false;
    }
```

- [ ] **Step 3: 重写 `deleteComment`**

替换 `CommentServiceImpl.java:142-162` 的整段 `deleteComment` 方法为：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId, String userId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.FAILED, "评论不存在");
        }

        // 幂等：已软删直接返回，避免重复减 comment_count
        if ("DELETED".equalsIgnoreCase(comment.getAuditStatus())) {
            return;
        }

        Post post = sysPostMapper.selectById(comment.getPostId());
        if (!canManageComment(comment, post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权删除该评论");
        }

        // 软删：仅更新状态与时间戳
        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
                .set(Comment::getAuditStatus, "DELETED")
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getId, commentId)
                .update();

        // 评论数 -1 + 刷新 Redis 版本
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            sysPostMapper.updateById(post);
            invalidatePostFeedCache(post.getSectionId(), post.getId());
        }
    }
```

- [ ] **Step 4: 编译验证**

```bash
mvn -q -DskipTests compile
```

期望：BUILD SUCCESS。

- [ ] **Step 5: 手测一遍 happy path（启动应用前先做下面 Task 4，再统一测）**

跳过手测；进入下一个 Task。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java
git commit -m "feat(comment): soft-delete instead of hard delete; expand delete permission"
```

---

### Task 4: 评论列表查询过滤软删评论

**Files:**
- Modify: `src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java`

- [ ] **Step 1: 改造 `getCommentsByPostId` 方法**

替换 `CommentServiceImpl.java:164-221`（`getCommentsByPostId` 整段方法）为：

```java
    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<CommentResp> getCommentsByPostId(String postId,
            Integer pageNo, Integer pageSize) {
        Post post = sysPostMapper.selectById(postId);
        String currentUserId = SecurityUtils.getCurrentUserId();
        if (post == null || !canViewHiddenPostComments(currentUserId, post)) {
            return new Page<CommentResp>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        // 分页只查"未删根评论"
        Page<Comment> page = new Page<>(pageNo, pageSize);
        Page<Comment> rootPage = this.page(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getParentId, "0")
                .ne(Comment::getAuditStatus, "DELETED")
                .orderByDesc(Comment::getCreateTime));

        List<Comment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return new Page<CommentResp>(pageNo, pageSize).setRecords(Collections.emptyList());
        }

        // 一次性拉取该帖所有评论（含已删），用于构建子树
        List<Comment> allComments = this.list(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getPostId, postId)
                .orderByAsc(Comment::getCreateTime));

        // 收集"被作为父引用过的 id 集合"——这些已删评论需要保留为占位
        Set<String> parentIdsThatHaveChildren = new HashSet<>();
        for (Comment item : allComments) {
            String pid = item.getParentId();
            if (pid != null && !pid.isBlank() && !"0".equals(pid)) {
                parentIdsThatHaveChildren.add(pid);
            }
        }

        // 过滤可见集合 + 把已删评论的 content 抹掉
        Map<String, Comment> visibleById = new HashMap<>();
        for (Comment item : allComments) {
            boolean isDeleted = "DELETED".equalsIgnoreCase(item.getAuditStatus());
            if (!isDeleted || parentIdsThatHaveChildren.contains(item.getId())) {
                if (isDeleted) {
                    item.setContent(null);
                }
                visibleById.put(item.getId(), item);
            }
        }

        // 仅在 visible 集合内构建 children
        Map<String, List<Comment>> childrenByParentId = new HashMap<>();
        for (Comment item : visibleById.values()) {
            String parentId = item.getParentId();
            if (parentId == null || parentId.isBlank() || "0".equals(parentId)) {
                continue;
            }
            if (!visibleById.containsKey(parentId)) {
                continue;
            }
            childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(item);
        }

        List<CommentResp> responseList = roots.stream().map(root -> {
            attachChildrenTree(root, childrenByParentId, new HashSet<>());
            return mapToResponse(root);
        }).collect(Collectors.toList());

        fillUserInfo(responseList);

        Page<CommentResp> resultPage = new Page<>(pageNo, pageSize);
        resultPage.setTotal(rootPage.getTotal());
        resultPage.setPages(rootPage.getPages());
        resultPage.setCurrent(rootPage.getCurrent());
        resultPage.setRecords(responseList);

        try {
            AuthUser authUser = SecurityUtils.getAuthenticatedUser();
            if (authUser != null) {
                String likeStatusUserId = authUser.getUser().getId();
                fillLikeStatus(responseList, likeStatusUserId);
            }
        } catch (Exception ignored) {
        }

        return resultPage;
    }
```

- [ ] **Step 2: 确认 `mapToResponse` 已正确拷贝 `auditStatus`**

查看 `CommentServiceImpl.java:296-307`，`mapToResponse` 使用 `BeanUtils.copyProperties(comment, response);`，自动会拷 `auditStatus` —— 无需修改。验证：

```java
private CommentResp mapToResponse(Comment comment) {
    CommentResp response = new CommentResp();
    BeanUtils.copyProperties(comment, response);
    // ...
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn -q -DskipTests compile
```

期望：BUILD SUCCESS。

- [ ] **Step 4: 启动应用并冒烟测试**

```bash
mvn -q spring-boot:run
```

在另一终端 / Postman / IDEA HTTP Client 中：
1. `GET /comment/post/<existingPostId>?page=1&size=10` —— 应返回正常评论列表，每条带 `auditStatus: "APPROVED"`
2. `DELETE /comment/<id>` (带登录 JWT) —— 应返回 success
3. 再次 `GET /comment/post/<existingPostId>` —— 已删评论：若无子评论则消失；若有子评论则 `content: null` + `auditStatus: "DELETED"` 占位
4. 查库 `SELECT id, audit_status, update_time FROM sys_comment WHERE id = '<id>';` —— 应为 `DELETED` + 最近时间

停掉应用：`Ctrl+C`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/campus/trend/campus_pulse/service/impl/CommentServiceImpl.java
git commit -m "feat(comment): filter soft-deleted comments in list; keep deleted parents as placeholders"
```

---

## Phase 3 ｜ 后端：3 天自动物理删除任务

### Task 5: 新建 CommentCleanupTask 定时任务

**Files:**
- Create: `src/main/java/com/campus/trend/campus_pulse/scheduled/CommentCleanupTask.java`

- [ ] **Step 1: 创建定时任务类**

文件内容：

```java
package com.campus.trend.campus_pulse.scheduled;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.trend.campus_pulse.entity.Comment;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务：物理清理软删超过 3 天的评论。
 * 与 PostCleanupTask 错开 30 分钟执行，避免对同一帖子的并发写竞争。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentCleanupTask {

    private final CommentMapper commentMapper;

    /**
     * 每天 04:30 执行
     */
    @Scheduled(cron = "0 30 4 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredDeletedComments() {
        log.info("[定时任务] 开始扫描软删除超过 3 天的评论...");
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

- [ ] **Step 2: 验证 `@Scheduled` 已启用（已确认无需改动）**

`CampusPulseApplication.java:12` 已有 `@EnableScheduling`，新建的 `@Scheduled` 方法将自动被调度，**本步骤无需任何修改**，确认即可。

- [ ] **Step 3: 编译验证**

```bash
mvn -q -DskipTests compile
```

期望：BUILD SUCCESS。

- [ ] **Step 4: 手工触发任务验证**

启动应用：

```bash
mvn -q spring-boot:run
```

在数据库手工构造一条 3 天前的软删评论：

```sql
-- 1. 找一条已存在的评论或先发一条新评论
-- 2. 软删它（通过接口）或直接 SQL 改：
UPDATE sys_comment
   SET audit_status = 'DELETED',
       update_time = DATE_SUB(NOW(), INTERVAL 4 DAY)
 WHERE id = '<some_comment_id>';
```

在 IDEA 里临时把 cron 改成 `0/30 * * * * ?`（每 30 秒一次）或者用 Spring Boot Actuator / 直接在 controller 中加临时 endpoint 触发，等待执行；观察日志：

```
[定时任务] 开始扫描软删除超过 3 天的评论...
[定时任务] 物理删除 1 条过期评论
```

数据库验证：

```sql
SELECT * FROM sys_comment WHERE id = '<some_comment_id>';
-- 期望：无记录
```

完成后**改回原 cron `0 30 4 * * ?`**。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/campus/trend/campus_pulse/scheduled/CommentCleanupTask.java
git commit -m "feat(scheduled): add CommentCleanupTask to physically delete soft-deleted comments after 3 days"
```

---

## Phase 4 ｜ 前端：评论 action 菜单 + 深链 + 软删占位

### Task 6: 扩展 Comment 类型定义

**Files:**
- Modify: `web/src/types/index.ts`

- [ ] **Step 1: 在 Comment interface 中新增 `auditStatus`**

修改 `web/src/types/index.ts:150-165` 的 `Comment` interface，在 `isAnonymous?: number` 后插入：

```ts
    auditStatus?: string
```

最终 interface：

```ts
export interface Comment {
    id: string
    parentId: string
    content: string
    userId: string
    nickname: string
    roles?: string[]
    userAvatar?: string
    likeCount: number
    createTime: string
    isLiked?: boolean
    children?: Comment[]
    replyUserId?: string
    replyUserNickname?: string
    isAnonymous?: number
    auditStatus?: string
}
```

- [ ] **Step 2: 类型检查**

```bash
cd web && npm run type-check
```

如果没有 `type-check` script，使用：

```bash
cd web && npx vue-tsc --noEmit
```

期望：无类型错误。

- [ ] **Step 3: Commit**

```bash
git add web/src/types/index.ts
git commit -m "feat(types): add auditStatus to Comment interface"
```

---

### Task 7: CommentList.vue 增加 action 菜单与软删占位

**Files:**
- Modify: `web/src/components/comment/CommentList.vue`

- [ ] **Step 1: 扩展 icon import 与 props/emits**

替换 `CommentList.vue:1-18` 这一段：

```vue
<script setup lang="ts">
import { ChatLineRound, Coordinate } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import { mdComment } from '@/utils/markdownRenderer'
import { warmupHighlighter, preloadLanguages } from '@/utils/shiki'

const props = defineProps<{
  comments: any[]
  activeCommentId?: string | null
}>()

const emit = defineEmits<{
  (e: 'like', comment: any): void
  (e: 'reply', comment: any): void
}>()
```

替换为：

```vue
<script setup lang="ts">
import { ChatLineRound, Coordinate, More, Delete, Warning, Link } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { ElMessage, ElMessageBox } from 'element-plus'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import { mdComment } from '@/utils/markdownRenderer'
import { warmupHighlighter, preloadLanguages } from '@/utils/shiki'

const props = defineProps<{
  comments: any[]
  activeCommentId?: string | null
  postId: string
  postShortId?: string
  postAuthorId?: string | null
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

- [ ] **Step 2: 在 `<script setup>` 末尾（`getSortedChildren` 函数下方、`</script>` 前面）追加权限计算与 action handler**

在 `CommentList.vue:141` 这一行（`}` 之后、`</script>` 之前）插入：

```ts
const isDeletedComment = (c: any) => c?.auditStatus === 'DELETED'

const canDelete = (c: any) => {
  if (!props.currentUserId) return false
  if (isDeletedComment(c)) return false
  return c.userId === props.currentUserId
      || (props.postAuthorId && props.postAuthorId === props.currentUserId)
      || props.isAdmin === true
      || props.canModerateSection === true
}

const canReport = (c: any) => {
  if (!props.currentUserId) return false
  if (isDeletedComment(c)) return false
  return c.userId !== props.currentUserId
}

const buildCommentLink = (commentId: string) => {
  const shortId = props.postShortId || props.postId
  return `${location.origin}/t/${shortId}?commentId=${commentId}`
}

const handleAction = async (cmd: string, comment: any) => {
  if (cmd === 'copy') {
    const link = buildCommentLink(comment.id)
    try {
      await navigator.clipboard.writeText(link)
      ElMessage.success('评论链接已复制')
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = link
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      ElMessage.success('评论链接已复制')
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(
        '确认删除该评论吗？删除后 3 天内可由管理员恢复，超过 3 天将永久清除。',
        '删除评论',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
          confirmButtonClass: 'el-button--danger',
        }
      )
      emit('delete', comment)
    } catch {
      // 取消，无操作
    }
  } else if (cmd === 'report') {
    emit('report', comment)
  }
}
```

- [ ] **Step 3: 修改根评论 `<template>` 内容渲染部分**

定位 `CommentList.vue:182-188`：

```vue
        <div class="comment-text markdown-body" :class="{ collapsed: isCollapsed(comment) }" v-html="sanitizeCommentHtml(comment.content)"></div>
        <div v-if="isLongComment(comment.content)" class="expand-wrap">
          <el-button link type="primary" size="small" @click.stop="toggleExpand(comment)">
            {{ isCollapsed(comment) ? '展开全文' : '收起' }}
          </el-button>
        </div>
```

替换为：

```vue
        <template v-if="isDeletedComment(comment)">
          <div class="comment-deleted-placeholder">
            <el-icon><Delete/></el-icon>
            <span>该评论已删除</span>
          </div>
        </template>
        <template v-else>
          <div class="comment-text markdown-body" :class="{ collapsed: isCollapsed(comment) }" v-html="sanitizeCommentHtml(comment.content)"></div>
          <div v-if="isLongComment(comment.content)" class="expand-wrap">
            <el-button link type="primary" size="small" @click.stop="toggleExpand(comment)">
              {{ isCollapsed(comment) ? '展开全文' : '收起' }}
            </el-button>
          </div>
        </template>
```

- [ ] **Step 4: 给根评论 `comment-actions` 加 dropdown 与已删隐藏**

定位 `CommentList.vue:189-204`（`<div class="comment-actions">` 整段）：

```vue
        <div class="comment-actions">
          <div class="actions-group">
            <el-button
              link
              :type="comment.isLiked ? 'primary' : 'info'"
              size="small"
              :icon="Coordinate"
              @click.stop="emit('like', comment)"
            >
              {{ getLikes(comment) > 0 ? getLikes(comment) : '点赞' }}
            </el-button>
            <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', comment)">
              回复
            </el-button>
          </div>
        </div>
```

替换为：

```vue
        <div v-if="!isDeletedComment(comment)" class="comment-actions">
          <div class="actions-group">
            <el-button
              link
              :type="comment.isLiked ? 'primary' : 'info'"
              size="small"
              :icon="Coordinate"
              @click.stop="emit('like', comment)"
            >
              {{ getLikes(comment) > 0 ? getLikes(comment) : '点赞' }}
            </el-button>
            <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', comment)">
              回复
            </el-button>
          </div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, comment)">
            <el-button link size="small" :icon="More" class="action-trigger" @click.stop/>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="copy">
                  <el-icon><Link/></el-icon>&nbsp;复制评论链接
                </el-dropdown-item>
                <el-dropdown-item v-if="canReport(comment)" command="report">
                  <el-icon><Warning/></el-icon>&nbsp;举报
                </el-dropdown-item>
                <el-dropdown-item v-if="canDelete(comment)" command="delete" divided>
                  <el-icon><Delete/></el-icon>&nbsp;删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
```

- [ ] **Step 5: 子评论与孙评论同样改造**

定位 `reply-item`（二级回复，`CommentList.vue:209-250` 左右）的 `reply-text` 块：

```vue
                <div class="reply-text markdown-body" :class="{ collapsed: isCollapsed(child) }" v-html="sanitizeCommentHtml(child.content)"></div>
                <div v-if="isLongComment(child.content)" class="expand-wrap">
                  <el-button link type="primary" size="small" @click.stop="toggleExpand(child)">
                    {{ isCollapsed(child) ? '展开全文' : '收起' }}
                  </el-button>
                </div>
                <div class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="child.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', child)"
                    >
                      {{ getLikes(child) > 0 ? getLikes(child) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', child)">
                      回复
                    </el-button>
                  </div>
                </div>
```

替换为：

```vue
                <template v-if="isDeletedComment(child)">
                  <div class="comment-deleted-placeholder">
                    <el-icon><Delete/></el-icon>
                    <span>该评论已删除</span>
                  </div>
                </template>
                <template v-else>
                  <div class="reply-text markdown-body" :class="{ collapsed: isCollapsed(child) }" v-html="sanitizeCommentHtml(child.content)"></div>
                  <div v-if="isLongComment(child.content)" class="expand-wrap">
                    <el-button link type="primary" size="small" @click.stop="toggleExpand(child)">
                      {{ isCollapsed(child) ? '展开全文' : '收起' }}
                    </el-button>
                  </div>
                </template>
                <div v-if="!isDeletedComment(child)" class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="child.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', child)"
                    >
                      {{ getLikes(child) > 0 ? getLikes(child) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', child)">
                      回复
                    </el-button>
                  </div>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, child)">
                    <el-button link size="small" :icon="More" class="action-trigger" @click.stop/>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">
                          <el-icon><Link/></el-icon>&nbsp;复制评论链接
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canReport(child)" command="report">
                          <el-icon><Warning/></el-icon>&nbsp;举报
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canDelete(child)" command="delete" divided>
                          <el-icon><Delete/></el-icon>&nbsp;删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
```

对 `grandChild` 块（`CommentList.vue:253-298` 左右）做同样改造：把 `<div class="reply-text...>` 包成 `template v-if="isDeletedComment(grandChild)">` 占位 / `<template v-else>` 原渲染，把 `<div class="comment-actions">` 加 `v-if="!isDeletedComment(grandChild)"`，并在内部加 dropdown（替换 `child` 为 `grandChild`）。

- [ ] **Step 6: 追加样式**

在 `<style scoped>` 末尾（`@keyframes comment-focus` 之后、`</style>` 之前）追加：

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

.comment-actions {
  position: relative;
}

.action-trigger {
  opacity: 0.5;
  transition: opacity 0.2s;
  margin-left: auto;
}

.comment-item:hover .action-trigger,
.reply-item:hover .action-trigger {
  opacity: 1;
}
```

- [ ] **Step 7: 类型检查与构建验证**

```bash
cd web && npx vue-tsc --noEmit
```

期望：无类型错误。

- [ ] **Step 8: Commit**

```bash
git add web/src/components/comment/CommentList.vue
git commit -m "feat(comment-ui): add action menu (delete/report/copy-link) and soft-delete placeholder"
```

---

### Task 8: PostDetailPage.vue 注入 props、wire handlers、兼容举报评论

**Files:**
- Modify: `web/src/pages/PostDetailPage.vue`

- [ ] **Step 1: 新增 reportTarget 状态**

定位 `PostDetailPage.vue:474-479`（`reportVisible` / `reportForm` 声明附近）：

```ts
const reportVisible = ref(false)
const isReporting = ref(false)
const reportForm = ref({
  reason: '',
  details: ''
})
```

替换为：

```ts
const reportVisible = ref(false)
const isReporting = ref(false)
const reportForm = ref({
  reason: '',
  details: ''
})
const reportTarget = ref<{ type: 'post' | 'comment'; id: string }>({ type: 'post', id: '' })
```

- [ ] **Step 2: 导入 reportApi（如果尚未导入）**

定位 `PostDetailPage.vue:6-10` 附近的 import 区。检查是否已 `import { reportApi } from '@/api/report'`。若无，在 `import { commentApi } from '@/api/comment'` 之后追加：

```ts
import { reportApi } from '@/api/report'
```

- [ ] **Step 3: 改造 `handleCommand('report')` 与 `submitReport`**

定位 `PostDetailPage.vue:527-534`（`handleCommand` 中 report 分支）：

```ts
  if (command === 'report') {
    if (!userStore.accessToken) {
      ElMessage.warning('请先登录后再进行举报')
      return
    }
    reportForm.value.reason = ''
    reportForm.value.details = ''
    reportVisible.value = true
  }
```

替换为：

```ts
  if (command === 'report') {
    if (!userStore.accessToken) {
      ElMessage.warning('请先登录后再进行举报')
      return
    }
    reportTarget.value = { type: 'post', id: postId.value }
    reportForm.value.reason = ''
    reportForm.value.details = ''
    reportVisible.value = true
  }
```

定位 `PostDetailPage.vue:602-622`（`submitReport`）：

```ts
const submitReport = async () => {
  if (!reportForm.value.reason) {
    ElMessage.warning('请选择举报理由')
    return
  }
  isReporting.value = true
  try {
    await postApi.report({
      targetType: 'post',
      targetId: postId.value,
      reason: reportForm.value.reason,
      details: reportForm.value.details
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    reportVisible.value = false
  } catch (error) {
    ElMessage.error('举报提交失败')
  } finally {
    isReporting.value = false
  }
}
```

替换为：

```ts
const submitReport = async () => {
  if (!reportForm.value.reason) {
    ElMessage.warning('请选择举报理由')
    return
  }
  if (!reportTarget.value.id) {
    ElMessage.warning('举报对象缺失')
    return
  }
  isReporting.value = true
  try {
    await reportApi.create({
      targetType: reportTarget.value.type,
      targetId: reportTarget.value.id,
      reason: reportForm.value.reason,
      details: reportForm.value.details
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    reportVisible.value = false
  } catch (error) {
    ElMessage.error('举报提交失败')
  } finally {
    isReporting.value = false
  }
}
```

- [ ] **Step 4: 新增评论删除 / 举报 handler**

在 `PostDetailPage.vue:443`（`cancelReply` 函数之后）插入：

```ts
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

const handleCommentReport = (comment: any) => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再进行举报')
    return
  }
  reportTarget.value = { type: 'comment', id: comment.id }
  reportForm.value.reason = ''
  reportForm.value.details = ''
  reportVisible.value = true
}
```

- [ ] **Step 5: 修改 `<CommentList>` 模板 props 与 events**

定位 `PostDetailPage.vue:971-976`：

```vue
          <CommentList 
            :comments="(comments as any)"
            :active-comment-id="activeCommentId"
            @like="handleCommentLike"
            @reply="handleReply"
          />
```

替换为：

```vue
          <CommentList 
            :comments="(comments as any)"
            :active-comment-id="activeCommentId"
            :post-id="postId"
            :post-short-id="encodePostId(postId)"
            :post-author-id="post?.userId"
            :current-user-id="userStore.userId"
            :is-admin="isAdmin || isGlobalModerator"
            :can-moderate-section="canModerateCurrentSection"
            @like="handleCommentLike"
            @reply="handleReply"
            @delete="handleCommentDelete"
            @report="handleCommentReport"
          />
```

- [ ] **Step 6: 类型检查**

```bash
cd web && npx vue-tsc --noEmit
```

期望：无类型错误。

- [ ] **Step 7: 启动前端 dev 服务 + 后端冒烟**

终端 1：

```bash
mvn -q spring-boot:run
```

终端 2：

```bash
cd web && npm run dev
```

浏览器打开 `http://localhost:5173/t/<existingPostId>`：

1. 在评论旁应看到 `…` icon dropdown
2. 自己的评论 dropdown 中应有：复制链接 / 删除（无举报）
3. 他人评论 dropdown 中应有：复制链接 / 举报（无删除，普通用户）
4. 帖子作者看自己帖子下他人评论：复制链接 / 举报 / 删除
5. 点"复制链接"→ 粘贴 URL → 应是 `http://localhost:5173/t/<postId>?commentId=<id>`
6. 点"删除"→ 弹窗确认 → 该评论变成"该评论已删除"占位 + 评论数 -1
7. 点"举报"→ 弹出举报 dialog → 选择理由提交 → 成功

停掉 dev / 后端。

- [ ] **Step 8: Commit**

```bash
git add web/src/pages/PostDetailPage.vue
git commit -m "feat(post-detail): wire comment action menu (delete/report/copy-link) and soft-delete state"
```

---

## Phase 5 ｜ 验证

### Task 9: 端到端验证

**Files:** 无修改

- [ ] **Step 1: SQL 基础校验**

```sql
-- 表结构
DESC sys_comment;
-- 必须包含 audit_status (VARCHAR 16, NOT NULL, DEFAULT 'APPROVED')、update_time (DATETIME)
-- 必须包含 idx_comment_audit_update 索引

-- 数据校验
SELECT COUNT(*) AS approved_count FROM sys_comment WHERE audit_status = 'APPROVED';
-- 应等于历史评论总数（迁移前的所有评论默认是 APPROVED）

SELECT COUNT(*) FROM sys_comment WHERE audit_status = 'DELETED';
-- 应为 0（除非测试中软删过）
```

- [ ] **Step 2: 权限矩阵 E2E**

启动后端 + 前端 dev。准备三个测试账号（A/B/Admin）和一个已发帖 P（作者 A）+ 评论 C1（作者 B）+ 评论 C2（作者 A）。

| 操作者 | 评论 | 期望 dropdown 项 |
|--------|------|-----------------|
| B（评论作者） | C1 | 复制 / 删除 |
| A（帖子作者，他人评论） | C1 | 复制 / 举报 / 删除 |
| A（自己评论） | C2 | 复制 / 删除 |
| C（路人登录） | C1 | 复制 / 举报 |
| C（路人登录） | C2 | 复制 / 举报 |
| 未登录 | C1 | 复制（仅，无举报/删除） |
| Admin | C1 | 复制 / 举报 / 删除 |

逐个核验，记录通过/失败。

- [ ] **Step 3: 占位策略 E2E**

1. 准备：评论 C1 下有子评论 C1a。
2. 删除 C1（用 B 账号）。
3. 刷新页面：C1 显示"该评论已删除"占位，C1a 仍可见。
4. 再删 C1a。
5. 刷新：因为 C1 已无可见后代 → C1 应整段消失（不再做占位）。
6. 后端日志/数据库 sys_comment 中 C1、C1a 仍存在（仅软删）。

- [ ] **Step 4: 深链 E2E**

1. 在评论 dropdown 点"复制链接"。
2. 新开标签页粘贴。
3. 期望：跳转到 `/t/<shortId>?commentId=<id>`，目标评论高亮（is-active 动画一次）。
4. 评论已被软删：链接打开后看到占位（不报错）。

- [ ] **Step 5: 定时任务 E2E（可选 / 时间充裕时）**

```sql
-- 改一条软删评论的 update_time 为 4 天前
UPDATE sys_comment
   SET update_time = DATE_SUB(NOW(), INTERVAL 4 DAY)
 WHERE audit_status = 'DELETED'
 LIMIT 1;
```

临时把 `CommentCleanupTask` 的 cron 改为 `0/30 * * * * ?`（30 秒一次），重启后端，观察日志：

```
[定时任务] 物理删除 N 条过期评论
```

验证：

```sql
SELECT * FROM sys_comment WHERE id = '<刚才改的 id>';
-- 应为空
```

**改回 cron `0 30 4 * * ?`**，提交（如果改过 cron 但忘了改回来不要提交）。

- [ ] **Step 6: 回归冒烟**

1. 发新评论 / 回复 / 点赞 → 流程正常
2. 帖子软删（已有功能）→ 7 天后 PostCleanupTask 仍能级联硬删该帖下所有评论（含未到 3 天的软删评论）
3. 评论通知点击 → 跳转到帖子页 + commentId 高亮

- [ ] **Step 7: 最终 Commit（如有任何小修补）**

```bash
git status
# 如果有遗留改动
git add <files>
git commit -m "chore: final tweaks for comment soft-delete verification"
```

---

## 完成 Checklist

- [ ] Phase 1：迁移 SQL 已执行 + schema.sql 同步 + 实体/DTO 已加字段
- [ ] Phase 2：`deleteComment` 软删 + 权限四档 + `getCommentsByPostId` 过滤与占位
- [ ] Phase 3：`CommentCleanupTask` 已注册并通过手测
- [ ] Phase 4：`CommentList.vue` action 菜单 + 占位渲染；`PostDetailPage.vue` props/handlers/举报 dialog 兼容
- [ ] Phase 5：权限矩阵 + 占位策略 + 深链 + 定时任务全部 E2E 通过

---

## 风险提醒

- **迁移 SQL 不可重入**：若已在某环境跑过一次，再次跑会因列已存在而失败 —— 部署到正式环境时需要先 `SHOW COLUMNS FROM sys_comment LIKE 'audit_status'` 检查
- **cron 临时改动**：Task 5 Step 4 / Task 9 Step 5 都涉及临时修改 cron 后**必须改回**，否则线上行为异常
- **占位节点不计入分页 total**：用户翻页时可能看到"单页项数 > size"，符合设计但需运营/产品知情
