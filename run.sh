#!/bin/bash

echo "正在编译项目..."
mvn clean package -q

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

echo "正在启动应用..."
java -jar dw-bootstrap/target/dw-bootstrap-1.0-SNAPSHOT.jar server dw-bootstrap/src/main/resources/config.yml

