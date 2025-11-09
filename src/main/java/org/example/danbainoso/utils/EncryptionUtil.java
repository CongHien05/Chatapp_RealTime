package org.example.danbainoso.utils;

import org.mindrot.jbcrypt.BCrypt;

public class EncryptionUtil {
    private static final int ROUNDS = 12;
    
    /**
     * Hash a password using BCrypt
     * @param password Plain text password
     * @return Hashed password
     */
    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(ROUNDS));
    }
    
    /**
     * Verify a password against a hash
     * @param password Plain text password
     * @param hash Hashed password
     * @return true if password matches hash
     */
    public static boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Generate a random token
     * @param length Token length
     * @return Random token string
     */
    public static String generateToken(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * chars.length());
            token.append(chars.charAt(index));
        }
        return token.toString();
    }
}
