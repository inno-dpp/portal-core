package com.data4circ.portal.common.converter;

import com.data4circ.portal.common.util.AesEncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA AttributeConverter that automatically encrypts/decrypts String fields.
 *
 * <p>Usage: Add {@code @Convert(converter = EncryptedStringConverter.class)} to entity fields
 * that should be encrypted at rest.</p>
 *
 * <p>Configuration: Set the {@code ENCRYPTION_KEY} environment variable (or system property) to a
 * 32-character secret key. The application will fail fast if this is not configured.</p>
 *
 * <p>Example:</p>
 * <pre>
 * &#64;Convert(converter = EncryptedStringConverter.class)
 * &#64;Column(name = "spip_password")
 * private String spipPassword;
 * </pre>
 *
 * <p>Migration: Existing unencrypted data will be automatically encrypted on first update.
 * The converter detects if data is already encrypted to prevent double-encryption.</p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private static final String ENCRYPTION_KEY_ENV = "ENCRYPTION_KEY";

    /**
     * Gets the encryption key from environment variable or system property.
     * Fails fast if neither is set to prevent silent data corruption.
     *
     * @throws IllegalStateException if ENCRYPTION_KEY is not configured
     */
    private String getEncryptionKey() {
        String key = System.getenv(ENCRYPTION_KEY_ENV);
        if (key == null || key.isEmpty()) {
            // Check system property as fallback (useful for testing)
            key = System.getProperty(ENCRYPTION_KEY_ENV);
        }
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY environment variable (or system property) is not set. " +
                    "The application cannot safely encrypt or decrypt sensitive data without it. " +
                    "Set a 32-character key via ENCRYPTION_KEY environment variable or " +
                    "-DENCRYPTION_KEY=<key> system property.");
        }
        return key;
    }

    /**
     * Encrypts the attribute value before storing in the database.
     *
     * @param attribute The plaintext value from the entity
     * @return The encrypted value to store in the database
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        try {
            String key = getEncryptionKey();

            // Check if already encrypted (to handle migration gracefully)
            if (isAlreadyEncrypted(attribute, key)) {
                log.debug("Value appears to already be encrypted, skipping encryption");
                return attribute;
            }

            String encrypted = AesEncryptionUtil.encrypt(attribute, key);
            log.debug("Successfully encrypted value for database storage");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt value: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the database value when loading into the entity.
     *
     * @param dbData The encrypted value from the database
     * @return The decrypted plaintext value
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        try {
            String key = getEncryptionKey();

            // Check if the data is actually encrypted
            if (!AesEncryptionUtil.isEncrypted(dbData)) {
                log.warn("Database value does not appear to be encrypted. " +
                        "This may be legacy data that needs migration.");
                return dbData; // Return as-is for legacy unencrypted data
            }

            String decrypted = AesEncryptionUtil.decrypt(dbData, key);
            log.debug("Successfully decrypted value from database");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt value: {}. Returning raw value.", e.getMessage());
            // Return raw value to allow reading legacy unencrypted data
            return dbData;
        }
    }

    /**
     * Checks if a value is already encrypted.
     * Attempts to decrypt and re-encrypt to see if it's valid ciphertext.
     */
    private boolean isAlreadyEncrypted(String value, String key) {
        if (!AesEncryptionUtil.isEncrypted(value)) {
            return false;
        }
        try {
            // Try to decrypt - if it works, it was encrypted
            AesEncryptionUtil.decrypt(value, key);
            return true;
        } catch (Exception e) {
            // Decryption failed - not encrypted with our key
            return false;
        }
    }
}
