package com.data4circ.portal.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EncryptionConfigTest {

    @Test
    void validateKeyAccepts32ByteKey() {
        assertDoesNotThrow(() -> EncryptionConfig.validateKey("A".repeat(32)));
    }

    @Test
    void validateKeyAcceptsBuiltInDevKey() {
        assertDoesNotThrow(() -> EncryptionConfig.validateKey(EncryptionConfig.DEV_DEFAULT_KEY));
    }

    @Test
    void validateKeyRejectsShortKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EncryptionConfig.validateKey("A".repeat(31)));
        assertTrue(ex.getMessage().contains("31 bytes"));
    }

    @Test
    void validateKeyRejectsLongKey() {
        assertThrows(IllegalStateException.class,
                () -> EncryptionConfig.validateKey("A".repeat(33)));
    }

    @Test
    void validateKeyCountsBytesNotCharacters() {
        // 32 characters but more than 32 bytes in UTF-8 must be rejected
        String multiByte = "ä".repeat(32);
        assertThrows(IllegalStateException.class,
                () -> EncryptionConfig.validateKey(multiByte));
    }
}
