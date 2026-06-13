# API 接口文档

## 一、置顶管理接口

### 1. 全局置顶/取消全局置顶

**接口：** `POST /api/post/{id}/global-pin`

**权限：** 管理员

**请求参数：**
```json
{
  "pinOrder": 1,           // 可选，置顶排序（数字越小越靠前）
  "expireAt": "2026-03-01T00:00:00"  // 可选，过期时间（ISO格式）
}
```

**响应：**
```json
{
  "code": 2000,
  "message": "操作成功",
  "data": null
}
```

**说明：**
- 切换全局置顶状态（已置顶则取消，未置顶则设置）
- `pinOrder` 默认为 0
- `expireAt` 为空表示永久置顶
- 全局置顶优先级最高

---

### 2. 板块置顶/取消板块置顶

**接口：** `POST /api/post/{id}/category-pin`

**权限：** 管理员/版主

**请求参数：**
```json
{
  "pinOrder": 2,
  "expireAt": "2026-03-01T00:00:00"
}
```

**响应：**
```json
{
  "code": 2000,
  "message": "操作成功",
  "data": null
}
```

**说明：**
- 切换板块置顶状态
- 仅在该帖子所属板块内置顶
- 优先级低于全局置顶

---

### 3. 获取置顶帖子列表

**接口：** `GET /api/post/pinned`

**权限：** 公开

**请求参数：**
- `sectionId`（可选）：板块ID，不传则返回所有全局置顶

**响应：**
```json
{
  "code": 2000,
  "message": "操作成功",
  "data": [
    {
      "id": "POST_xxx",
      "title": "置顶帖子标题",
      "globalPin": 1,
      "categoryPin": 0,
      "pinOrder": 1,
      "pinExpireAt": null,
      "sectionId": 1,
      "sectionName": "技术交流",
      "authorName": "管理员",
      "createTime": "2026-02-27T10:00:00",
      "viewCount": 100,
      "commentCount": 10
    }
  ]
}
```

---

### 4. 旧置顶接口（兼容）

**接口：** `POST /api/post/{id}/pin`

**权限：** 管理员

**说明：**
- 兼容旧版本，切换 `isPinned` 和 `globalPin` 字段
- 建议使用新接口 `/global-pin` 或 `/category-pin`

---

## 二、帖子列表接口

### 1. 搜索帖子列表

**接口：** `POST /api/post/search-lists`

**权限：** 公开

**请求参数：**
```json
{
  "page": 1,
  "pageSize": 10,
  "keyword": "搜索关键词",
  "sectionId": 1,
  "status": 1,
  "orderBy": "new",        // "new" 或 "hot"
  "timeRange": "WEEK",     // "TODAY", "WEEK", "MONTH"
  "isFeatured": false,
  "tag": "Java",
  "pinnedOnly": false,     // 只看置顶
  "cursor": "2026-02-27T10:00:00",  // 游标分页（可选）
  "cursorId": "POST_xxx"   // 游标分页（可选）
}
```

**响应：**
```json
{
  "code": 2000,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": "POST_xxx",
        "title": "帖子标题",
        "content": "帖子内容",
        "summary": "AI生成的摘要",
        "globalPin": 1,
        "categoryPin": 0,
        "pinOrder": 1,
        "sectionId": 1,
        "sectionName": "技术交流",
        "authorName": "用户昵称",
        "authorAvatar": "头像URL",
        "authorRoles": ["ROLE_ADMIN"],
        "viewCount": 100,
        "likeCount": 20,
        "collectCount": 5,
        "commentCount": 10,
        "heatScore": 85.5,
        "createTime": "2026-02-27T10:00:00",
        "lastReplyAt": "2026-02-27T12:00:00",
        "lastActivityAt": "2026-02-27T12:00:00",
        "isLiked": false,
        "isCollected": false,
        "tags": "#Java #Spring",
        "sentimentScore": 0.75,
        "sentimentLabel": "positive",
        "trendLevel": "hot"
      }
    ],
    "total": 100,
    "pages": 10
  }
}
```

**排序规则：**
1. 全局置顶（`globalPin = 1`）优先
2. 板块置顶（`categoryPin = 1`）次之
3. 按 `pinOrder` 升序
4. 普通帖子按 `lastActivityAt` 倒序（最新活跃在前）

---

## 三、WebSocket 实时推送

### 1. 连接端点

**URL：** `ws://localhost:7800/ws`（开发环境）

**协议：** STOMP over SockJS

**连接示例：**
```javascript
import SockJS from 'sockjs-client'
import { Stomp } from '@stomp/stompjs'

const socket = new SockJS('http://localhost:7800/ws')
const client = Stomp.over(socket)

client.connect({}, () => {
  console.log('Connected')
})
```

---

### 2. 订阅频道

#### 全局频道（所有帖子事件）

**频道：** `/topic/posts`

**订阅示例：**
```javascript
client.subscribe('/topic/posts', (message) => {
  const event = JSON.parse(message.body)
  console.log('Received:', event)
})
```

#### 板块频道（指定板块事件）

**频道：** `/topic/section/{sectionId}`

**订阅示例：**
```javascript
client.subscribe('/topic/section/1', (message) => {
  const event = JSON.parse(message.body)
  console.log('Section 1 event:', event)
})
```

---

### 3. 事件类型

#### POST_CREATED（新帖创建）

```json
{
  "type": "POST_CREATED",
  "postId": "POST_xxx",
  "sectionId": 1,
  "title": "新帖子标题",
  "authorName": "用户昵称",
  "authorAvatar": "头像URL",
  "timestamp": "2026-02-27T10:00:00"
}
```

#### POST_REPLIED（新回复）

```json
{
  "type": "POST_REPLIED",
  "postId": "POST_xxx",
  "sectionId": 1,
  "data": {
    "commentCount": 11,
    "lastReplyAt": "2026-02-27T12:00:00",
    "lastActivityAt": "2026-02-27T12:00:00"
  },
  "timestamp": "2026-02-27T12:00:00"
}
```

#### POST_VIEWED（浏览量更新）

```json
{
  "type": "POST_VIEWED",
  "postId": "POST_xxx",
  "sectionId": 1,
  "data": {
    "viewCount": 101
  },
  "timestamp": "2026-02-27T12:05:00"
}
```

#### POST_LIKED（点赞数更新）

```json
{
  "type": "POST_LIKED",
  "postId": "POST_xxx",
  "sectionId": 1,
  "data": {
    "likeCount": 21
  },
  "timestamp": "2026-02-27T12:10:00"
}
```

#### PIN_UPDATED（置顶状态更新）

```json
{
  "type": "PIN_UPDATED",
  "postId": "POST_xxx",
  "sectionId": 1,
  "data": {
    "globalPin": 1,
    "categoryPin": 0,
    "pinOrder": 1
  },
  "timestamp": "2026-02-27T12:15:00"
}
```

---

### 4. 前端集成示例

```typescript
import { wsClient } from '@/utils/websocket'

// 订阅全局事件
const unsubscribe = wsClient.subscribeGlobal((event) => {
  switch (event.type) {
    case 'POST_CREATED':
      // 显示新内容提示
      showNewContentAlert()
      break

    case 'POST_REPLIED':
      // 更新回复数
      updatePostStats(event.postId, event.data)
      break

    case 'PIN_UPDATED':
      // 刷新列表
      refreshList()
      break
  }
})

// 组件卸载时取消订阅
onUnmounted(() => {
  unsubscribe()
})
```

---

## 四、板块管理接口

### 1. 获取所有板块

**接口：** `GET /api/section/list`

**权限：** 公开

**响应：**
```json
{
  "code": 2000,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "技术交流",
      "description": "技术讨论与分享",
      "icon": "💻",
      "sortOrder": 1,
      "status": 1,
      "createdAt": "2026-01-01T00:00:00",
      "postCount": 100,
      "todayCount": 5
    }
  ]
}
```

---

### 2. 创建板块

**接口：** `POST /api/section`

**权限：** 管理员

**请求参数：**
```json
{
  "name": "新板块",
  "description": "板块描述",
  "icon": "📚",
  "sortOrder": 10
}
```

---

### 3. 更新板块

**接口：** `PUT /api/section/{id}`

**权限：** 管理员

**请求参数：**
```json
{
  "name": "更新后的名称",
  "description": "更新后的描述",
  "icon": "🎓",
  "sortOrder": 5
}
```

---

### 4. 删除板块

**接口：** `DELETE /api/section/{id}`

**权限：** 管理员

---

### 5. 切换板块状态

**接口：** `PUT /api/section/{id}/status?status={0|1}`

**权限：** 管理员

**说明：**
- `status=1`：启用
- `status=0`：禁用

---

## 五、错误码说明

| 错误码 | 说明 |
|--------|------|
| 2000 | 操作成功 |
| 2001 | 操作失败 |
| 3001 | 用户名或密码错误 |
| 3002 | 登录信息已过期 |
| 3003 | 无访问权限 |
| 3004 | 未提供Token |
| 3005 | Token无效 |
| 4001 | 参数错误 |
| 5001 | 用户不存在 |
| 5002 | 帖子不存在 |
| 9000 | 系统异常 |

---

## 六、认证说明

### 1. 获取 Token

**接口：** `POST /api/auth/login`

**请求参数：**
```json
{
  "loginType": "password",
  "account": "username",
  "password": "password",
  "rememberMe": true
}
```

**响应：**
```json
{
  "code": 2000,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### 2. 使用 Token

在请求头中添加：
```
Authorization: Bearer {accessToken}
```

### 3. 刷新 Token

**接口：** `POST /api/auth/refresh`

**请求头：**
```
Authorization: Bearer {refreshToken}
```

---

**完整 API 文档请访问 Swagger UI：** http://localhost:7800/swagger-ui.html
