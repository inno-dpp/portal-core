package com.data4circ.portal.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utility class for AES-GCM encryption and decryption.
 *
 * <p>Uses AES-256-GCM which provides both confidentiality and authenticity.
 * The Initialization Vector (IV) is prepended to the ciphertext for storage.</p>
 *
 * <p>Key requirements:</p>
 * <ul>
 *   <li>Key must be exactly 32 bytes (256 bits) for AES-256</li>
 *   <li>Key should be stored securely (environment variable, vault, etc.)</li>
 * </ul>
 */
public class AesEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits - recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits - authentication tag
    private static final int AES_KEY_LENGTH = 32;  // 256 bits for AES-256

    private AesEncryptionUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plainText The text to encrypt
     * @param secretKey The encryption key (must be 32 characters/bytes)
     * @return Base64-encoded ciphertext with IV prepended, or null if input is null
     * @throws IllegalArgumentException if secretKey is invalid
     * @throws RuntimeException if encryption fails
     */
    public static String encrypt(String plainText, String secretKey) {
        if (plainText == null) {
            return null;
        }

        validateKey(secretKey);

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Create cipher
            SecretKeySpec keySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext for storage
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts AES-256-GCM encrypted ciphertext.
     *
     * @param encryptedText Base64-encoded ciphertext with IV prepended
     * @param secretKey The encryption key (must be 32 characters/bytes)
     * @return The decrypted plaintext, or null if input is null
     * @throws IllegalArgumentException if secretKey is invalid
     * @throws RuntimeException if decryption fails
     */
    public static String decrypt(String encryptedText, String secretKey) {
        if (encryptedText == null) {
            return null;
        }

        validateKey(secretKey);

        try {
            // Decode from Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            // Create cipher
            SecretKeySpec keySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Validates that the encryption key meets requirements.
     *
     * @param secretKey The key to validate
     * @throws IllegalArgumentException if key is null or wrong length
     */
    private static void validateKey(String secretKey) {
        if (secretKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "Encryption key must be exactly " + AES_KEY_LENGTH + " bytes (256 bits). " +
                "Current length: " + secretKey.getBytes(StandardCharsets.UTF_8).length + " bytes");
        }
    }

    /**
     * Checks if a string appears to be encrypted (Base64 encoded with minimum length).
     * This is a heuristic check, not a guarantee.
     *
     * @param value The value to check
     * @return true if the value appears to be encrypted
     */
    public static boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // Minimum length: IV (12) + at least some ciphertext + tag (16)
            return decoded.length > GCM_IV_LENGTH + 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
