# MySQL 从库搭建小白手册

这份文档专门写给现在这个项目的当前阶段：

- 你现在只有一个 MySQL 主库
- 你想先搭一个只读从库
- 后面让 Agent 和其它读请求优先走从库

我给你分成两条路：

1. `本地演示版`：最快学会主从怎么跑
2. `服务器正式版`：真给项目用的做法

另外先说结论：**你当前最应该搭的是 MySQL 主从，不是先上 MySQL -> PostgreSQL 同步。**

原因很简单：

- 你主站本来就在用 MySQL
- Agent 现在已经支持直接连 MySQL
- 先把只读副本跑起来，风险最小
- 以后再决定要不要把搜索层单独同步到 PostgreSQL

---

## 0. 先理解三个概念

### 什么是主库

主库就是现在你正在用的那台 MySQL。

它负责：

- 新增数据
- 修改数据
- 删除数据

比如发帖、评论、点赞、注册，这些都写主库。

### 什么是从库

从库就是主库的复制副本。

它通常负责：

- 查询
- 报表
- 搜索
- Agent 检索

一句话理解：

**主库负责写，从库负责读。**

### 主从同步到底在同步什么

MySQL 主从复制本质上是：

1. 主库把每次写操作记到 `binlog`
2. 从库持续读取主库的 `binlog`
3. 从库把这些操作再执行一遍

所以从库会慢一点点，但一般是秒级延迟。

---

## 1. 官方文档你以后重点看这几页

我这里只放 MySQL 官方资料，后面你要深挖就看它们：

- MySQL 官方复制入门: [Configuring Replication](https://dev.mysql.com/doc/refman/8.4/en/replication-howto.html)
- 主库基础配置: [Setting the Source Configuration](https://dev.mysql.com/doc/refman/8.4/en/replication-howto-masterbaseconfig.html)
- 从库基础配置: [Setting the Replica Configuration](https://dev.mysql.com/doc/refman/8.4/en/replication-howto-slavebaseconfig.html)
- 初始化从库: [Creating a Replica Using Binary Log File Position Information](https://dev.mysql.com/doc/refman/8.4/en/replication-howto-slaveinit.html)

我下面的步骤就是按这套思路，帮你翻译成小白版。

---

## 2. 你这次最推荐的落地顺序

### 第一阶段

先在本地跑一个主从演示。

目的不是上线，而是让你真正看懂：

- 主库怎么开 binlog
- 从库怎么连主库
- `SHOW REPLICA STATUS\G` 应该看什么

### 第二阶段

再把线上或云服务器上的第二台 MySQL 做成正式从库。

### 第三阶段

把这几个读流量改到从库：

- Agent 检索
- 搜索页
- 后台统计
- 报表类查询

---

## 3. 本地演示版：直接用我给你准备好的 Docker 文件

我已经给你加好了这套文件：

```text
deploy/mysql-replication/
├── .env.example
├── README.md
├── docker-compose.yml
├── bootstrap-replica.ps1
├── bootstrap-replica.sh
├── scripts/validate-env.sh
├── source/
│   ├── conf.d/my.cnf
│   └── scripts/
└── replica/
    ├── conf.d/my.cnf
    └── scripts/
```

这套 Compose **只用于本地演示**。引导脚本会删除并重新灌入演示从库的
`campus_pulse`，还会重置演示从库的 GTID；不要把它指向线上数据库，也不要把
这两个 Docker volume 当生产存储。

### 3.1 你需要准备什么

先装好支持 `--wait` 的 Docker Compose v2，然后从示例创建本地密钥文件：

验证命令：

```powershell
docker --version
docker compose version
Set-Location deploy/mysql-replication
Copy-Item .env.example .env
notepad .env
```

把 `.env` 中每一个占位值换掉。两套 root 密码、复制账号和复制密码这四个字段
都是必填项，密码至少 16 位，复制账号必须是独立的非 root 账号。`.env` 已被
Git 忽略，禁止提交。

### 3.2 一键启动

在项目根目录打开 PowerShell，执行：

```powershell
cd "D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse\deploy\mysql-replication"
.\bootstrap-replica.ps1
```

如果你在 Linux 或 macOS：

```bash
cd /path/to/campus-pulse/deploy/mysql-replication
cp .env.example .env
${EDITOR:-vi} .env
bash ./bootstrap-replica.sh
```

### 3.3 启动成功后你会得到什么

- 主库地址：`127.0.0.1:3307`
- 从库地址：`127.0.0.1:3308`
- root 密码、复制账号和复制密码：只存在你本机未提交的 `.env` 中

脚本不会把密码值放进 `docker`、`mysql` 或 `mysqladmin` 的命令行参数；连接
数据库时使用容器环境，临时快照无论成功或失败都会清理。

### 3.4 怎么验证它真的同步了

先连主库：

```powershell
docker compose exec mysql-source mysql --user=root -p
```

执行：

```sql
USE campus_pulse;
CREATE TABLE IF NOT EXISTS replica_demo (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  content VARCHAR(100) NOT NULL
);

INSERT INTO replica_demo (content) VALUES ('hello replica');
SELECT * FROM replica_demo;
```

再连从库：

```powershell
docker compose exec mysql-replica mysql --user=root -p
```

执行：

```sql
USE campus_pulse;
SELECT * FROM replica_demo;
```

如果你能在从库看到 `hello replica`，说明同步成功。

### 3.5 如何看复制状态

在从库里执行：

```sql
SHOW REPLICA STATUS\G
```

你重点看这几个字段：

- `Replica_IO_Running: Yes`
- `Replica_SQL_Running: Yes`
- `Seconds_Behind_Source: 0`

只要前两个是 `Yes`，基本就是通了。

---

## 4. 正式版：服务器上怎么搭

本节描述的是**生产拓扑**：已有主库与独立从库位于不同主机或故障域，通过
私网复制；它不是把上一节的双容器 Compose 直接搬到服务器。上线前必须先做
可恢复性验证过的备份，并准备回滚和维护窗口。生产复制账号只允许固定从库 IP，
Agent 另用仅有 `SELECT` / `SHOW VIEW` 的读取账号。

下面这部分假设你是：

- 主库服务器：`10.0.0.10`
- 从库服务器：`10.0.0.11`
- MySQL 版本：`8.0 / 8.4`
- 系统：`Ubuntu 22.04`

如果你不是 Ubuntu，也别慌，核心思路一样。

---

## 5. 第一步：先给主库做一次完整备份

这个步骤非常重要。

**别一上来就改配置。先备份。**

```bash
mysqldump -u root -p \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  --set-gtid-purged=ON \
  campus_pulse > campus_pulse_full.sql
```

为什么加 `--single-transaction`：

- 对 InnoDB 更友好
- 备份时不用长时间锁表

为什么加 `--set-gtid-purged=ON`：

- 后面用 GTID 复制更方便

---

## 6. 第二步：配置主库

### 6.1 找到 MySQL 配置文件

Ubuntu 常见位置：

```bash
/etc/mysql/mysql.conf.d/mysqld.cnf
```

### 6.2 加这些配置

在 `[mysqld]` 下面加：

```ini
server-id = 1
log_bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce_gtid_consistency = ON
log_replica_updates = ON
innodb_flush_log_at_trx_commit = 1
sync_binlog = 1
```

你可以这样理解：

- `server-id = 1`：给主库一个唯一编号
- `log_bin`：开启 binlog
- `binlog_format = ROW`：复制更稳，生产里更常见
- `gtid_mode = ON`：开启 GTID，主从切换更省心
- `enforce_gtid_consistency = ON`：保证 GTID 正常工作

### 6.3 重启 MySQL

```bash
sudo systemctl restart mysql
```

### 6.4 验证主库配置

登录 MySQL：

```bash
mysql -u root -p
```

执行：

```sql
SHOW VARIABLES LIKE 'server_id';
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'gtid_mode';
```

你预期看到：

- `server_id = 1`
- `log_bin = ON`
- `gtid_mode = ON`

---

## 7. 第三步：在主库创建复制账号

在主库执行：

```sql
CREATE USER 'repl'@'10.0.0.%' IDENTIFIED BY '换成强密码';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'10.0.0.%';
FLUSH PRIVILEGES;
```

如果你只知道从库固定 IP，比如 `10.0.0.11`，那就更好：

```sql
CREATE USER 'repl'@'10.0.0.11' IDENTIFIED BY '换成强密码';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'10.0.0.11';
FLUSH PRIVILEGES;
```

这样更安全。

---

## 8. 第四步：安装并配置从库

### 8.1 从库机器上装 MySQL

```bash
sudo apt update
sudo apt install -y mysql-server
```

### 8.2 修改从库配置

编辑：

```bash
sudo vim /etc/mysql/mysql.conf.d/mysqld.cnf
```

在 `[mysqld]` 下加入：

```ini
server-id = 2
log_bin = mysql-bin
relay_log = mysql-relay-bin
relay_log_recovery = ON
read_only = ON
super_read_only = ON
gtid_mode = ON
enforce_gtid_consistency = ON
log_replica_updates = ON
replicate_wild_do_table = campus_pulse.%
```

解释一下：

- `server-id = 2`：从库唯一编号，不能和主库重复
- `relay_log`：从库保存主库 binlog 的地方
- `relay_log_recovery`：异常重启后按复制元数据恢复 relay log
- `read_only = ON`：普通写入禁止
- `super_read_only = ON`：更严格，避免手滑往从库写
- `replicate_wild_do_table`：只接收 `campus_pulse` 库的表变更

### 8.3 重启从库

```bash
sudo systemctl restart mysql
```

---

## 9. 第五步：把主库数据导入从库

把第 5 步导出的 `campus_pulse_full.sql` 传到从库服务器。

比如：

```bash
scp campus_pulse_full.sql your-user@10.0.0.11:/tmp/
```

然后在从库导入：

```bash
mysql -u root -p < /tmp/campus_pulse_full.sql
```

导入后，先确认库在不在：

```sql
SHOW DATABASES;
USE campus_pulse;
SHOW TABLES;
```

---

## 10. 第六步：让从库连接主库

在从库执行：

```sql
STOP REPLICA;
RESET REPLICA ALL;

CHANGE REPLICATION SOURCE TO
  SOURCE_HOST = '10.0.0.10',
  SOURCE_PORT = 3306,
  SOURCE_USER = 'repl',
  SOURCE_PASSWORD = '换成你的复制密码',
  SOURCE_AUTO_POSITION = 1,
  GET_SOURCE_PUBLIC_KEY = 1;

START REPLICA;
```

这里最关键的是：

- `SOURCE_AUTO_POSITION = 1`
  - 表示用 GTID 自动定位
  - 不用你手动算 binlog 文件名和位置
- `GET_SOURCE_PUBLIC_KEY = 1`
  - 对 MySQL 8 的默认认证方式更友好

---

## 11. 第七步：检查从库状态

在从库执行：

```sql
SHOW REPLICA STATUS\G
```

重点看：

```text
Replica_IO_Running: Yes
Replica_SQL_Running: Yes
Seconds_Behind_Source: 0
```

如果你看到：

- `Replica_IO_Running = No`
  - 一般是网络不通、账号不对、权限不对
- `Replica_SQL_Running = No`
  - 一般是导入数据不一致，或者执行 SQL 冲突

---

## 12. 第八步：做一次真实同步测试

在主库执行：

```sql
USE campus_pulse;

CREATE TABLE IF NOT EXISTS replica_check (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note VARCHAR(100) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO replica_check (note) VALUES ('replication works');
SELECT * FROM replica_check;
```

一分钟内到从库执行：

```sql
USE campus_pulse;
SELECT * FROM replica_check;
```

如果能查到，正式说明 OK。

---

## 13. 你的项目接下来怎么用这个从库

### 13.1 给 Agent 用

你现在新加的 Python Agent 服务，已经支持直接连 MySQL。

等从库上线以后，把它的连接改成从库：

```env
AGENT_SEARCH_BACKEND=mysql
AGENT_MYSQL_REPLICA_DSN=jdbc:mysql://从库IP:3306/campus_pulse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
AGENT_MYSQL_REPLICA_USERNAME=agent_reader
AGENT_MYSQL_REPLICA_PASSWORD=请替换为只读账号密码
AGENT_MYSQL_REQUIRE_READ_ONLY=true
```

请给 `agent_reader` 仅授予 `campus_pulse` 库的 `SELECT`、`SHOW VIEW` 权限。这样 Agent 的搜索和问答不会压主库，误连到可写库时也会在启动阶段失败。

### 13.2 给 Spring Boot 主服务用

你项目里已经有读写分离配置入口，但现在还没真正全面接入。

所以推荐顺序是：

1. 先把 Agent 切从库
2. 再把搜索/统计类查询切从库
3. 最后再评估是否要把更多接口做读写分离

---

## 14. 常见报错和排查

### 14.1 `Replica_IO_Running: No`

先查：

```sql
SHOW REPLICA STATUS\G
```

看这几个字段：

- `Last_IO_Error`
- `Source_Host`
- `Source_User`

通常原因：

1. 主库没放通 3306
2. 从库连错 IP
3. 复制账号密码写错
4. 主库没开 `log_bin`

### 14.2 `Replica_SQL_Running: No`

通常原因：

1. 从库里已经有脏数据
2. 导入的数据和主库快照不一致
3. 主从某张表结构不一致

这时先看：

```sql
SHOW REPLICA STATUS\G
```

重点关注：

- `Last_SQL_Error`

### 14.3 `Can't connect to MySQL server`

先在从库机器测试网络：

```bash
telnet 10.0.0.10 3306
```

或者：

```bash
nc -zv 10.0.0.10 3306
```

如果不通，先查防火墙和安全组。

### 14.4 从库被人误写入了

从库一定要开：

```ini
read_only = ON
super_read_only = ON
```

这两个别省。

---

## 15. 我最建议你的实际落地方案

如果你现在是第一次搭，我建议你按这个顺序来：

1. 先跑本地 Docker 主从演示
2. 看懂 `SHOW REPLICA STATUS\G`
3. 再买或准备第二台 MySQL 机器
4. 真正把 `campus_pulse` 做成只读从库
5. 先让 Agent 连从库

这是最稳的。

别一上来就同时做：

- 主从复制
- PostgreSQL 搜索库
- Java 全量读写分离
- 缓存重构

那样很容易自己把自己绕晕。

---

## 16. 你下一步可以直接照着做的命令

### 如果你想先学会

```powershell
cd "D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse"
.\deploy\mysql-replication\bootstrap-replica.ps1
```

### 如果你想真给项目用

按这条主线走：

1. 主库开启 `log_bin + GTID`
2. 创建 `repl` 复制用户
3. 新机器装 MySQL
4. 从主库导出 `campus_pulse`
5. 导入从库
6. `CHANGE REPLICATION SOURCE TO ...`
7. `START REPLICA`
8. `SHOW REPLICA STATUS\G`
9. 把 Agent 指到从库

---

## 17. 最后一句大白话

你现在最需要的不是“最复杂最先进的数据库架构”，而是：

**先把一个稳定可验证的 MySQL 从库搭出来。**

只要这一步完成，你这个项目的 Agent、搜索、统计，就已经开始有企业化基础了。
