@echo off
chcp 65001 >nul
echo 正在编译项目...
call mvn clean package -q

if %errorlevel% neq 0 (
    echo 编译失败！
    exit /b %errorlevel%
)

echo 正在启动应用...
java -Dfile.encoding=UTF-8 ^
     -Dsun.jnu.encoding=UTF-8 ^
     -jar dw-bootstrap/target/dw-bootstrap-1.0-SNAPSHOT.jar server dw-bootstrap/src/main/resources/config.yml

