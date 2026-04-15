# 运维脚本

## 1. 发布脚本

```bash
chmod +x scripts/*.sh
APP_NAME=campus-pulse \
DEPLOY_DIR=/opt/campus-pulse \
BACKUP_DIR=/opt/campus-pulse-backups \
HEALTH_URL=http://127.0.0.1:7800/actuator/health \
SERVICE_NAME=campus-pulse \
./scripts/deploy.sh /tmp/campus-pulse.jar
```

- 默认会先备份旧 jar，再发布新 jar。
- 健康检查失败会自动回滚到本次发布前备份。

## 2. 回滚脚本

```bash
./scripts/rollback.sh
```

- 不传参数时，默认回滚到最新备份。
- 也可以指定备份包路径：

```bash
./scripts/rollback.sh /opt/campus-pulse-backups/campus-pulse_20260304_220000.jar
```

## 3. 备份脚本

```bash
MYSQL_DATABASE=campus_pulse \
MYSQL_USER=root \
MYSQL_PASSWORD=your_password \
UPLOAD_DIR=/opt/campus-pulse/data/uploads \
./scripts/backup.sh
```

- 备份内容：MySQL、Redis dump、上传目录。
- 默认保留 14 天备份目录，可通过 `RETENTION_DAYS` 调整。

## 4. 压测脚本

单接口压测：

```bash
node scripts/loadtest.js http://127.0.0.1:7800/section/active 200 4000
```

混合场景压测：

```bash
LOADTEST_POST_IDS=postA,postB,postC \
LOADTEST_AUTH_TOKEN=your_jwt_token \
node scripts/mixed-loadtest.js http://127.0.0.1:7800 300 6000
```

- `mixed-loadtest.js` 默认混合了首页 `GET /public/home-bootstrap`、帖子列表 `POST /post/search-lists`、浏览日志 `POST /view-log/record`、点赞 `POST /post/{id}/like`。
- 如果不提供 `LOADTEST_AUTH_TOKEN`，脚本会自动跳过点赞流量；如果不提供 `LOADTEST_POST_IDS`，会自动跳过需要帖子 ID 的场景。
- WebSocket 在线场景暂未自动注入，这一部分建议在线上压测机配合专门的 WS 客户端单独补测。

## 5. 生产参数建议

生产压测不要直接照搬开发机线程数，优先按机器 CPU、内存、数据库连接上限和 Redis 实例规格调环境变量：

```bash
SERVER_TOMCAT_THREADS_MAX=200
SERVER_TOMCAT_THREADS_MIN_SPARE=20
SERVER_TOMCAT_MAX_CONNECTIONS=8192
DB_MAX_POOL_SIZE=32
DB_MIN_IDLE=8
REDIS_POOL_MAX_ACTIVE=20
REDIS_POOL_MAX_IDLE=10
REDIS_POOL_MIN_IDLE=2
```

- Web 层线程和 DB 连接池必须联动调，不要只拉高 `SERVER_TOMCAT_THREADS_MAX`。
- 建议先在与生产接近的压测机上，以 60%、80%、100% 目标并发分阶段压，观察 P95、P99、GC、MySQL CPU、慢 SQL、Redis 命中率。
