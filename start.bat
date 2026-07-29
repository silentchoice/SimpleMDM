@echo off
chcp 65001 >nul
echo ========================================
echo   SimpleMDM — 启动服务
echo ========================================

set SCRIPT_DIR=%~dp0

echo [1/2] 启动后端...
start "SimpleMDM-Backend" cmd /c "cd /d %SCRIPT_DIR%backend-java && call mvnw.cmd spring-boot:run"

echo [2/2] 启动前端...
start "SimpleMDM-Frontend" cmd /c "cd /d %SCRIPT_DIR%frontend && npm run dev"

echo ========================================
echo   后端: http://localhost:18001
echo   前端: http://localhost:5173
echo   API文档: http://localhost:18001/docs  (已废弃，使用Swagger替代URL)
echo ========================================
echo   关闭窗口或运行 stop.bat 停止服务
pause
