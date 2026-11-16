package com.pnrl.crisasterhub;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    public static Connection getConnection() throws SQLException {
        String url = requiredConfiguration("DB_URL");
        String user = requiredConfiguration("DB_USERNAME");
        String password = requiredConfiguration("DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static String requiredConfiguration(String name) throws SQLException {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        if (value == null || value.isBlank()) {
            throw new SQLException("Missing required database configuration: " + name);
        }
        return value;
    }
}
