@echo off
chcp 65001 >nul
echo ========================================
echo   SimpleMDM — 启动服务
echo ========================================

echo [1/2] 启动后端...
cd /d "%~dp0backend-java"
start "SimpleMDM-Backend" cmd /c "mvn spring-boot:run"
cd /d "%~dp0"

echo [2/2] 启动前端...
cd /d "%~dp0frontend"
start "SimpleMDM-Frontend" cmd /c "npm run dev"
cd /d "%~dp0"

echo ========================================
echo   后端: http://localhost:18001
echo   前端: http://localhost:5173
echo   API文档: http://localhost:18001/docs  (已废弃，使用Swagger替代URL)
echo ========================================
echo   关闭窗口或运行 stop.bat 停止服务
pause
