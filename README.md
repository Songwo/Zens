<div align="center">
  <img src="https://api.dicebear.com/7.x/shapes/png?seed=Zens&backgroundColor=000000&radius=10" width="120" alt="Zens Logo" />
  <h1>Zens 社区 (Campus Pulse)</h1>
  <p><strong>新一代智能校园/技术交流社区系统</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Spring%20Boot-3.1.2-brightgreen.svg" alt="Spring Boot">
    <img src="https://img.shields.io/badge/Vue.js-3.0-blue.svg" alt="Vue.js">
    <img src="https://img.shields.io/badge/DeepSeek-AI-orange.svg" alt="DeepSeek AI">
    <img src="https://img.shields.io/badge/HackerNews-Algorithm-ff6600.svg" alt="Algorithm">
  </p>
</div>

---

## 📖 项目简介
**Zens 社区 (Campus Pulse)** 是一款现代化的全栈分布式社区论坛系统。核心围绕极简、高效与智能打造。搭载了类 Hacker News 的帖子热度衰减算法，以及全面接入了 **DeepSeek** 大模型实现 AI 智能摘要与发帖标签提取，致力于保障社区内容的高质量与纯净度。

本项目后端基于 `Spring Boot 3` + `MyBatis-Plus`，前端使用 `Vue 3` + `TypeScript` + `Vite` 构建，并内置基于 DFA 算法的全局敏感词脱敏防护与高亮邮件通知模版。

---

## ✨ 核心特性

- 🤖 **AI 深度驱动 (DeepSeek Integration)**: 抛弃笨重的本地 NLP 模型，全面接入 DeepSeek 大语言模型，针对每一篇长文本帖子自动抽取结构化 `Tags(标签)` 与高度浓缩的 `Summary(摘要)`。
- 🔥 **真实动态热力引擎 (Dynamic Heat Decay)**: 创新的帖子热度算法引擎：`Interaction / (1 + Age_Hours)^Gravity`。结合点赞、收藏、评论与浏览量赋予基础分，并随时间指数级衰减。更引入**情感惩罚机制**，自动对负能量违规帖进行算法级降权。
- 🛡️ **全局敏感词巡检 (Content Security)**: 采用高性能 DFA (确定有穷自动机) 算法，全面接管用户的文章、评论及「用户个人资料(昵称/简介)」的修改，自动实现敏感词阻断与替换(*)。
- 🎨 **现代化 UI / UX (Modern Interface)**: 前端采用纯净的暗/亮色双模自适应极简 UI。在「板块分类」中支持无缝平滑浏览体验。配置了「Zens」高级定制版的深色极验邮件模板。
- ⏱️ **精准实时通信 (Real-time WS)**: 内置高可用 WebSocket 连接池，支持全局与特定板块的新消息/点赞/回复实时通知与浮窗红点。

---

## 🛠️ 技术栈清单

### 后端 (Backend)
- **核心框架**: Spring Boot 3.x, Spring Security (JWT 无状态认证)
- **数据持久化**: MySQL 8.0, MyBatis-Plus
- **缓存与消息**: Redis (Spring Data Redis)
- **AI 大模型接入**: Spring `RestTemplate` 直连 DeepSeek `chat/completions` 标准流协议
- **其他核心工具**: Jackson (JSON处理器), JavaMailSender (定制邮件), HanLP (底层兜底保留)

### 前端 (Frontend)
- **核心框架**: Vue 3 (Composition API), TypeScript
- **构建工具**: Vite
- **UI 组件库**: Element Plus (全系暗色模式完美兼容)
- **状态管理**: Pinia
- **网络与通信**: Axios, WebSocket API 支持

---

## ⚙️ 快速部署与运行指南

### 1. 环境准备
确保您的服务器（或本地开发机）已安装以下必备环境：
- Node.js >= 18.x
- JDK >= 17
- MySQL >= 8.0
- Redis >= 6.0 (默认端口 6379 需开放或映射)

### 2. 数据库初始化
1. 在您的 MySQL 实例中，创建一个全新的库并命名为 `campus_pulse`。
2. 找到项目工程中的 `src/main/resources/sql/campus_pulse_schema.sql`，它是全量合成版 SQL 脚本。
3. 直接执行导入！这一张脚本将为您重建所有的精简表结构以及核心初始分类数据。

### 3. 后端服务配置 (Backend Setup)
在 `src/main/resources/application.yml` (或 application-dev.yml) 中按您线上的真实情况修改以下节点：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/campus_pulse?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: <您的数据库密码>

  data:
    redis:
      host: localhost
      port: 6379 
      password: <您的Redis密码，如无则留空>

  mail:
    host: smtp.163.com # 或您的其余 SMTP 服务器 (如 QQ: smtp.qq.com)
    username: <您的发件人邮箱账号>
    password: <您的SMTP授权鉴权码>

# --- ✨ AI 模型配置环节 ✨ ---
ai:
  deepseek:
    # 兼容标准的 OpenAI 协议的 endpoint。可以是基地址（自动拼装 /chat/completions）
    api-url: "https://api.deepseek.com" 
    api-key: "sk-xxxxxxxx" # 必填：请填入您申请的有效 DeepSeek API Key
    model: "deepseek-chat"
```

启动后端：
```bash
# 推荐使用 Maven 打包
mvn clean package -DskipTests

# 将生在 target 目录下的 jar 包投运到后台
java -jar target/campus-pulse-1.0.jar
```
*后端成功运行后默认挂载端口将是 `7800`。*

### 4. 前端服务构建 (Frontend Build)
前端项目的源码及核心配置位于 `web/` 目录中。

```bash
cd web

# 1. 下载组件依赖模块 (推荐使用淘宝镜像)
npm config set registry https://registry.npmmirror.com/
npm install

# 2. 本地开发调试运行状态
npm run dev

# 3. 生产环境混淆极速打包
npm run build
```
执行完毕后，生成的极致压缩资源将集中在 `web/dist/` 目录下。此时您可将该资源文件使用 Nginx (反向代理) 或 Docker 挂载成线上 Web 服务器以供外网访问。

---

## 📁 生产级纯净架构

所有研发时期的测试、探针类文书早已一扫而空，当下是最干净也是含金量最高的文件架构表：

```text
campus-pulse/
├── src/main/java.../service/
│   ├── impl/
│   │   ├── PostServiceImpl.java         # 核心推文/交互引擎 (挂载了AI解析拦截点)
│   │   ├── DeepSeekServiceImpl.java     # 专用 AI 大模型对接驱动基座
│   │   ├── AuthServiceImpl.java         # Token 派发鉴权
│   │   ├── MailServiceImpl.java         # 惊艳的「Zens版」暗色验证码模版池
│   ├── scheduled/
│       ├── PostHeatDecayTask.java       # 定时全局执行热度引力下落调度分配计算器
│
├── src/main/resources/
│   ├── application.yml                  # 后端运行脉络大管家
│   ├── sql/campus_pulse_schema.sql      # 一键直达装配表 (The ONLY Database File you need)
│
├── web/                                 # 智慧前端 (全栈自包含 Vue3)
│   ├── src/components/                  # 分治化挂件组件柜 (如弹窗/高级分类树)
│   ├── src/pages/                       # SPA 呈现骨干
│   ├── package.json
```

---

## 🔐 生产环境安全与排障建议

- 🗝️ **数据密文妥防**: 当部署至公网服务器并开放访问时，请务必保证 `application.yml` 中的 **MySQL密匙** 及 **SMTP邮箱授权码** 不会遭遇意外的分支并线外泄。本项目的 `.gitignore` 对多余的文件结构已具备强大的抗干扰脱敏阻断。
- 🔓 **跨域预检防护策略**: Spring Boot 层已支持并开放 `CorsConfig`。在多云节点服务器分离部署时，请自行将跨站点配置列表的域名白名单锁定至对应的合法源头避免被滥用。
- 🤖 **DeepSeek API 密匙存放**: LLM 的 Key 必须且仅被允许存放在受到网关保护的 Spring Boot (`YML` 持久层) 内存中，**严禁下发给 Vue 暴露至浏览器客户端** 以防恶意刷量！

---

<p align="center">
  <i>"为热爱技术的校园开发者铸就的纯净数字花园体验。"</i> <br>
  <b><a href="#">© 2026 Zens 社区 版权所有</a></b>
</p>
