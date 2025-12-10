<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.8-green?style=for-the-badge&logo=springboot" alt="Spring Boot Version">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java" alt="Java Version">
  <img src="https://img.shields.io/badge/MySQL-8.0-blue?style=for-the-badge&logo=mysql" alt="MySQL Version">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" alt="License">
</div>

<h1 align="center">🏫 CampusPulse Backend</h1>

<p align="center">
  <b>校园智能内容趋势分析与推荐平台后端</b>
</p>

<p align="center">
  基于 Spring Boot 3 构建，提供用户认证、个人信息管理、内容发布等功能，
  为校园内容的热度分析、词云生成和个性化推荐奠定数据基础。
</p>

---

## 🌟 项目简介

CampusPulse 是一个面向高校的智能化内容与趋势分析平台后端系统。该系统采用现代化的技术架构，实现了用户认证、个人信息管理、内容发布等核心功能，并通过收集用户行为数据，为后续的数据分析模块（热度排行、词云展示、个性化推荐等）提供坚实的基础。

### ✨ 核心特性

- 🔐 **安全认证** - 基于 JWT 和 Spring Security 的用户身份验证
- 👤 **用户管理** - 完整的用户资料管理功能
- 📝 **内容服务** - 支持多板块的内容发布与管理
- 📊 **数据分析准备** - 结构化数据设计，为趋势分析预留接口
- ⚡ **高性能** - 使用 Redis 缓存提升系统响应速度
- 🛡️ **稳定性** - 完善的异常处理机制和参数校验

---

## 🛠 技术栈

| 技术 | 用途 | 版本 |
|------|------|------|
| ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-green?style=flat-square&logo=springboot) | 核心框架 | 3.5.8 |
| ![Spring MVC](https://img.shields.io/badge/Spring%20MVC-Framework-green?style=flat-square&logo=spring) | Web 层框架 | - |
| ![Spring Security](https://img.shields.io/badge/Spring%20Security-Security-green?style=flat-square&logo=springsecurity) | 安全框架 | - |
| ![JWT](https://img.shields.io/badge/JWT-Token%20Management-black?style=flat-square&logo=jsonwebtokens) | Token 管理 | jjwt |
| ![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-ORM-green?style=flat-square&logo=apachemaven) | ORM 框架 | 3.5.7 |
| ![MySQL](https://img.shields.io/badge/MySQL-Database-blue?style=flat-square&logo=mysql) | 主数据库 | >= 8.0 |
| ![Redis](https://img.shields.io/badge/Redis-Cache-red?style=flat-square&logo=redis) | 缓存系统 | >= 6 |
| ![HanLP](https://img.shields.io/badge/HanLP-NLP-yellow?style=flat-square&logo=nlp) | 中文分词 | portable-1.8.6 |
| ![Lombok](https://img.shields.io/badge/Lombok-Utility-red?style=flat-square&logo=lombok) | 简化开发 | - |

---

## 🏗 运行环境

| 环境 | 版本要求 |
|------|----------|
| JDK | `17` |
| Maven | `>= 3.8` |
| MySQL | `>= 8.0` (默认 `localhost:3306`) |
| Redis | `>= 6` (默认 `localhost:6379`) |

---

## 🚀 快速开始

### 1. 数据库初始化

```bash
# 导入初始数据结构和测试数据
mysql -u root -p < src/main/resources/sql/Init.sql

# 可选：导入执行日志触发器
mysql -u root -p < src/main/resources/sql/exec_log.sql
```

### 2. 配置文件调整

编辑配置文件 `src/main/resources/application.yml`：

```yaml
server:
  port: 7800  # 默认端口

spring:
  datasource:  # 数据库配置
    url: jdbc:mysql://localhost:3306/campus_pulse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    
  data:
    redis:  # Redis 配置
      host: localhost
      port: 6379

jwt:
  secret: "your-secret-key-here"  # JWT 密钥
  access-expire: 3600000          # Access Token 过期时间 (1小时)
  refresh-expire: 604800000       # Refresh Token 过期时间 (7天)
```

### 3. 构建与运行

#### Windows 环境

```powershell
# 清理并打包
mvn clean package

# 运行应用
java -jar .\target\campus-pulse-0.0.1-SNAPSHOT.jar
```

#### Linux/Mac 环境

```bash
# 清理并打包
mvn clean package

# 运行应用
java -jar ./target/campus-pulse-0.0.1-SNAPSHOT.jar
```

### 4. 验证部署

启动成功后，访问以下地址确认服务正常运行：

```
http://localhost:7800/sys-user/test
```

如果返回 "test" 字符串，则说明服务已成功启动。

---

## 📁 项目结构

```
src/main/java/com/campus/trend/campus_pulse/
├── common/           # 通用类（统一返回结果、状态码等）
├── config/           # 配置类（安全、Redis、Web、静态资源配置）
├── controller/       # 控制器层
├── dto/              # 数据传输对象
│   ├── request/      # 请求DTO
│   └── response/     # 响应DTO
├── entity/           # 实体类
├── enums/            # 枚举类
├── exception/        # 异常处理
├── filter/           # 过滤器
├── mapper/           # MyBatis Mapper接口
├── security/         # 安全相关
├── service/          # 服务层
│    └── impl/         # 服务实现
├── utils/            # 工具类
└── CampusPulseApplication.java  # 启动类
```

---

## 📞 API Endpoints

### 认证相关

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/login` | POST | 用户登录 |
| `/auth/register` | POST | 用户注册 |
| `/auth/logout` | POST | 用户登出 |

### 用户相关

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/sys-user/profile` | GET | 获取用户详细信息 |
| `/sys-user/simple-profile` | GET | 获取用户简要信息 |
| `/sys-user/update-pwd` | POST | 更新用户密码 |
| `/sys-user/update-udetail` | POST | 更新用户详细信息 |
| `/sys-user/avatar` | PUT | 上传用户头像 |

### 帖子相关

| Endpoint                 | Method | Description |
|--------------------------|--------|-------------|
| `/sys-post/{id}}`        | GET    | 获取帖子详细信息    |
| `/sys-post/create-post`  | POST   | 创建帖子        |
| `/sys-post/extract-tags` | GET    | 根据文章内容加载标签  |
| `/sys-post/search-lists` | POST   | 获取文章集合      |
📝 /sys-post/search-lists — 文章列表查询接口说明
📌 接口描述

该接口用于 分页查询帖子列表，支持多条件组合查询，包括：

分类 ID

用户 ID

发布状态

标题/内容关键字模糊搜索

分页功能

搜索结果按 创建时间倒序 排序返回。

📍 请求方式
POST /sys-post/search-lists

🧩 请求参数（JSON）
字段名	类型	是否必填	说明

categoryID	String	否	分类 ID，用于筛选指定分类下的文章

userID	String	否	用户 ID，用于筛选该用户发布的帖子

keyword	String	否	搜索关键词，可在标题和内容中模糊匹配

status	Integer	否	帖子状态，例如：0=草稿 1=发布 2=隐藏

page	Integer	是	第几页，从 1 开始

pageSize	Integer	是	每页条数

---

## 🤝 贡献

欢迎提交 Issue 或 Pull Request 来帮助改进这个项目！

---

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。