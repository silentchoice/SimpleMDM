@echo off
chcp 65001 >nul
echo ========================================
echo   SimpleMDM — 停止服务
echo ========================================

echo [1/2] 停止后端 (端口 18001)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":18001" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   ✓ Java 进程 %%a 已停止
)

echo [2/2] 停止前端 (端口 5173)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   ✓ Node 进程 %%a 已停止
)

echo ========================================
echo   服务已全部停止
echo ========================================
pause
