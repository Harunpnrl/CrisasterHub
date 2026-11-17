package com.pnrl.crisasterhub;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthService {

    private static volatile UserSession activeSession;

    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int HASH_ITERATIONS = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    public static class UserSession {
        public int userId;
        public String role;

        public UserSession(int userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }

    public static UserSession login(String identifier, String password, String requestedRole) {
        activeSession = null;
        String selectQuery = "SELECT user_id, role, password FROM user " +
                "WHERE email = ? OR username = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {

            selectStmt.setString(1, identifier);
            selectStmt.setString(2, identifier);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                String dbPassword = rs.getString("password");

                if (!verifyPassword(password, dbPassword)) {
                    System.out.println("Wrong password");
                    return null;
                }

                // Eski düz metin kayıtları başarılı girişten sonra güvenli formata geçir.
                if (dbPassword != null && !dbPassword.startsWith(HASH_PREFIX + "$")) {
                    upgradePasswordHash(conn, rs.getInt("user_id"), password);
                }

                String storedRole = rs.getString("role");
                String selectedRole = normalizeRole(requestedRole);
                if (!hasRole(storedRole, selectedRole)) {
                    return null;
                }

                UserSession session = new UserSession(rs.getInt("user_id"), selectedRole);
                activeSession = session;
                return session;
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
            insertStmt.setString(4, hashPassword(password));
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

    private static String hashPassword(String password) throws SQLException {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        try {
            byte[] hash = deriveKey(password, salt, HASH_ITERATIONS);
            return HASH_PREFIX + "$" + HASH_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new SQLException("Could not hash password", e);
        }
    }

    private static boolean verifyPassword(String password, String storedValue) {
        if (password == null || storedValue == null) {
            return false;
        }

        if (!storedValue.startsWith(HASH_PREFIX + "$")) {
            // Geçiş dönemi: eski düz metin kayıtlarını destekle.
            return MessageDigest.isEqual(
                    password.getBytes(StandardCharsets.UTF_8),
                    storedValue.getBytes(StandardCharsets.UTF_8));
        }

        try {
            String[] parts = storedValue.split("\\$", -1);
            if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = deriveKey(password, salt, iterations);
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] deriveKey(String password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_LENGTH * 8);
        try {
            return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void upgradePasswordHash(Connection conn, int userId, String password)
            throws SQLException {
        String updateQuery = "UPDATE user SET password = ? WHERE user_id = ?";
        try (PreparedStatement statement = conn.prepareStatement(updateQuery)) {
            statement.setString(1, hashPassword(password));
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
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

    public static UserSession getActiveSession() {
        return activeSession;
    }

    public static boolean hasActiveRole(String role) {
        return activeSession != null && normalizeRole(role).equals(normalizeRole(activeSession.role));
    }

    public static void clearSession() {
        activeSession = null;
    }
}
