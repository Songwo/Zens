# Zens 自动运营执行器

独立、HTTP-only 的自动运营 MVP。它调用只读 Agent 做选题研究，再通过主站受保护的 `/api/internal/ops/**` 接口创建运营计划、原创草稿、待审批评论、每周精选和指标记录。代码中没有数据库驱动、DSN 或主库连接能力。

## 安全边界

- 默认 `OPS_DRY_RUN=true`、`OPS_DRAFT_ONLY=true`。
- 写模式必须配置至少 32 字符的 HMAC 共享密钥，服务身份固定建议为 `zens-ops`。
- 所有写请求使用主站既有 HMAC 规范，并携带稳定的幂等键。
- 首批 30 篇及敏感主题只进入人工审批；当前 MVP **没有任何自动 publish 调用**。
- 每天最多生成 1 篇帖子草稿、10 条回复草稿。
- `OPS_KILL_SWITCH=true` 会立即阻止所有定时工作；主站 `/circuit` 是第二道服务端熔断。
- 政治、医疗、法律、金融、未成年人、投诉、商业承诺、广告、抽奖和付费主题只生成审批项。
- 外部模型未配置时使用可测试的确定性原创模板，不会抓取或复制外站文章。

## 定时任务

| 时间（Asia/Shanghai） | 任务 |
|---|---|
| 每天 06:30 | 只读检索、选题计划、原创帖子草稿并提交审批 |
| 每 2 小时的 :15 | 扫描互动候选，生成回复草稿 |
| 周日 18:00 | 生成“Zens 本周精选”草稿 |
| 周一 08:00 | 写入上周指标报告记录 |

## 本地运行

```bash
cd zens-ops-service
python -m venv .venv
. .venv/bin/activate
pip install .
pip install pytest pytest-asyncio respx
cp .env.example .env.local
zens-ops health --once
zens-ops plan-daily --once
zens-ops serve
```

Windows 激活命令为 `.\.venv\Scripts\Activate.ps1`。健康端点：`GET /health` 只表示进程存活；`GET /ready` 同时验证 Agent 与主站内部接口。

灰度环境可设置 `OPS_DRY_RUN=false`，但必须保持 `OPS_DRAFT_ONLY=true`。一次性发布前烟雾命令：

```bash
zens-ops plan-daily --once
```

预期结果是主站新增运营计划和 `PENDING_APPROVAL` 草稿，绝不能出现已发布帖子。

## 主站契约

执行器使用：

- `GET /api/internal/ops/status`
- `POST /api/internal/ops/plans`
- `POST /api/internal/ops/drafts`
- `POST /api/internal/ops/drafts/{id}/submit`
- `POST /api/internal/ops/metrics`

审批、拒绝、发布与服务端熔断端点由人工管理面调用，执行器不调用 publish。HMAC 原文为：

```text
METHOD\nPATH\nTIMESTAMP_MS\nNONCE\nsha256Hex(rawBody)
```

## 部署

`deploy/` 提供 systemd 服务和健康检查 timer 示例。生产配置放在 `/etc/zens/zens-ops.env`，权限建议 `0600`；不要写入仓库。服务使用专用低权限系统用户，监听地址默认仅 `127.0.0.1`。SIGTERM 会停止 scheduler、等待请求边界并关闭 HTTP 连接池。

## 测试

```bash
pytest -q
```

契约测试会验证原始 JSON 字节、签名路径、HMAC header 和幂等 header，防止客户端与 Spring `InternalServiceFilter` 漂移。
