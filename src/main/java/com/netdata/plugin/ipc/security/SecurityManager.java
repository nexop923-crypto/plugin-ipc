package com.netdata.plugin.ipc.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Security utility for encrypting and securing the IPC plugin
 */
public class SecurityManager {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Generate a secure encryption key
     */
    public static SecretKey generateEncryptionKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        return keyGenerator.generateKey();
    }

    /**
     * Encrypt sensitive data
     */
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Decrypt sensitive data
     */
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedData = cipher.doFinal(decodedData);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    /**
     * Generate a secure hash for data integrity
     */
    public static String generateHash(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encodedhash);
    }

    /**
     * Verify data integrity using hash
     */
    public static boolean verifyHash(String data, String hash) throws Exception {
        String computedHash = generateHash(data);
        return computedHash.equals(hash);
    }

    /**
     * Generate a random API token
     */
    public static String generateAPIToken() {
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Validate API token format
     */
    public static boolean isValidAPIToken(String token) {
        return token != null && token.length() >= 32 && token.matches("[A-Za-z0-9_-]+");
    }

    /**
     * Create a secure key from password
     */
    public static SecretKey deriveKeyFromPassword(String password) throws Exception {
        byte[] decodedKey = MessageDigest.getInstance(HASH_ALGORITHM)
                .digest(password.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(decodedKey, 0, Math.min(decodedKey.length, 32), ALGORITHM);
    }
}
