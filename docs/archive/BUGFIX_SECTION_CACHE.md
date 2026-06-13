# 🔧 板块缓存问题修复指南

## 问题描述

新增"答疑解惑"板块后，前端发帖时看不到新板块选项。

## 原因分析

后端使用Redis缓存板块列表，缓存键为：
- `section:list:active` - 启用的板块列表
- `section:list:all` - 所有板块列表

缓存TTL为60秒，新增板块后旧缓存未失效，导致前端获取不到最新板块数据。

## 解决方案

### 方法1：手动清除缓存（立即生效）

**Windows环境**:
```bash
# 运行清除缓存脚本
scripts\clear-section-cache.bat
```

**Linux/Mac环境**:
```bash
# 运行清除缓存脚本
bash scripts/clear-section-cache.sh
```

**或使用Redis命令**:
```bash
redis-cli DEL "section:list:active" "section:list:all"
```

### 方法2：等待缓存过期（60秒后自动生效）

缓存会在60秒后自动过期，之后前端刷新页面即可看到新板块。

### 方法3：重启后端应用

重启后端应用会清空所有缓存：
```bash
# 停止应用后重新启动
mvn spring-boot:run
```

## 验证步骤

### 1. 检查数据库
```sql
SELECT id, name, status, sort_order, allow_adoption 
FROM sections 
ORDER BY sort_order, id;
```

预期结果：
```
id   name      status  sort_order  allow_adoption
11   答疑解惑   1       0           1
1    技术交流   1       1           0
2    学习资源   1       2           0
3    生活分享   1       3           0
4    求职招聘   1       4           0
5    闲聊灌水   1       5           0
```

### 2. 检查Redis缓存
```bash
# 查看缓存键
redis-cli KEYS "section:*"

# 查看缓存内容
redis-cli GET "section:list:active"
```

### 3. 测试前端
1. 清除缓存后，刷新前端页面
2. 点击"发帖"按钮
3. 在"选择板块"下拉框中应该能看到"答疑解惑"选项
4. "答疑解惑"板块应该排在第一位（sort_order=0）

## 新增板块功能说明

### "答疑解惑"板块特性

- **板块ID**: 11
- **排序**: 0（置顶显示）
- **状态**: 启用
- **特殊功能**: 支持答案采纳（`allow_adoption=1`）

### 答案采纳功能

只有在"答疑解惑"板块发布的帖子才能：
- 采纳最佳答案
- 回答者获得 +15声望 +20经验
- 帖子显示"已解决"标记

其他板块（技术交流、学习资源等）不支持答案采纳功能。

## 常见问题

### Q1: 为什么清除缓存后还是看不到新板块？

**A**: 请检查：
1. Redis是否正在运行（`redis-cli ping`）
2. 后端应用是否正常运行
3. 浏览器是否刷新页面（F5或Ctrl+R）
4. 浏览器缓存是否清除（Ctrl+Shift+Delete）

### Q2: 如何添加更多板块？

**A**: 
```sql
-- 插入新板块
INSERT INTO sections (name, description, icon, sort_order, status, allow_adoption)
VALUES ('新板块名称', '板块描述', 'icon-name', 10, 1, 0);

-- 清除缓存
redis-cli DEL "section:list:active" "section:list:all"
```

### Q3: 如何开启其他板块的答案采纳功能？

**A**:
```sql
-- 开启指定板块的答案采纳
UPDATE sections SET allow_adoption = 1 WHERE id = 1;

-- 清除缓存
redis-cli DEL "section:list:active" "section:list:all"
```

## 自动化脚本

项目提供了缓存清除脚本：
- `scripts/clear-section-cache.bat` (Windows)
- `scripts/clear-section-cache.sh` (Linux/Mac)

脚本功能：
1. 检查Redis连接
2. 清除板块缓存
3. 显示当前数据库中的板块列表

## 预防措施

为避免类似问题，建议：

1. **开发环境**：设置较短的缓存TTL（如10秒）
2. **生产环境**：保持60秒TTL，平衡性能和数据新鲜度
3. **管理后台**：添加"清除缓存"按钮
4. **数据变更**：修改板块后自动清除相关缓存

## 相关代码

**缓存配置**:
- 文件：`SectionServiceImpl.java`
- 缓存TTL：60秒
- 缓存键：`section:list:active`, `section:list:all`

**前端调用**:
- 文件：`PostComposerModal.vue`
- API：`/section/active`
- 方法：`fetchCategories()`

---

**修复日期**: 2026-06-08  
**问题状态**: ✅ 已解决  
**解决方式**: 清除Redis缓存
