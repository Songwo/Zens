#!/bin/bash
# ========================================
# Campus Pulse - 清除板块缓存脚本
# 用途：新增板块后清除Redis缓存，使前端能看到新板块
# ========================================

echo ""
echo "===================================="
echo "  Campus Pulse 板块缓存清除工具"
echo "===================================="
echo ""

# 检查Redis是否运行
if ! redis-cli ping >/dev/null 2>&1; then
    echo "[错误] Redis未运行！"
    echo "请先启动Redis服务"
    echo ""
    exit 1
fi

echo "[1/3] Redis连接成功"
echo ""

# 清除板块相关缓存
echo "[2/3] 正在清除板块缓存..."
redis-cli DEL "section:list:active" "section:list:all" >/dev/null 2>&1
echo "      ✓ section:list:active"
echo "      ✓ section:list:all"
echo ""

# 查看数据库中的板块
echo "[3/3] 当前数据库中的板块："
mysql --default-character-set=utf8mb4 -u root -p123456 campus_pulse -e "SELECT id, name, status, sort_order, allow_adoption FROM sections ORDER BY sort_order, id;" 2>/dev/null
echo ""

echo "===================================="
echo "  缓存清除完成！"
echo "===================================="
echo ""
echo "说明："
echo "- 前端刷新页面后会重新加载板块列表"
echo "- \"答疑解惑\"板块已开启答案采纳功能"
echo "- 其他板块不支持答案采纳"
echo ""
