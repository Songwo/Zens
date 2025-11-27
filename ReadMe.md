# CampusPulse 后端

校园智能内容与趋势分析平台后端（Spring Boot 3）。提供用户认证、个人信息查询、基础内容与行为数据结构，为后续“热度/词云/推荐”等分析打基础。

## 技术栈
- Spring Boot 3.5.8、Spring MVC、Spring Validation
- Spring Security + JWT（`jjwt`）
- MyBatis-Plus（数据访问）
- MySQL（业务数据）
- Redis（令牌与缓存）
- HanLP 分词（趋势分析预留）
- Lombok（简化实体与 DTO）

## 运行环境
- JDK `17`
- Maven `>= 3.8`
- MySQL `>= 8.0`（默认 `localhost:3306`）
- Redis `>= 6`（默认 `localhost:6379`）

## 快速开始
- 数据库初始化
  - 导入：`src/main/resources/sql/Init.sql`
  - 可选触发器日志：`src/main/resources/sql/exec_log.sql`
- 配置文件：`src/main/resources/application.yml`
  - `server.port: 7800`
  - `spring.datasource.*`（数据库）
  - `spring.data.redis.*`（Redis）
  - `jwt.secret`、`jwt.access-expire`、`jwt.refresh-expire`
- 构建与运行（Windows）
  ```powershell
  mvn clean package
  java -jar .\target\campus-pulse-0.0.1-SNAPSHOT.jar
