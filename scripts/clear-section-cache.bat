@echo off
REM ========================================
REM Campus Pulse - 清除板块缓存脚本
REM 用途：新增板块后清除Redis缓存，使前端能看到新板块
REM ========================================

echo.
echo ====================================
echo   Campus Pulse 板块缓存清除工具
echo ====================================
echo.

REM 检查Redis是否运行
redis-cli ping >nul 2>&1
if errorlevel 1 (
    echo [错误] Redis未运行！
    echo 请先启动Redis服务：
    echo D:\Program Files\Redis-x64-6.0.20\redis-server.exe
    echo.
    pause
    exit /b 1
)

echo [1/3] Redis连接成功
echo.

REM 清除板块相关缓存
echo [2/3] 正在清除板块缓存...
redis-cli DEL "section:list:active" "section:list:all" >nul 2>&1
echo       ✓ section:list:active
echo       ✓ section:list:all
echo.

REM 查看数据库中的板块
echo [3/3] 当前数据库中的板块：
mysql --default-character-set=utf8mb4 -u root -p123456 campus_pulse -e "SELECT id, name, status, sort_order, allow_adoption FROM sections ORDER BY sort_order, id;" 2>nul
echo.

echo ====================================
echo   缓存清除完成！
echo ====================================
echo.
echo 说明：
echo - 前端刷新页面后会重新加载板块列表
echo - "答疑解惑"板块已开启答案采纳功能
echo - 其他板块不支持答案采纳
echo.
pause
