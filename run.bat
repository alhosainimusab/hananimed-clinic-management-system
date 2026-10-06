@echo off
REM Compile and run without Maven (needs JDK 17+). Usage: run.bat [data-dir]
cd /d "%~dp0"
if exist out rmdir /s /q out
set SRC=src\main\java\com\hananimed
javac --release 17 -encoding UTF-8 -d out %SRC%\model\*.java %SRC%\persistence\*.java %SRC%\service\*.java %SRC%\ui\*.java || exit /b 1
chcp 65001 > nul
if "%~1"=="" (java -cp out com.hananimed.ui.ClinicSystem data) else (java -cp out com.hananimed.ui.ClinicSystem "%~1")
