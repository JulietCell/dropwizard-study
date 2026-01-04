package com.gutou;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestMySQLConnection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/dw_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true&allowPublicKeyRetrieval=true";
        String user = "root";
        String password = "1234";
        
        System.out.println("正在测试 MySQL 连接...");
        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        System.out.println("Password: " + (password.isEmpty() ? "(空)" : "***"));
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            if (conn != null) {
                System.out.println("✓ 连接成功！");
                System.out.println("数据库产品: " + conn.getMetaData().getDatabaseProductName());
                System.out.println("数据库版本: " + conn.getMetaData().getDatabaseProductVersion());
            }
        } catch (SQLException e) {
            System.err.println("✗ 连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}



