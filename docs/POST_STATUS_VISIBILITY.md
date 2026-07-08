# 帖子状态与可见性口径

## 两层状态

`status` 表示发布生命周期：

- `0`：草稿/非发布态。
- `1`：已提交到发布流，但不代表已经公开。

`audit_status` 表示审核/治理状态：

- `DRAFT`：草稿。
- `PENDING`：待审核/待版务确认，不公开。
- `APPROVED`：审核通过，可公开。
- `REJECTED`：打回修改，不公开。
- `DELETED`：回收站/软删除，不公开。

历史数据里 `audit_status` 为空或 `NULL` 时，按 `APPROVED` 兼容处理。

## 可见性规则

公开可见必须同时满足：

```text
status = 1
AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED')
```

`PENDING` 只允许以下角色查看：

- 作者本人。
- 管理员。
- 全局版主。
- 对应板块版主。

普通搜索、首页、热门、推荐、标签计数、Agent 检索、短链、点赞/收藏、举报入口都不得返回或操作 `PENDING`。

## 状态流转

- 新用户/低信任用户发帖：`status=1` + `audit_status=PENDING`。
- 可信用户发帖：`status=1` + `audit_status=APPROVED`。
- 草稿保存：`status=0` + `audit_status=DRAFT`。
- 待审或打回后重新提交：保持或进入 `PENDING`，不能绕过审核直接 `APPROVED`。
- 版务通过：`status=1` + `audit_status=APPROVED`，再同步搜索索引和公共通知。
- 版务打回：`status=0` + `audit_status=REJECTED`，从搜索索引移除。
- 删除：`status=0` + `audit_status=DELETED`，从搜索索引移除。
- 举报自治隐藏：`APPROVED` 转 `PENDING`，等待版务确认，并从公开入口隐藏，同时清理搜索索引和帖子缓存。

## 检索口径

- 主站 MySQL 搜索、Meilisearch hydration、Agent MySQL 检索必须使用同一公开可见条件。
- Meilisearch 只索引公开可见帖子；收到非公开帖子同步请求时删除对应文档。
- 匿名帖子详情缓存只能缓存和返回公开可见帖子，命中旧的非公开缓存时必须重新回源校验。
- 管理后台和作者中心可以按 `audit_status` 查询非公开内容，但这些查询必须依赖权限上下文。
