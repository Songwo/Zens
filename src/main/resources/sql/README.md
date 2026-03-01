# Campus Pulse SQL 使用说明

## 统一入口
- 唯一初始化脚本: `campus_pulse_schema.sql`
- 本目录内其余历史增量脚本已合并进上述文件，不再单独执行。

## 环境要求
- MySQL 8.0+
- 字符集: `utf8mb4`

## 初始化步骤
1. 确保 MySQL 服务可用。
2. 执行脚本:

```bash
mysql -u root -p < src/main/resources/sql/campus_pulse_schema.sql
```

3. 脚本会创建 `campus_pulse` 数据库并初始化完整表结构、索引与部分基础数据。

## 维护约定
- 后续结构变更统一回写到 `campus_pulse_schema.sql`。
- 禁止新增“散落式”临时 SQL，避免线上线下结构漂移。
