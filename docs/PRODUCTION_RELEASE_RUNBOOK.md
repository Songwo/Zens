# Zens 生产发布与回滚 Runbook

本文针对当前宝塔生产布局，不适用于仓库中旧的 `/opt/campus-pulse` 示例脚本：

- 后端 JAR：`/www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar`
- 前端目录：`/www/wwwroot/Zens/dist`
- 自动运营执行器：`/opt/zens-ops-service`
- 自动运营环境文件：`/etc/zens/zens-ops.env`
- Nginx 由宝塔管理；不要执行 `systemctl restart nginx`。

发布默认保持 `OPS_AUTO_PUBLISH=false`、`OPS_CIRCUIT_OPEN=true`、执行器 `OPS_DRAFT_ONLY=true`。首次灰度只允许生成一条待审批草稿，不发布帖子或评论。

## 1. 发布前门禁

本地必须全部通过：

```bash
mvn test
cd web && npm run build:check && cd ..
cd zens-ops-service && pytest -q && python -m compileall zens_ops && cd ..
git diff --check
```

确认精确暂存清单中不包含运行态和审计产物：

```bash
git diff --cached --name-only
```

不得提交 `.codex-artifacts/`、`campus-lottery-station/server/data/state.json`、根目录 `tmp-*` 或截图，也不得把任何真实密码、Token、R2/Cloudflare 凭据写入仓库。

生产发布前检查磁盘、当前进程和目录；磁盘可用空间不足时先停止，不要继续制造备份：

```bash
df -h /www/wwwroot/Zens /opt /var/backups
pgrep -af 'campus-pulse-0.0.1-SNAPSHOT.jar'
du -sh /www/wwwroot/Zens/dist /www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar
```

记录当前 Java 进程的启动方式、工作目录和环境文件。后续只能通过宝塔当前已有的 Java 项目管理器或已经确认的 supervisor 重启，禁止用宽泛 `pkill` + `nohup` 猜测启动参数。

## 2. 构建与上传候选产物

本地构建测试通过后生成候选产物：

```bash
mvn clean package -DskipTests
cd web && npm run build && cd ..
```

将以下内容上传到服务器的独立临时目录，例如 `/tmp/zens-release-YYYYmmdd-HHMMSS/`：

- `target/campus-pulse-0.0.1-SNAPSHOT.jar`
- `web/dist/` 的完整内容
- `zens-ops-service/`
- `src/main/resources/sql/migrations/2026-07-11-ops-automation-mvp.sql`

上传后核对大小和 SHA-256；不要覆盖在线文件。

## 3. 有限备份

服务器磁盘空间偏紧，每次发布只保留本次回滚所需的一套压缩备份。先创建带时间戳目录并限制权限：

```bash
RELEASE_TS="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="/var/backups/zens-release/${RELEASE_TS}"
install -d -m 0700 "$BACKUP_DIR"
cp -a /www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar "$BACKUP_DIR/backend.jar"
tar -C /www/wwwroot/Zens -czf "$BACKUP_DIR/dist.tgz" dist
cp -a /etc/zens/zens-ops.env "$BACKUP_DIR/zens-ops.env" 2>/dev/null || true
cp -a /etc/systemd/system/zens-ops.service "$BACKUP_DIR/" 2>/dev/null || true
cp -a /etc/systemd/system/zens-ops-health.service "$BACKUP_DIR/" 2>/dev/null || true
cp -a /etc/systemd/system/zens-ops-health.timer "$BACKUP_DIR/" 2>/dev/null || true
```

数据库使用服务器既有、权限受控的 MySQL client 配置执行一次压缩逻辑备份，不要把密码放在命令行或 shell 历史中：

```bash
mysqldump --defaults-extra-file=/root/.my.cnf \
  --single-transaction --set-gtid-purged=OFF campus_pulse \
  | gzip > "$BACKUP_DIR/campus_pulse.sql.gz"
test -s "$BACKUP_DIR/campus_pulse.sql.gz"
```

发布成功并观察稳定后，仅保留最近 1–2 套已验证备份；删除旧备份前再次确认绝对路径位于 `/var/backups/zens-release/`。

## 4. 数据库迁移

迁移只新增五张 `ops_*` 表，可重复执行，不修改既有业务表：

```bash
mysql --defaults-extra-file=/root/.my.cnf campus_pulse \
  < /tmp/zens-release-YYYYmmdd-HHMMSS/2026-07-11-ops-automation-mvp.sql
mysql --defaults-extra-file=/root/.my.cnf campus_pulse -e \
  "SHOW TABLES LIKE 'ops_%';"
```

预期存在：`ops_content_plan`、`ops_draft`、`ops_approval`、`ops_job_run`、`ops_metric_snapshot`。

回滚旧代码时保留这些新增表，不执行 `DROP TABLE`。它们不会影响旧 JAR，保留表才能保存草稿、审批和失败审计；只有在确认数据无需保留、完成独立备份并经过人工批准后才考虑清理。

## 5. 主站环境变量

在宝塔当前 Java 项目的生产环境文件中生成并注入一份随机、至少 32 字符的 HMAC 密钥，主站与执行器必须使用同一个值。不得从 `.env.example` 直接复制占位符。

主站安全初始值：

```text
ZENS_OPS_ENABLED=true
OPS_SERVICE_ID=zens-ops
OPS_AUTHOR_USERNAME=zens_ops
OPS_DAILY_PUBLISH_LIMIT=1
OPS_DAILY_REPLY_LIMIT=10
OPS_FIRST_APPROVAL_COUNT=30
OPS_AUTO_PUBLISH=false
OPS_CIRCUIT_OPEN=true
```

`ZENS_OPS_SERVICE_SECRET` 通过安全环境文件注入。备份旧环境文件并保持 `0600`；不要在终端输出完整值。

## 6. 部署后端和前端

先把候选文件放到同一文件系统的 `.next` 路径，核对后原子替换：

```bash
install -m 0644 /tmp/zens-release-YYYYmmdd-HHMMSS/campus-pulse-0.0.1-SNAPSHOT.jar \
  /www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar.next
rm -rf /www/wwwroot/Zens/dist.next
cp -a /tmp/zens-release-YYYYmmdd-HHMMSS/dist /www/wwwroot/Zens/dist.next
mv -f /www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar.next \
  /www/wwwroot/Zens/campus-pulse-0.0.1-SNAPSHOT.jar
mv /www/wwwroot/Zens/dist /www/wwwroot/Zens/dist.previous
mv /www/wwwroot/Zens/dist.next /www/wwwroot/Zens/dist
```

通过宝塔中当前已有的 Java 项目执行一次受控重启。不要修改宝塔 Nginx 服务类型，不要启动第二个 Java 进程。确认主站健康后再删除 `dist.previous`；本次发布的正式回滚副本仍在 `$BACKUP_DIR/dist.tgz`。

主站 smoke：

```bash
curl -fsS http://127.0.0.1:7800/actuator/health
curl -fsSI https://www.allinsong.top/
curl -fsSI https://www.allinsong.top/api/actuator/health || true
```

同时验证登录、首页、帖子详情、图片和 `/admin/ops-automation`；非管理员必须无法访问运营管理 API。

## 7. 部署自动运营执行器

首次安装：

```bash
id zens-ops >/dev/null 2>&1 || useradd --system --home /nonexistent --shell /usr/sbin/nologin zens-ops
install -d -o root -g root -m 0755 /opt/zens-ops-service
install -d -o root -g zens-ops -m 0750 /etc/zens
install -d -o zens-ops -g zens-ops -m 0750 /var/log/zens-ops
rsync -a --delete /tmp/zens-release-YYYYmmdd-HHMMSS/zens-ops-service/ /opt/zens-ops-service/
python3 -m venv /opt/zens-ops-service/.venv
/opt/zens-ops-service/.venv/bin/pip install --upgrade pip
/opt/zens-ops-service/.venv/bin/pip install /opt/zens-ops-service
```

写入 `/etc/zens/zens-ops.env`，所有权 `root:root`、权限 `0600`。systemd 管理器会在降权前读取该文件；不要让普通服务进程直接读取密钥文件。灰度值必须包括：

```text
OPS_ENVIRONMENT=production
OPS_DRY_RUN=false
OPS_DRAFT_ONLY=true
OPS_SERVICE_ID=zens-ops
OPS_MAIN_SITE_BASE_URL=http://127.0.0.1:7800
OPS_AGENT_BASE_URL=http://127.0.0.1:7810
OPS_MAX_POSTS_PER_DAY=1
OPS_MAX_REPLIES_PER_DAY=10
OPS_FIRST_APPROVAL_POSTS=30
OPS_DEFAULT_SECTION_ID=1
OPS_KILL_SWITCH=false
OPS_HEALTH_HOST=127.0.0.1
OPS_HEALTH_PORT=7820
```

`OPS_SERVICE_SECRET` 必须与主站一致；LLM 配置从现有 Agent 安全配置复制到环境文件，不在屏幕、日志或 Git 中显示。

安装单元并启动：

```bash
install -m 0644 /opt/zens-ops-service/deploy/zens-ops.service /etc/systemd/system/zens-ops.service
install -m 0644 /opt/zens-ops-service/deploy/zens-ops-health.service /etc/systemd/system/zens-ops-health.service
install -m 0644 /opt/zens-ops-service/deploy/zens-ops-health.timer /etc/systemd/system/zens-ops-health.timer
systemctl daemon-reload
systemctl enable --now zens-ops.service zens-ops-health.timer
systemctl --no-pager --full status zens-ops.service zens-ops-health.timer
curl -fsS http://127.0.0.1:7820/health
curl -fsS http://127.0.0.1:7820/ready
```

## 8. 灰度验证

保持主站熔断开启，先验证坏签名返回 401 且不会占用 nonce，再执行一次：

```bash
sudo -u zens-ops sh -c \
  'cd /opt/zens-ops-service && set -a && . /etc/zens/zens-ops.env && exec .venv/bin/zens-ops plan-daily --once'
```

数据库和后台必须同时满足：

- 新增 1 条计划。
- 新增 1 条 `PENDING_APPROVAL` 草稿。
- `Zens运营` 正式发帖数仍为 0。
- 没有已发布帖子或评论。
- 后台审批页可以预览该草稿。
- 主从复制仍为 IO/SQL `Yes`、延迟为 0；Agent 仍只读访问从库。

若任一条件不满足，立即执行：

```bash
systemctl stop zens-ops.service zens-ops-health.timer
```

并保持 `OPS_CIRCUIT_OPEN=true`，不要继续审批或发布。

## 9. 回滚

触发条件包括：主站健康失败、登录/发帖核心链路异常、错误率明显上升、重复草稿/发布、鉴权异常或数据库复制异常。

1. 停止执行器和 timer，保持熔断开启。
2. 用 `$BACKUP_DIR/backend.jar` 恢复真实 JAR 路径。
3. 解压 `$BACKUP_DIR/dist.tgz` 到临时目录，核对后替换 `/www/wwwroot/Zens/dist`。
4. 恢复本次备份的主站/runner 环境文件和 systemd 单元；执行 `systemctl daemon-reload`。
5. 通过宝塔当前 Java 项目管理器重启主站，不操作 systemd Nginx。
6. 验证健康、登录、首页、帖子详情、图片、主从复制和 Agent 只读链路。
7. 保留五张 `ops_*` 表和其中数据，不做破坏性数据库回滚。

若迁移本身失败且尚未创建任何业务数据，优先从错误日志判断并修复；只有数据库整体损坏时才使用已验证的压缩备份恢复，恢复操作必须单独审批。

## 10. 发布后观察

至少观察 30 分钟：Java/runner 日志无密钥、无连续 5xx、无重复任务；CPU/内存/磁盘稳定；主从延迟正常。24 小时内继续关注草稿质量、审批结果、错误率和用户投诉。首批 30 篇仍逐篇人工确认，在完整验证前不要关闭熔断或开启自动发布。
