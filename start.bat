@echo off
chcp 65001 >nul
echo ==============================================
echo   SimpleMDM - 主数据管理平台 Demo
echo ==============================================
echo.

:: Check Python
where python >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未找到 Python，请先安装 Python 3.10+
    pause
    exit /b 1
)

:: Check Node.js
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未找到 Node.js，请先安装 Node.js 18+
    pause
    exit /b 1
)

:: Install backend dependencies
echo [1/4] 安装后端依赖...
cd /d "%~dp0backend"
pip install -r requirements.txt -q
if %errorlevel% neq 0 (
    echo [ERROR] 后端依赖安装失败
    pause
    exit /b 1
)
echo       后端依赖安装完成

:: Install frontend dependencies
echo [2/4] 安装前端依赖...
cd /d "%~dp0frontend"
call npm install --silent
if %errorlevel% neq 0 (
    echo [ERROR] 前端依赖安装失败
    pause
    exit /b 1
)
echo       前端依赖安装完成

:: Start backend
echo [3/4] 启动后端服务 (port 18001)...
cd /d "%~dp0backend"
start "SimpleMDM Backend" cmd /c "python run.py"
echo       等待后端启动...
timeout /t 4 /nobreak >nul

:: Start frontend
echo [4/4] 启动前端服务 (port 5173)...
cd /d "%~dp0frontend"
start "SimpleMDM Frontend" cmd /c "npm run dev"
timeout /t 3 /nobreak >nul

echo.
echo ==============================================
echo   SimpleMDM 启动完成!
echo   前端: http://localhost:5173
echo   后端API文档: http://localhost:18001/docs
echo ==============================================
echo.
echo 演示账号:
echo   HR操作员: wangwu / 123456
echo   HR审批人: lisi   / 123456
echo   查 看 者: zhaoliu / 123456
echo.
pause
