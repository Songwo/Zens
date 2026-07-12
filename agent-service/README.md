# Community QA Agent Service

独立的 Python Agent 服务，面向 `Campus Pulse / Zens` 的第一期能力：

- 社区问答
- 历史讨论检索
- 引用式回答
- 只读搜索副本访问，不占用主业务写链路

这套服务默认假设：

1. 主站仍由 Spring Boot 提供写入与主业务接口
2. Agent 只使用 **MySQL 只读副本** 或独立的 PostgreSQL 搜索库
3. Agent 不持有主库写账号，也不执行写入、会话落库或数据库迁移
4. 搜索库中建议同步 `sys_post`、`sys_comment`、`sections`、`sys_user` 等基础表

如果暂时没有接入大模型，服务也能返回基于社区检索结果的可读回答；配置 OpenAI 兼容接口后，会自动升级为更自然的总结式答案。

## 目录

```text
agent-service/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── models.py
│   ├── repositories/
│   │   ├── base.py
│   │   ├── mysql_search.py
│   │   └── postgres_search.py
│   └── services/
│       ├── answering.py
│       ├── community_insights.py
│       ├── community_qa.py
│       └── llm_client.py
├── scripts/
│   ├── dev.ps1
│   ├── dev.sh
│   └── smoke.ps1
├── sql/
│   └── postgres/
│       └── recommended_indexes.sql
├── .env.example
├── Dockerfile
└── requirements.txt
```

## 能力说明

### 1. `POST /v1/community-qa/ask`

输入一个问题，返回：

- `answer`: Agent 输出的最终回答
- `citations`: 引用来源
- `related_posts`: 相关帖子
- `trace`: 本次检索耗时、命中的后端与命中数

### 2. `POST /v1/community-qa/search`

只返回检索结果，不走总结模型。适合联调和后台排查。

### 3. `GET /health`

返回服务、当前搜索后端和 MySQL 副本只读状态。该接口始终返回健康快照；供容器编排使用的 `GET /ready` 在数据库不可用时会返回 `503`。

### 4. 只读社区洞察

- `GET /v1/insights/weekly-digest?days=7&limit=8`：按精华、热度、收藏和讨论筛选本周值得回看的帖子。
- `GET /v1/insights/unanswered?days=14&limit=8&max_comments=0`：筛选尚无回复或回复不足的问题。
- `GET /v1/insights/community-health?days=7`：返回发帖、评论、贡献者、回复覆盖和阅读量快照。

这些接口只执行 `SELECT`，MySQL 下每次连接仍先执行 `SET SESSION TRANSACTION READ ONLY`。前端 Agent 页面采用按需加载，用户点击对应服务后才发起查询，不进行后台轮询。

## 快速启动

### 1. 安装依赖

```bash
cd agent-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Windows PowerShell:

```powershell
cd agent-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 2. 配置环境变量

复制一份：

```bash
cp .env.example .env.local
```

最关键的配置：

```env
AGENT_SEARCH_BACKEND=mysql
AGENT_MYSQL_REPLICA_DSN=jdbc:mysql://127.0.0.1:3308/campus_pulse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
AGENT_MYSQL_REPLICA_USERNAME=agent_reader
AGENT_MYSQL_REPLICA_PASSWORD=change-me
AGENT_MYSQL_REQUIRE_READ_ONLY=true
AGENT_LLM_ENABLED=false
```

建议在 MySQL 副本创建独立只读账号，不要把主站的 `root` 或业务写账号交给 Agent：

```sql
CREATE USER 'agent_reader'@'应用容器网段' IDENTIFIED BY '请替换为强密码';
GRANT SELECT, SHOW VIEW ON campus_pulse.* TO 'agent_reader'@'应用容器网段';
```

`AGENT_MYSQL_REQUIRE_READ_ONLY=true` 会在启动阶段确认 `read_only` 与 `super_read_only` 均已开启。如果配置误指向可写主库或保护不完整的副本，服务会直接拒绝启动；此外，每个 MySQL 会话都会执行 `SET SESSION TRANSACTION READ ONLY`。本地临时直连开发库时可以显式设为 `false`，生产环境应保持 `true`。

旧的 `AGENT_MYSQL_DSN`、`AGENT_MYSQL_USERNAME`、`AGENT_MYSQL_PASSWORD` 仍可兼容读取，但新部署应只使用 `AGENT_MYSQL_REPLICA_*`，并避免向 Agent 容器注入主库写凭证。

当前项目里，`agent-service/.env.local` 就是 Agent 的本地配置入口。你要改：

- 搜索走哪套库
- Agent 监听端口
- OpenAI 兼容 API 地址 / Key / 模型

都在这个文件里改。

如果你已经有 PostgreSQL 搜索副本，可以继续补上：

```env
AGENT_POSTGRES_DSN=postgresql://readonly:password@127.0.0.1:5432/campus_pulse_search
```

如果需要开启模型总结：

```env
AGENT_LLM_ENABLED=true
AGENT_LLM_BASE_URL=https://muyuan.do/v1
AGENT_LLM_API_KEY=sk-xxxx
AGENT_LLM_MODEL=gpt-5.4
```

也可以改成 DeepSeek 这类 OpenAI 兼容接口。

### 3. 启动

```bash
uvicorn app.main:app --host 0.0.0.0 --port 7810 --reload
```

或直接用脚本：

```powershell
.\scripts\dev.ps1
```

### 4. 验证

```bash
python -m unittest discover -s tests -v
curl -fsS http://127.0.0.1:7810/ready
```

`/ready` 返回 `200` 才表示搜索库可用；启用严格只读校验后，响应中的 `mysql_replica` 应为 `ok`，`replica_read_only` 应为 `true`。

## 示例请求

```bash
curl -X POST "http://127.0.0.1:7810/v1/community-qa/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Spring Boot 登录态频繁失效怎么排查？",
    "limit": 6,
    "include_comments": true
  }'
```

返回示例：

```json
{
  "answer": "根据社区历史讨论，登录态失效通常集中在令牌过期、刷新逻辑和设备绑定校验三个方向。[1][2]",
  "confidence": "medium",
  "citations": [
    {
      "index": 1,
      "post_id": "POST_xxx",
      "title": "Spring Boot 登录态排查记录",
      "section_name": "后端工程",
      "excerpt": "..."
    }
  ],
  "related_posts": [],
  "trace": {
    "backend": "mysql",
    "retrieval_ms": 42,
    "llm_ms": 0,
    "total_ms": 43,
    "hit_count": 4
  }
}
```

## 后端切换说明

- `AGENT_SEARCH_BACKEND=auto`: 有 PostgreSQL 就优先用 PostgreSQL，否则自动回退 MySQL
- `AGENT_SEARCH_BACKEND=mysql`: 强制走 MySQL
- `AGENT_SEARCH_BACKEND=postgres`: 强制走 PostgreSQL

生产环境使用 MySQL 时，`AGENT_MYSQL_REPLICA_*` 必须指向同步正常的只读副本。主库继续只服务 Spring Boot 的写入、事务、会话和迁移链路。

## PostgreSQL 搜索副本建议

推荐至少具备：

- `pg_trgm`
- `GIN + tsvector`
- `status / audit_status / section_id / create_time` 过滤索引

可直接参考：

- [sql/postgres/recommended_indexes.sql](./sql/postgres/recommended_indexes.sql)

## 与主站集成建议

主站暂时不需要改数据库写链路。推荐的接入方式：

1. 前端搜索页直接调用 Agent
2. 或 Spring Boot 提供一个轻量代理接口 `/api/agent/ask`
3. Agent 的搜索请求只连 MySQL 副本或 PostgreSQL 搜索库

这样即使 Agent 压力上来，也不会直接拖慢主站事务链路。
