# 快速部署指南

## 一、数据库迁移（必须先执行）

```bash
# 连接到 MySQL
mysql -u root -p

# 选择数据库
USE campus_pulse;

# 执行迁移脚本
SOURCE D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse/src/main/resources/sql/add_pin_and_activity_fields.sql;

# 验证字段是否添加成功
DESC sys_post;

# 应该看到以下新字段：
# - global_pin
# - category_pin
# - pin_order
# - pin_expire_at
# - last_reply_at
# - last_activity_at
```

## 二、后端启动

```bash
# 进入项目目录
cd D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse

# 方式1：使用 Maven 启动（开发环境）
mvn spring-boot:run

# 方式2：打包后启动（生产环境）
mvn clean package -DskipTests
java -jar target/campus-pulse-0.0.1-SNAPSHOT.jar

# 启动成功后，访问：
# - API: http://localhost:7800
# - Swagger: http://localhost:7800/swagger-ui.html
# - WebSocket: ws://localhost:7800/ws
```

## 三、前端启动

```bash
# 进入前端目录
cd D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse/web

# 安装新依赖（首次或依赖更新后）
npm install

# 启动开发服务器
npm run dev

# 访问：http://localhost:5173
```

## 四、功能测试清单

### 1. 板块管理页面测试

访问：http://localhost:5173/admin/sections

**验证点：**
- [ ] 板块列表正常加载
- [ ] 显示 createdAt（创建时间）
- [ ] 显示 postCount（帖子数）
- [ ] 显示 todayCount（今日新增）
- [ ] 时间格式正确（yyyy-MM-dd HH:mm:ss）

### 2. 置顶功能测试

**步骤：**
1. 创建一个测试帖子
2. 以管理员身份登录
3. 点击帖子的"置顶"按钮
4. 刷新首页，验证帖子显示在列表顶部

**API 测试：**
```bash
# 获取 token（先登录）
TOKEN="your_access_token"

# 全局置顶
curl -X POST http://localhost:7800/api/post/{postId}/global-pin \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pinOrder": 1}'

# 板块置顶
curl -X POST http://localhost:7800/api/post/{postId}/category-pin \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pinOrder": 2, "expireAt": "2026-03-01T00:00:00"}'

# 获取置顶列表
curl http://localhost:7800/api/post/pinned
```

### 3. 列表排序测试

**验证点：**
- [ ] 全局置顶帖子显示在最上方
- [ ] 板块置顶帖子显示在全局置顶下方
- [ ] 普通帖子按最后活跃时间倒序排列
- [ ] 回复旧帖子后，该帖子浮到普通列表顶部

**测试步骤：**
1. 创建3个普通帖子（A、B、C）
2. 将帖子A设为全局置顶
3. 将帖子B设为板块置顶
4. 回复帖子C
5. 验证顺序：A（全局置顶）→ B（板块置顶）→ C（最新回复）

### 4. WebSocket 实时更新测试

**步骤：**
1. 打开两个浏览器窗口（或使用隐私模式）
2. 窗口A：登录并停留在首页
3. 窗口B：登录并创建新帖子
4. 窗口A：应该看到"有新内容，点击刷新"提示

**浏览器控制台测试：**
```javascript
// 打开浏览器控制台（F12）
// 应该看到以下日志：
// [WebSocket] 连接成功
// [WebSocket] 已订阅: /topic/posts
// [TopicList] WebSocket 订阅成功

// 创建帖子或回复后，应该看到：
// Received: {type: "POST_CREATED", postId: "...", ...}
```

### 5. 降级机制测试

**步骤：**
1. 停止后端服务
2. 刷新前端页面
3. 应该看到控制台日志：
   ```
   [WebSocket] 连接失败
   [TopicList] WebSocket 订阅失败，降级到轮询模式
   ```
4. 重启后端服务
5. WebSocket 应该自动重连（最多5次尝试）

## 五、常见问题排查

### 问题1：数据库迁移失败

**错误：** `ERROR 1060: Duplicate column name 'global_pin'`

**原因：** 字段已存在

**解决：**
```sql
-- 检查字段是否已存在
DESC sys_post;

-- 如果已存在，跳过迁移或手动删除后重新执行
ALTER TABLE sys_post DROP COLUMN global_pin;
```

### 问题2：WebSocket 连接失败

**错误：** `WebSocket connection failed`

**排查：**
1. 检查后端是否启动：`curl http://localhost:7800/actuator/health`
2. 检查 WebSocket 端点：`curl http://localhost:7800/ws/info`
3. 检查防火墙/代理配置

**解决：**
```java
// WebSocketConfig.java - 确保配置正确
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*")  // 开发环境允许所有来源
    .withSockJS();
```

### 问题3：前端依赖安装失败

**错误：** `npm ERR! code ERESOLVE`

**解决：**
```bash
# 清理缓存
npm cache clean --force

# 删除 node_modules 和 package-lock.json
rm -rf node_modules package-lock.json

# 重新安装
npm install

# 如果仍然失败，使用 --legacy-peer-deps
npm install --legacy-peer-deps
```

### 问题4：板块管理页面不显示数据

**排查：**
1. 打开浏览器控制台（F12）→ Network 标签
2. 查看 `/api/section/list` 请求
3. 检查响应数据格式

**常见原因：**
- 后端返回 `code: 2000`，但前端期望 `code: 200`
- 响应结构不匹配：`res.data` vs `res.data.data`

**解决：** 已在 `lib/api.ts` 中统一处理，确保 `ResultCode.SUCCESS = 2000`

### 问题5：置顶帖子不显示

**排查：**
```sql
-- 检查数据
SELECT id, title, global_pin, category_pin, pin_order, pin_expire_at
FROM sys_post
WHERE global_pin = 1 OR category_pin = 1;

-- 检查是否过期
SELECT id, title, pin_expire_at
FROM sys_post
WHERE (global_pin = 1 OR category_pin = 1)
  AND pin_expire_at IS NOT NULL
  AND pin_expire_at < NOW();
```

**解决：**
```sql
-- 手动设置置顶（测试用）
UPDATE sys_post
SET global_pin = 1, pin_order = 1, pin_expire_at = NULL
WHERE id = 'POST_xxx';
```

## 六、性能优化建议

### 1. 数据库索引验证

```sql
-- 检查索引是否创建
SHOW INDEX FROM sys_post;

-- 应该看到以下索引：
-- idx_global_pin
-- idx_category_pin
-- idx_last_activity_at
-- idx_section_activity

-- 分析查询性能
EXPLAIN SELECT * FROM sys_post
WHERE status = 1
ORDER BY global_pin DESC, category_pin DESC, pin_order ASC, last_activity_at DESC
LIMIT 10;
```

### 2. WebSocket 连接数监控

```java
// 添加监控日志（可选）
@Component
public class WebSocketEventListener {
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        log.info("WebSocket 连接建立");
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("WebSocket 连接断开");
    }
}
```

### 3. 缓存优化（可选）

```java
// 缓存置顶帖子列表（Redis）
@Cacheable(value = "pinnedPosts", key = "#sectionId")
public List<PostResp> getPinnedPosts(Long sectionId) {
    // ...
}

// 置顶状态变更时清除缓存
@CacheEvict(value = "pinnedPosts", allEntries = true)
public void setGlobalPin(...) {
    // ...
}
```

## 七、生产环境部署

### 1. 后端打包

```bash
# 打包（跳过测试）
mvn clean package -DskipTests

# 生成的 JAR 文件位置
# target/campus-pulse-0.0.1-SNAPSHOT.jar
```

### 2. 前端构建

```bash
cd web

# 构建生产版本
npm run build

# 生成的文件位置
# dist/
```

### 3. Nginx 配置

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # 前端静态文件
    location / {
        root /var/www/campus-pulse/dist;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://localhost:7800/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket
    location /api/ws {
        proxy_pass http://localhost:7800/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }
}
```

### 4. 系统服务配置（systemd）

```ini
# /etc/systemd/system/campus-pulse.service
[Unit]
Description=Campus Pulse Backend
After=network.target mysql.service redis.service

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/campus-pulse
ExecStart=/usr/bin/java -jar /opt/campus-pulse/campus-pulse-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启动服务
sudo systemctl start campus-pulse

# 开机自启
sudo systemctl enable campus-pulse

# 查看状态
sudo systemctl status campus-pulse

# 查看日志
sudo journalctl -u campus-pulse -f
```

## 八、监控与日志

### 1. 应用日志

```bash
# 后端日志位置
tail -f log/logfile.log

# 关键日志关键词
grep "WebSocket" log/logfile.log
grep "推送事件" log/logfile.log
grep "置顶" log/logfile.log
```

### 2. 数据库慢查询

```sql
-- 开启慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;

-- 查看慢查询
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;
```

### 3. WebSocket 连接监控

```bash
# 查看 WebSocket 连接数（Linux）
netstat -an | grep :7800 | grep ESTABLISHED | wc -l

# 查看 WebSocket 连接详情
ss -tn | grep :7800
```

---

**部署完成！** 如有问题，请参考上述排查步骤或查看详细文档 `IMPLEMENTATION_SUMMARY.md`。
