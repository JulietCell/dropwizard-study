-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS dw_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE dw_test;

-- 注意：如果表已存在且使用自增ID，下面的ID可能不需要指定
-- 插入测试用户数据
INSERT INTO users (username, email, full_name, created_at, updated_at) VALUES
('alice', 'alice@example.com', 'Alice Zhang', NOW(), NOW()),
('bob', 'bob@example.com', 'Bob Li', NOW(), NOW()),
('charlie', 'charlie@example.com', 'Charlie Wang', NOW(), NOW()),
('diana', 'diana@example.com', 'Diana Liu', NOW(), NOW()),
('eve', 'eve@example.com', 'Eve Chen', NOW(), NOW());

-- 查看插入的数据
SELECT * FROM users;

