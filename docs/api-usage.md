# Campus Pulse — API 调用示例手册

> 所有接口 base URL: `http://localhost:7800`  
> 前端 axios baseURL = `/api`（Vite proxy 去掉 `/api` 前缀后转发到后端）  
> 需鉴权接口请在 Header 中添加 `Authorization: Bearer <token>`

---

## 1. 认证模块 — `/auth`

### POST /auth/send-code — 发送验证码
```bash
curl -X POST http://localhost:7800/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```
```ts
// 前端
authApi.sendOtp('user@example.com')
```

### POST /auth/verify-code — 校验验证码
```bash
curl -X POST http://localhost:7800/auth/verify-code \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","code":"123456"}'
```
```ts
authApi.verifyOtp('user@example.com', '123456')
```

### POST /auth/check-email — 检查邮箱是否已注册
```bash
curl -X POST http://localhost:7800/auth/check-email \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```
```ts
authApi.checkEmail('user@example.com')
// 返回 { data: { exists: boolean, username?: string } }
```

### GET /auth/check-username — 检查用户名是否可用
```bash
curl http://localhost:7800/auth/check-username?username=testuser
```
```ts
authApi.checkUsername('testuser')
// 返回 { data: { available: boolean, message?: string } }
```

### POST /auth/login — 登录
```bash
curl -X POST http://localhost:7800/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","code":"123456"}'
```
```ts
authApi.login({ email: 'user@example.com', code: '123456' })
// 返回 { data: { accessToken, refreshToken } }
```

### POST /auth/register — 注册
```bash
curl -X POST http://localhost:7800/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"pass123","email":"user@example.com","code":"123456"}'
```
```ts
authApi.register({ username: 'newuser', password: 'pass123', email: 'user@example.com', code: '123456' })
```

### POST /auth/logout — 登出 🔒
```bash
curl -X POST http://localhost:7800/auth/logout \
  -H "Authorization: Bearer <token>"
```
```ts
authApi.logout()
```

---

## 2. 公告模块 — `/announcement`

### GET /announcement/pending-popup — 获取待显示弹窗
```bash
curl http://localhost:7800/announcement/pending-popup \
  -H "Authorization: Bearer <token>"
```
```ts
announcementApi.getPendingPopup()
```

### POST /announcement/mark-seen/{id} — 标记弹窗已读 🔒
```bash
curl -X POST http://localhost:7800/announcement/mark-seen/1 \
  -H "Authorization: Bearer <token>"
```
```ts
announcementApi.markSeen(1)
```

### GET /announcement/list — 获取公告列表（分页）
```bash
curl "http://localhost:7800/announcement/list?page=1&pageSize=10"
```
```ts
announcementApi.getList(1, 10)
// 返回 { data: { records: Announcement[], total: number } }
```

### POST /announcement/save — 发布公告 🔒管理员
```bash
curl -X POST http://localhost:7800/announcement/save \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"title":"公告标题","content":"公告内容","type":"popup","isActive":1}'
```

---

## 3. 帖子模块 — `/post`

### GET /post/{id} — 获取帖子详情
```bash
curl http://localhost:7800/post/abc123
```
```ts
postApi.getDetail('abc123')
```

### POST /post/search-lists — 搜索帖子列表
```bash
curl -X POST http://localhost:7800/post/search-lists \
  -H "Content-Type: application/json" \
  -d '{"page":1,"pageSize":20,"orderBy":"new","status":1}'
```
```ts
postApi.searchList({ page: 1, pageSize: 20, orderBy: 'new', status: 1 })
// 返回 { data: { records: Post[], total, pages } }
```

### POST /post/create-post — 创建帖子 🔒
```bash
curl -X POST http://localhost:7800/post/create-post \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"title":"标题","content":"内容","categoryId":"cat1","tags":"Go,Java"}'
```
```ts
postApi.create({ title: '标题', content: '内容', categoryId: 'cat1', tags: 'Go,Java' })
```

### POST /post/update-post — 更新帖子 🔒
```bash
curl -X POST http://localhost:7800/post/update-post \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"id":"abc123","title":"新标题","content":"新内容"}'
```
```ts
postApi.update({ id: 'abc123', title: '新标题', content: '新内容' })
```

### POST /post/extract-tags — AI 提取标签
```bash
curl -X POST http://localhost:7800/post/extract-tags \
  -H "Content-Type: application/json" \
  -d '{"title":"标题","content":"帖子内容..."}'
```
```ts
postApi.extractTags({ title: '标题', content: '帖子内容...' })
// 返回 { data: { tags: "Go,Java", summary: "..." } }
```

### POST /post/{id}/like — 点赞帖子 🔒
```bash
curl -X POST http://localhost:7800/post/abc123/like \
  -H "Authorization: Bearer <token>"
```
```ts
postApi.like('abc123')
```

### POST /post/{id}/collect — 收藏帖子 🔒
```bash
curl -X POST http://localhost:7800/post/abc123/collect \
  -H "Authorization: Bearer <token>"
```
```ts
postApi.collect('abc123')
```

### DELETE /post/{id} — 删除帖子 🔒
```bash
curl -X DELETE http://localhost:7800/post/abc123 \
  -H "Authorization: Bearer <token>"
```
```ts
postApi.delete('abc123')
```

---

## 4. 评论模块 — `/comment`

### GET /comment/post/{postId} — 获取帖子评论
```bash
curl "http://localhost:7800/comment/post/abc123?page=1&size=10"
```
```ts
commentApi.getByPostId('abc123', 1, 10)
// 返回 { data: { records: Comment[], total } }
```

### POST /comment/create — 创建评论（支持匿名）
```bash
curl -X POST http://localhost:7800/comment/create \
  -H "Content-Type: application/json" \
  -d '{"postId":"abc123","content":"评论内容","isAnonymous":0}'
```
```ts
commentApi.add({ postId: 'abc123', content: '评论内容', isAnonymous: 0 })
```

### DELETE /comment/{id} — 删除评论 🔒
```bash
curl -X DELETE http://localhost:7800/comment/abc123 \
  -H "Authorization: Bearer <token>"
```
```ts
commentApi.delete('abc123')
```

### POST /comment/{id}/like — 点赞评论 🔒
```bash
curl -X POST http://localhost:7800/comment/abc123/like \
  -H "Authorization: Bearer <token>"
```
```ts
commentApi.like('abc123')
```

---

## 5. 分类模块 — `/categories`

### GET /categories/list — 获取所有分类
```bash
curl http://localhost:7800/categories/list
```
```ts
categoryApi.getList()
// 返回 { data: CategoryDetail[] }
```

### GET /categories/{id} — 获取分类详情
```bash
curl http://localhost:7800/categories/cat1
```
```ts
categoryApi.getById('cat1')
```

### GET /categories/{id}/topics — 获取分类下帖子
```bash
curl "http://localhost:7800/categories/cat1/topics?page=1&size=20&sort=latest"
```
```ts
categoryApi.getTopics('cat1', 1, 20, 'latest')
// 返回 { data: { list: TopicPreview[], total, page, size } }
```

---

## 6. 关注模块 — `/follow`

### POST /follow/{userId} — 关注用户 🔒
```bash
curl -X POST http://localhost:7800/follow/user123 \
  -H "Authorization: Bearer <token>"
```
```ts
followApi.follow('user123')
```

### DELETE /follow/{userId} — 取消关注 🔒
```bash
curl -X DELETE http://localhost:7800/follow/user123 \
  -H "Authorization: Bearer <token>"
```
```ts
followApi.unfollow('user123')
```

### GET /follow/is-following/{userId} — 检查是否已关注 🔒
```bash
curl http://localhost:7800/follow/is-following/user123 \
  -H "Authorization: Bearer <token>"
```
```ts
followApi.isFollowing('user123')
// 返回 { data: boolean }
```

### GET /follow/followers/{userId} — 获取粉丝列表 🔒
```bash
curl "http://localhost:7800/follow/followers/user123?page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```
```ts
followApi.getFollowers('user123', 1, 20)
// 返回 { data: { records: [], total } }
```

### GET /follow/following/{userId} — 获取关注列表 🔒
```bash
curl "http://localhost:7800/follow/following/user123?page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```
```ts
followApi.getFollowing('user123', 1, 20)
// 返回 { data: { records: [], total } }
```

---

## 7. 通知模块 — `/notification`

### GET /notification/unread-count — 获取未读数量 🔒
```bash
curl http://localhost:7800/notification/unread-count \
  -H "Authorization: Bearer <token>"
```
```ts
notificationApi.getUnreadCount()
```

### GET /notification/list — 获取通知列表 🔒
```bash
curl "http://localhost:7800/notification/list?page=1&pageSize=10" \
  -H "Authorization: Bearer <token>"
```
```ts
notificationApi.getList(1, 10)
```

### POST /notification/{id}/read — 标记已读 🔒
```bash
curl -X POST http://localhost:7800/notification/1/read \
  -H "Authorization: Bearer <token>"
```
```ts
notificationApi.markAsRead('1')
```

### POST /notification/read-all — 全部已读 🔒
```bash
curl -X POST http://localhost:7800/notification/read-all \
  -H "Authorization: Bearer <token>"
```
```ts
notificationApi.markAllAsRead()
```

### DELETE /notification/{id} — 删除通知 🔒
```bash
curl -X DELETE http://localhost:7800/notification/1 \
  -H "Authorization: Bearer <token>"
```
```ts
notificationApi.delete('1')
```

---

## 8. 板块模块 — `/section`

### GET /section/list — 获取所有板块
```bash
curl http://localhost:7800/section/list
```
```ts
sectionApi.getList()
```

### GET /section/{id} — 获取板块详情
```bash
curl http://localhost:7800/section/1
```
```ts
sectionApi.getById('1')
```

### POST /section — 创建板块 🔒
```bash
curl -X POST http://localhost:7800/section \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"新板块","description":"描述","icon":"📌"}'
```
```ts
sectionApi.create({ name: '新板块', description: '描述', icon: '📌' })
```

### PUT /section/{id} — 更新板块 🔒
```bash
curl -X PUT http://localhost:7800/section/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"更新名称"}'
```
```ts
sectionApi.update('1', { name: '更新名称' })
```

### DELETE /section/{id} — 删除板块 🔒
```bash
curl -X DELETE http://localhost:7800/section/1 \
  -H "Authorization: Bearer <token>"
```
```ts
sectionApi.delete('1')
```

---

## 9. 标签模块 — `/tag`

### GET /tag/hot — 获取热门标签
```bash
curl "http://localhost:7800/tag/hot?limit=10"
```
```ts
tagApi.getHotTags(10)
```

### GET /tag/search — 搜索标签
```bash
curl "http://localhost:7800/tag/search?keyword=java"
```
```ts
tagApi.search('java')
```

### POST /tag/{tagId}/toggle — 切换关注标签 🔒
```bash
curl -X POST "http://localhost:7800/tag/1/toggle?score=3.0" \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.toggleFollow(1, 3.0)
```

### POST /tag/{tagId}/follow — 关注标签 🔒
```bash
curl -X POST "http://localhost:7800/tag/1/follow?score=3.0" \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.follow(1, 3.0)
```

### DELETE /tag/{tagId}/unfollow — 取消关注标签 🔒
```bash
curl -X DELETE http://localhost:7800/tag/1/unfollow \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.unfollow(1)
```

### GET /tag/{tagId}/status — 检查标签关注状态 🔒
```bash
curl http://localhost:7800/tag/1/status \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.getStatus(1)
```

### GET /tag/my-following — 我关注的标签 🔒
```bash
curl http://localhost:7800/tag/my-following \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.getMyFollowing()
```

### PUT /tag/{tagId}/score — 更新标签兴趣权重 🔒
```bash
curl -X PUT "http://localhost:7800/tag/1/score?score=4.5" \
  -H "Authorization: Bearer <token>"
```
```ts
tagApi.updateScore(1, 4.5)
```

---

## 10. 推荐模块 — `/recommend`

### GET /recommend/list — 获取推荐列表 🔒
```bash
curl "http://localhost:7800/recommend/list?page=1&pageSize=10" \
  -H "Authorization: Bearer <token>"
```
```ts
recommendApi.getHybridList(1, 10)
```

### GET /recommend/post-detail/{postId} — 获取详情页推荐
```bash
curl "http://localhost:7800/recommend/post-detail/abc123?limit=6" \
  -H "Authorization: Bearer <token>"
```
```ts
recommendApi.getSimilar('abc123', 6)
```

---

## 11. 统计模块 — `/stats`

### GET /stats/site — 站点汇总统计
```bash
curl http://localhost:7800/stats/site
```
```ts
statsApi.getSiteStats()
// 返回 { data: { totalPosts, totalUsers, totalComments, todayPosts } }
```

---

## 12. 热度排行 — `/heat-rank`

### GET /heat-rank/top — 热度排行 TOP 10
```bash
curl http://localhost:7800/heat-rank/top
```
```ts
statsApi.getHotRank()
// 返回 { data: HotPost[] }
```

---

## 13. 趋势统计 — `/trend-stat`

### GET /trend-stat/keyword-cloud — 关键词词云
```bash
curl http://localhost:7800/trend-stat/keyword-cloud
```
```ts
trendsApi.getKeywordCloud()
```

### GET /trend-stat/post-trend — 帖子发布趋势
```bash
curl http://localhost:7800/trend-stat/post-trend
```
```ts
trendsApi.getPostTrend()
```

### GET /trend-stat/category-pie — 分类饼图
```bash
curl http://localhost:7800/trend-stat/category-pie
```
```ts
trendsApi.getCategoryPie()
```

### GET /trend-stat/heat-rank — 热度排行（趋势）
```bash
curl http://localhost:7800/trend-stat/heat-rank
```
```ts
trendsApi.getHeatRank()
```

### GET /trend-stat/prediction — 话题预测
```bash
curl http://localhost:7800/trend-stat/prediction
```
```ts
trendsApi.getPrediction()
```

### GET /trend-stat/latest/{type} — 获取最新统计
```bash
curl http://localhost:7800/trend-stat/latest/keyword_cloud
```

### GET /trend-stat/by-date — 按日期查询统计
```bash
curl "http://localhost:7800/trend-stat/by-date?statDate=2026-02-26&type=keyword_cloud"
```

### GET /trend-stat/range — 时间范围统计
```bash
curl "http://localhost:7800/trend-stat/range?startDate=2026-02-01&endDate=2026-02-26&type=post_trend"
```

### POST /trend-stat/save — 保存统计数据 🔒管理员
```bash
curl -X POST "http://localhost:7800/trend-stat/save?statDate=2026-02-26&type=keyword_cloud" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"keywords":[{"name":"Java","value":50}]}'
```

### POST /trend-stat/generate-daily — 触发生成当日统计 🔒管理员
```bash
curl -X POST http://localhost:7800/trend-stat/generate-daily \
  -H "Authorization: Bearer <admin_token>"
```

### DELETE /trend-stat/clean — 清理旧统计数据 🔒管理员
```bash
curl -X DELETE "http://localhost:7800/trend-stat/clean?beforeDate=2025-01-01" \
  -H "Authorization: Bearer <admin_token>"
```

---

## 14. 文件上传 — `/common`

### POST /common/upload/image — 上传图片 🔒
```bash
curl -X POST http://localhost:7800/common/upload/image \
  -H "Authorization: Bearer <token>" \
  -F "file=@/path/to/image.jpg" \
  -F "module=avatar"
```
```ts
uploadApi.uploadImage(file)
// 返回 { data: "/uploads/avatar/2026/02/26/IMG_xxx.jpg" }
```

---

## 15. 用户模块 — `/user`

### GET /user/profile — 获取当前用户资料 🔒
```bash
curl http://localhost:7800/user/profile \
  -H "Authorization: Bearer <token>"
```
```ts
userApi.getProfile()
```

### GET /user/simple-profile — 获取简要资料 🔒
```bash
curl http://localhost:7800/user/simple-profile \
  -H "Authorization: Bearer <token>"
```
```ts
userApi.getSimpleProfile()
```

### PUT /user/avatar — 更新头像 🔒
```bash
curl -X PUT http://localhost:7800/user/avatar \
  -H "Authorization: Bearer <token>" \
  -F "avatar=@/path/to/avatar.jpg"
```
```ts
userApi.updateAvatar(file)
```

### POST /user/update-pwd — 修改密码 🔒
```bash
curl -X POST http://localhost:7800/user/update-pwd \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"oldPassword":"old","newPassword":"new"}'
```
```ts
userApi.updatePwd({ oldPassword: 'old', newPassword: 'new' })
```

### POST /user/update-udetail — 更新用户详情 🔒
```bash
curl -X POST http://localhost:7800/user/update-udetail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"nickname":"新昵称","school":"大学","major":"计算机"}'
```
```ts
userApi.updateUserDetails({ nickname: '新昵称', school: '大学', major: '计算机' })
```

### GET /user/profile-stats — 当前用户统计 🔒
```bash
curl http://localhost:7800/user/profile-stats \
  -H "Authorization: Bearer <token>"
```
```ts
userApi.getProfileStats()
// 返回 { data: { postCount, followingCount, followerCount } }
```

---

## 16. 用户画像 — `/user-profile`

### GET /user-profile — 获取我的画像 🔒
```bash
curl http://localhost:7800/user-profile \
  -H "Authorization: Bearer <token>"
```
```ts
userProfileApi.getMyProfile()
```

### GET /user-profile/{userId} — 获取指定用户画像
```bash
curl http://localhost:7800/user-profile/user123
```
```ts
userProfileApi.getUserProfile('user123')
```

### PUT /user-profile — 更新我的画像 🔒
```bash
curl -X PUT http://localhost:7800/user-profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"activeRegion":"北京"}'
```
```ts
userProfileApi.updateMyProfile({ activeRegion: '北京' })
```

### GET /user-profile/stats — 获取我的统计 🔒
```bash
curl http://localhost:7800/user-profile/stats \
  -H "Authorization: Bearer <token>"
```
```ts
userProfileApi.getMyStats()
// 返回 { data: { posts, likes, reputation, contribution } }
```

---

## 17. 浏览日志 — `/view-log`

### POST /view-log/record — 记录浏览
```bash
curl -X POST "http://localhost:7800/view-log/record?postId=abc123" \
  -H "Authorization: Bearer <token>"
```
```ts
viewLogApi.recordView('abc123')
```

### GET /view-log/user-history/{userId} — 获取浏览历史
```bash
curl "http://localhost:7800/view-log/user-history/user123?limit=20" \
  -H "Authorization: Bearer <token>"
```
```ts
viewLogApi.getUserHistory('user123', 20)
```

### GET /view-log/count — 获取帖子浏览次数
```bash
curl "http://localhost:7800/view-log/count?postId=abc123"
```

### GET /view-log/total/{postId} — 获取帖子总浏览次数
```bash
curl http://localhost:7800/view-log/total/abc123
```

### GET /view-log/hot-posts — 热门帖子排行（按浏览量）
```bash
curl "http://localhost:7800/view-log/hot-posts?limit=10"
```

### GET /view-log/daily-stats — 每日浏览统计
```bash
curl "http://localhost:7800/view-log/daily-stats?startDate=2026-02-01 00:00:00&endDate=2026-02-26 23:59:59"
```

### GET /view-log/device-distribution — 设备类型分布
```bash
curl http://localhost:7800/view-log/device-distribution
```

### DELETE /view-log/clean — 清理旧日志 🔒管理员
```bash
curl -X DELETE "http://localhost:7800/view-log/clean?daysToKeep=90" \
  -H "Authorization: Bearer <admin_token>"
```

---

## 18. 帖子点赞 — `/post-like` (辅助接口)

### POST /post-like/{postId}/toggle — 切换点赞 🔒
```bash
curl -X POST http://localhost:7800/post-like/abc123/toggle \
  -H "Authorization: Bearer <token>"
```

### GET /post-like/{postId}/status — 查询点赞状态 🔒
```bash
curl http://localhost:7800/post-like/abc123/status \
  -H "Authorization: Bearer <token>"
```

### GET /post-like/user — 获取用户点赞的帖子列表 🔒
```bash
curl "http://localhost:7800/post-like/user?page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```

---

## 19. 帖子收藏 — `/post-collect` (辅助接口)

### POST /post-collect/{postId}/toggle — 切换收藏 🔒
```bash
curl -X POST http://localhost:7800/post-collect/abc123/toggle \
  -H "Authorization: Bearer <token>"
```

### GET /post-collect/{postId}/status — 查询收藏状态 🔒
```bash
curl http://localhost:7800/post-collect/abc123/status \
  -H "Authorization: Bearer <token>"
```

### GET /post-collect/user — 获取用户收藏的帖子列表 🔒
```bash
curl "http://localhost:7800/post-collect/user?page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```

---

## 20. 缓存管理 — `/admin/cache` 🔒管理员

### DELETE /admin/cache/tag/clear — 清除标签缓存
```bash
curl -X DELETE http://localhost:7800/admin/cache/tag/clear \
  -H "Authorization: Bearer <admin_token>"
```

### DELETE /admin/cache/token/clear — 清除Token缓存
```bash
curl -X DELETE http://localhost:7800/admin/cache/token/clear \
  -H "Authorization: Bearer <admin_token>"
```

### DELETE /admin/cache/clear — 按模式清除缓存
```bash
curl -X DELETE "http://localhost:7800/admin/cache/clear?pattern=tag:*" \
  -H "Authorization: Bearer <admin_token>"
```

---

## 21. 帖子收藏(Favorite) — `/api/post/favorite` 🔒

> 注意：此模块通过 `/api/post/favorite/*` 路径访问（前端经 Vite 代理后变为 `/post/favorite/*`）

### POST /api/post/favorite/{postId} — 收藏帖子
```bash
curl -X POST http://localhost:7800/api/post/favorite/1 \
  -H "Authorization: Bearer <token>"
```

### DELETE /api/post/favorite/{postId} — 取消收藏
```bash
curl -X DELETE http://localhost:7800/api/post/favorite/1 \
  -H "Authorization: Bearer <token>"
```

### GET /api/post/favorite/check/{postId} — 检查是否已收藏
```bash
curl http://localhost:7800/api/post/favorite/check/1 \
  -H "Authorization: Bearer <token>"
```

### GET /api/post/favorite/list — 获取收藏列表
```bash
curl "http://localhost:7800/api/post/favorite/list?page=1&size=20" \
  -H "Authorization: Bearer <token>"
```

### GET /api/post/favorite/count — 获取收藏数量
```bash
curl http://localhost:7800/api/post/favorite/count \
  -H "Authorization: Bearer <token>"
```
