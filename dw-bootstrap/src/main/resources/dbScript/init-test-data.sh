#!/bin/bash
# 初始化测试数据的脚本
# 使用方法: ./init-test-data.sh

echo "正在插入测试数据到 MySQL 数据库..."

mysql -u root -p1234 dw_test < init-test-data.sql

if [ $? -eq 0 ]; then
    echo "✓ 测试数据插入成功！"
else
    echo "✗ 测试数据插入失败，请检查数据库连接和SQL脚本"
fi

