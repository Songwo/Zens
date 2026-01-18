# 🏫 CampusPulse: 校园智能内容趋势分析与全功能管理平台

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.8-green?style=for-the-badge&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.0-4FC08D?style=for-the-badge&logo=vue.js" alt="Vue 3">
  <img src="https://img.shields.io/badge/Vite-Latest-646CFF?style=for-the-badge&logo=vite" alt="Vite">
  <img src="https://img.shields.io/badge/Tailwind-3.0-38B2AC?style=for-the-badge&logo=tailwind-css" alt="Tailwind CSS">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-6.2-DC382D?style=for-the-badge&logo=redis" alt="Redis">
</div>

---

## 🌟 项目愿景

**CampusPulse** 旨在打造一个“懂学生、助学业”的智能化校园生态系统。它不仅是学生分享生活、探索趋势的社交港湾，更是集成了教务、学工、生活服务的高效数字化平台。通过数据驱动的决策支持，CampusPulse 能够精准感知校园脉搏，为每一位学生提供个性化的学业引导与内容推送。

---

## ✨ 核心功能深度解析

### 1. 🧠 智能内容大脑 (Intelligent Content Engine)
*   **混合推荐算法 (Hybrid Recommendation)**:
    - **兴趣画像 (40%)**: 基于用户关注的标签（Tags）计算相关度分数，并辅以时间衰减因子（1 / (1 + days/7)^1.2）确保内容新鲜度。
    - **协同过滤 (30%)**: 采用 **Item-Based CF**。通过分析“看过该帖子的用户也看过的其他内容”，并根据用户行为加权（浏览:1.0, 点赞:3.0, 收藏:5.0）进行计算。
    - **背景关联**: 自动识别学生专业，优先推荐同学院、同专业的高质量学术讨论或经验分享。
*   **NLP 语义感知**:
    - **趋势分析**: 实时提取校园动态中的高频热词，生成动态词云。
    - **情感监测**: 基于 HanLP 分词与情感词典，对社区氛围进行正负向建模（0.0-1.0 评分制），自动过滤极端负面情绪。

### 2. 🎓 全功能教务管理 (Integrated SIMS)
*   **学籍档案中心**: 
    - 数字化存储身份证、学号、入学时间等关键信息。
    - **宿舍管理**: 精确到校区、楼栋、房间及床位的空间档案。
*   **学业评价系统**:
    - **成绩看板**: 按学期聚合展示分数与绩点，直观反馈学业状态。
    - **毕业自检**: 核心逻辑自动扫描已获学分，对比毕业要求（如120学分），实时计算剩余差距。
*   **互动选课系统**:
    - **高并发适配**: 利用 Redis 缓存与数据库行锁，确保在高并发选课期间的容量（Capacity）控制准确无误。
    - **逻辑校验**: 自动拦截重复选课、学分超额、时间冲突等异常申请。

### 3. 📋 校园行政与生活 (Campus Services)
*   **请假审批流**: 学生线上提交事假/病假申请（附带理由与时段），管理员后台一键审批，系统自动推送处理结果。
*   **感知式弹窗**: 系统能够识别“首次登录”或“重要通知”，通过高度自定义的弹窗组件（WelcomePopup）确保关键公告的 100% 触达。

---

## 🏗 技术架构与设计模式

### 1. 后端架构 (Spring Boot 3)
- **安全体系**: **Spring Security + JWT**。采用无状态认证，JWT 存储在客户端，服务端通过拦截器校验权限，支持多角色（学生、老师、管理员）访问控制。
- **持久化层**: **MyBatis-Plus** 配合雪花算法 ID 生成器，保证了海量数据下的查询性能与主键唯一性。
- **性能优化**: 
    - **Redis 二级缓存**: 针对热门帖子、标签排行等高频访问数据进行缓存，降低数据库负载。
    - **异步处理**: 情感分析与标签提取采用异步处理，提升接口响应速度。

### 2. 前端架构 (Vue 3 + Vite)
- **现代 UI 方案**: 基于 **Tailwind CSS** 的 Utility-First 理念，配合 **Lucide Icons**，构建出符合 Z 世代审美的高颜值、响应式界面。
- **状态管理**: **Pinia** 实现用户状态与配置的全局响应式流转。
- **工程化**: 严格的 TypeScript 类型约束，配合 Vite 的极速热更新，大幅提升开发效率。

---

## � 开发与部署指南

### 环境要求
- **JDK 17+**
- **Node.js 18+**
- **MySQL 8.0.x**
- **Redis 6.x+**

### 1. 数据库配置
```bash
# 1. 创建数据库
mysql -e "CREATE DATABASE IF NOT EXISTS campus_pulse DEFAULT CHARACTER SET utf8mb4;"

# 2. 依次执行 SQL 脚本 (位于 src/main/resources/sql/)
# - Init.sql (基础表)
# - student_system_init.sql (教务模块)
# - test_data_for_recommend.sql (算法测试数据)
```

### 2. 后端启动
```bash
# 修改 application.yml 中的 mysql/redis 连接
mvn clean install
mvn spring-boot:run
```

### 3. 前端启动
```bash
cd web
npm install
npm run dev
```

---

## 📁 项目关键目录
```text
campus-pulse/
├── src/main/java/com/campus/trend/campus_pulse/
│   ├── service/impl/           # 推荐算法与教务核心逻辑所在地
│   │   ├── PostRecommendServiceImpl.java     # 混合推荐核心
│   │   ├── SentimentAnalysisServiceImpl.java # 情感分析
│   │   └── StudentServiceImpl.java           # 学籍与毕业检查
│   ├── config/                  # 安全、Redis、CORS 全局配置
│   └── filter/                  # JWT 权限拦截
└── web/src/
    ├── api/                     # 统一 API 调用封装
    ├── views/                   # 业务页面 (Academic, Course, Profile)
    └── store/                   # Pinia 状态树
```

---

## 🔮 未来规划 (Roadmap)
- [ ] **AI 辅导员**: 集成大语言模型（LLM），提供 24/7 的学业规划建议。
- [ ] **实时通讯**: 基于 WebSocket 实现学生间的即时私聊与学术讨论组。
- [ ] **可视化大屏**: 为校方提供校园热度与学生心理健康指数的实时监控大屏。

---

## 📄 许可证
本项目采用 **MIT License**。欢迎用于毕业设计、学术研究或个人学习。
