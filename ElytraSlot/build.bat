@echo off
chcp 65001 >nul
echo ============================================
echo   ElytraSlot 鞘翅占位槽 — 一键构建脚本
echo ============================================
echo.

:: 检查 Java
java -version 2>nul
if %errorlevel% neq 0 (
    echo [错误] 没有找到 Java！请先安装 JDK 21+
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

echo [1/3] Java 已就绪

:: 复制 Gradle Wrapper（从 ModWhitelist）
if not exist "gradlew.bat" (
    echo [2/3] 复制 Gradle Wrapper...
    xcopy "D:\ModWhitelist\gradlew.bat" "." /Y >nul 2>&1
    xcopy "D:\ModWhitelist\gradlew" "." /Y >nul 2>&1
    if not exist "gradle\wrapper" mkdir "gradle\wrapper"
    xcopy "D:\ModWhitelist\gradle\wrapper\*" "gradle\wrapper\" /Y /E >nul 2>&1
    echo Gradle Wrapper 已复制
) else (
    echo [2/3] Gradle Wrapper 已就绪
)

echo [3/3] 开始构建（首次会下载依赖，需要几分钟）...
echo.
call gradlew build

if %errorlevel% neq 0 (
    echo.
    echo [失败] 构建出错！请检查上面的错误信息。
    pause
    exit /b 1
)

echo.
echo ============================================
echo   产物位置: build\libs\elytraslot-1.0.0.jar
echo ============================================
echo.
echo 使用方法:
echo   1. 把 jar 放进服务端的 mods 文件夹
echo   2. 把 jar 也放进客户端的 mods 文件夹
echo   3. 启动游戏，打开背包即可看到鞘翅槽位！
echo.
pause
