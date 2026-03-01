# UI Style Guide (Campus Pulse - Amber Theme)

## 1. 设计目标
- **一眼技术社区**：极客感、信息密度高、卡片边界干净。
- **品牌黄色系**：主色采用“琥珀黄 (Amber)”，仅用于关键强调点（按钮、选中态、特殊徽章、统计数字强调），避免大面积使用导致刺眼。背景和基础框架保持灰白，确保长时间阅读友好。
- **一致性**：前台（C端社区）和后台（B端管理）共用同套色彩规范，但后台强调信息密度和数据展示。

## 2. 色彩规范 (Color Palette)

### 主色 (Brand Colors)
- **Primary (品牌黄)**: `#F4B400` (用于核心按钮、激活Tab、重点数字)
- **Primary Hover**: `#E6A700` (悬浮态)
- **Primary Active**: `#D99500` (按压态)

### 中性色 (Neutral Colors - 阅读友好)
- **Page BG (页面背景)**: `#F5F7FA` (极浅灰，降低对比度疲劳)
- **Card BG (卡片/容器背景)**: `#FFFFFF`
- **Border Light (极浅边框)**: `#F3F4F6`
- **Border Base (基础边框)**: `#E5E7EB` 
- **Text Primary (主标题/强调)**: `#1F2937`
- **Text Regular (正文内容)**: `#4B5563`
- **Text Secondary (次要信息/辅助)**: `#6B7280`
- **Text Muted (失效/极弱)**: `#9CA3AF`

### 状态色 (Semantic Colors)
- **Success**: `#16A34A` (成功、通过)
- **Warning**: `#F59E0B` (警告、待审核 - 偏橙色拉开与主色的差距)
- **Danger**: `#EF4444` (危险、删除、失败)
- **Info**: `#3B82F6` (普通提示、连接、默认标签)

## 3. 字体与排版 (Typography)
- **Base Font Size**: `14px` (保证阅读足够清晰)
- **H1 (大标题)**: `24px`, Font Weight: `800` (如详情页大标题)
- **H2 (模块标题)**: `18px`-`20px`, Font Weight: `700`
- **H3 (卡片标题)**: `16px`, Font Weight: `600`
- **Body (正文)**: `14px`, Font Weight: `400`/`500`, Line Height: `1.6`~`1.8`
- **Small (时间/统计/标签)**: `12px`-`13px`, Font Weight: `500`

## 4. 空间与布局 (Spacing & Layout)
- **容器安全宽度**: `max-width: 1280px`
- **模块间距 (Gap/Margin)**: `16px` ~ `24px`
- **卡片内边距 (Padding)**: `16px` (小卡片) ~ `24px`/`32px` (详情内容区)
- **统一圆角 (Border Radius)**: 
  - 基础组件 (Tag, Button, Input): `6px`
  - 卡片/弹窗 (Card, Dialog): `10px` ~ `12px`
- **阴影 (Box Shadow)**:
  - 基础状态悬浮：由无阴影切换至轻阴影 (`0 2px 12px 0 rgba(0,0,0,0.05)`)
  - 浮层弹窗：`0 8px 24px rgba(0,0,0,0.08)`

## 5. 核心组件规范 (Component Specs)
- **Buttons（按钮）**: 圆角 6px，主按钮为黄底白字，次要按钮为浅灰底暗字或线框。
- **Tabs（面板）**: 隐藏多余边框，激活状态底部 3px 黄色线条提示并加粗字体。
- **Tags（标签）**: 圆角 4px~6px。信息标签使用浅灰底色（无边框或极淡边框），特殊身份或状态态（精华/顶置）使用黄色底或强表现力色彩。
- **Tables（表格 - Admin重点）**: 增大行高 (`52px+`)，表头灰底 (`#F9FAFB`)，斑马纹/Hover响应。
- **Inputs（输入框）**: 圆角 6px，常态极淡边框或浅灰背景，聚焦聚焦时边框变黄并加强背景反差。
