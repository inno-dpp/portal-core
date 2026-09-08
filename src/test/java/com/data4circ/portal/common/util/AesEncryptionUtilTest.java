package com.data4circ.portal.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AesEncryptionUtil.
 */
class AesEncryptionUtilTest {

    // Test key - exactly 32 bytes for AES-256
    private static final String TEST_KEY = "ThisIsA32ByteKeyForTestingOnly!!";

    @Test
    @DisplayName("Should encrypt and decrypt a simple string")
    void encryptDecrypt_simpleString() {
        String plainText = "Hello, World!";

        String encrypted = AesEncryptionUtil.encrypt(plainText, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt a password-like string")
    void encryptDecrypt_password() {
        String password = "MyS3cr3tP@ssw0rd!";

        String encrypted = AesEncryptionUtil.encrypt(password, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertEquals(password, decrypted);
    }

    @Test
    @DisplayName("Should encrypt and decrypt Base64-encoded string")
    void encryptDecrypt_base64String() {
        // Simulating a Base64-encoded password like SPIP uses
        String base64Password = "U29tZUJhc2U2NFBhc3N3b3JkIQ==";

        String encrypted = AesEncryptionUtil.encrypt(base64Password, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertEquals(base64Password, decrypted);
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void encryptDecrypt_nullValues() {
        assertNull(AesEncryptionUtil.encrypt(null, TEST_KEY));
        assertNull(AesEncryptionUtil.decrypt(null, TEST_KEY));
    }

    @Test
    @DisplayName("Should handle empty strings")
    void encryptDecrypt_emptyString() {
        String empty = "";

        String encrypted = AesEncryptionUtil.encrypt(empty, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertEquals(empty, decrypted);
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void encryptDecrypt_unicodeCharacters() {
        String unicode = "Пароль123 日本語 émojis: 🔐🔑";

        String encrypted = AesEncryptionUtil.encrypt(unicode, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertEquals(unicode, decrypted);
    }

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext (due to random IV)")
    void encrypt_differentCiphertextEachTime() {
        String plainText = "Same text";

        String encrypted1 = AesEncryptionUtil.encrypt(plainText, TEST_KEY);
        String encrypted2 = AesEncryptionUtil.encrypt(plainText, TEST_KEY);

        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertext due to random IV");

        // But both should decrypt to the same value
        assertEquals(plainText, AesEncryptionUtil.decrypt(encrypted1, TEST_KEY));
        assertEquals(plainText, AesEncryptionUtil.decrypt(encrypted2, TEST_KEY));
    }

    @Test
    @DisplayName("Should throw exception for invalid key length")
    void encrypt_invalidKeyLength() {
        String shortKey = "TooShort";
        String longKey = "ThisKeyIsWayTooLongForAES256EncryptionAndWillFail!";

        assertThrows(IllegalArgumentException.class, () ->
            AesEncryptionUtil.encrypt("test", shortKey));

        assertThrows(IllegalArgumentException.class, () ->
            AesEncryptionUtil.encrypt("test", longKey));
    }

    @Test
    @DisplayName("Should throw exception for null key")
    void encrypt_nullKey() {
        assertThrows(IllegalArgumentException.class, () ->
            AesEncryptionUtil.encrypt("test", null));
    }

    @Test
    @DisplayName("Should fail decryption with wrong key")
    void decrypt_wrongKey() {
        String plainText = "Secret message";
        String correctKey = "CorrectKey12345678901234567890ab";
        String wrongKey = "WrongKey123456789012345678901234";

        String encrypted = AesEncryptionUtil.encrypt(plainText, correctKey);

        assertThrows(RuntimeException.class, () ->
            AesEncryptionUtil.decrypt(encrypted, wrongKey));
    }

    @Test
    @DisplayName("Should correctly identify encrypted values")
    void isEncrypted_validCiphertext() {
        String plainText = "test";
        String encrypted = AesEncryptionUtil.encrypt(plainText, TEST_KEY);

        assertTrue(AesEncryptionUtil.isEncrypted(encrypted));
        assertFalse(AesEncryptionUtil.isEncrypted(plainText));
        assertFalse(AesEncryptionUtil.isEncrypted(null));
        assertFalse(AesEncryptionUtil.isEncrypted(""));
    }

    @Test
    @DisplayName("Should handle long strings")
    void encryptDecrypt_longString() {
        String longText = "A".repeat(10000);

        String encrypted = AesEncryptionUtil.encrypt(longText, TEST_KEY);
        String decrypted = AesEncryptionUtil.decrypt(encrypted, TEST_KEY);

        assertEquals(longText, decrypted);
    }
}
