@echo off
chcp 65001 >nul
cd /d "%~dp0"
title World Lyrics - сборка мода

where java >nul 2>nul
if errorlevel 1 (
    echo Java не найдена. Ставлю JDK 17 через winget...
    winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo.
        echo Не получилось. Установите JDK 17 вручную: https://adoptium.net/temurin/releases/?version=17
        pause
        exit /b 1
    )
    echo.
    echo Java установлена. Закройте это окно и запустите build.bat ещё раз.
    pause
    exit /b 0
)

echo Сборка World Lyrics. Первый раз Gradle скачает Minecraft и Forge (5-15 минут), дальше быстро.
echo.
call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo.
    echo Ошибка сборки. Пришлите текст выше — разберёмся.
    pause
    exit /b 1
)
copy /y build\libs\worldlyrics-*.jar . >nul
echo.
echo ============================================================
echo  Готово! Файл worldlyrics-1.20.1-1.0.0.jar лежит рядом.
echo  Положите его в папку .minecraft\mods (Forge 1.20.1).
echo ============================================================
pause
