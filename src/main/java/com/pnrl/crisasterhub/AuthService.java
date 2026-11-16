package com.pnrl.crisasterhub;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthService {

    public static class UserSession {
        public int userId;
        public String role;

        public UserSession(int userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }

    public static UserSession login(String identifier, String password, String requestedRole) {
        String selectQuery = "SELECT user_id, role, password FROM user " +
                "WHERE email = ? OR username = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            selectStmt.setString(1, identifier);
            selectStmt.setString(2, identifier);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                String dbPassword = rs.getString("password");

                if (dbPassword == null || !dbPassword.equals(password)) {
                    System.out.println("Wrong password");
                    return null;
                }

                String storedRole = rs.getString("role");
                String selectedRole = normalizeRole(requestedRole);
                if (!hasRole(storedRole, selectedRole)) {
                    return null;
                }

                return new UserSession(rs.getInt("user_id"), selectedRole);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public static UserSession registerAccount(String fullName, String username,
                                              String email, String password) {
        String insertQuery = "INSERT INTO user " +
                "(full_name, username, email, password, role) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {

            insertStmt.setString(1, fullName);
            insertStmt.setString(2, username);
            insertStmt.setString(3, email);
            insertStmt.setString(4, password);
            insertStmt.setString(5, "victim,volunteer");

            insertStmt.executeUpdate();

            try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new UserSession(generatedKeys.getInt(1), "victim");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase();
    }

    private static boolean hasRole(String storedRoles, String requestedRole) {
        if (storedRoles == null || requestedRole.isEmpty()) {
            return false;
        }

        for (String role : storedRoles.split(",")) {
            if (normalizeRole(role).equals(requestedRole)) {
                return true;
            }
        }
        return false;
    }
}
