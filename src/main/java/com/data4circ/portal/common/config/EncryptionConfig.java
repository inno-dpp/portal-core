package com.data4circ.portal.common.config;

import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Bridges Spring's property system with the JPA AttributeConverter for encryption,
 * and validates the encryption key at startup.
 *
 * <p>The {@code EncryptedStringConverter} is instantiated by JPA (not Spring) and cannot
 * use {@code @Value} injection. It reads the encryption key from {@code System.getenv()}
 * or {@code System.getProperty()}. This config reads {@code app.encryption.key} from
 * application.yml and publishes it as a system property so the converter can find it.</p>
 *
 * <p>Precedence (highest wins):
 * <ol>
 *   <li>{@code ENCRYPTION_KEY} environment variable (production/.env)</li>
 *   <li>{@code -DENCRYPTION_KEY=...} JVM system property</li>
 *   <li>{@code app.encryption.key} in application.yml (dev/test profiles only)</li>
 * </ol>
 *
 * <p>Startup fails if no key is configured or the key is not exactly 32 bytes, so a
 * misconfigured deployment cannot boot and later corrupt or expose encrypted data.
 * The dev and test profiles ship a built-in key, so local development needs no setup.</p>
 */
@Configuration
public class EncryptionConfig {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConfig.class);

    private static final String ENCRYPTION_KEY_PROPERTY = "ENCRYPTION_KEY";
    private static final int REQUIRED_KEY_BYTES = 32;

    /** Built-in dev-profile key; public knowledge, must never protect real data. */
    static final String DEV_DEFAULT_KEY = "Dev_Encryption_Key_D4C_Portal_32";

    private final Environment environment;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    public EncryptionConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        String envKey = System.getenv(ENCRYPTION_KEY_PROPERTY);
        String sysPropKey = System.getProperty(ENCRYPTION_KEY_PROPERTY);

        String key;
        if (envKey != null && !envKey.isEmpty()) {
            key = envKey;
            log.info("ENCRYPTION_KEY loaded from environment variable");
        } else if (sysPropKey != null && !sysPropKey.isEmpty()) {
            key = sysPropKey;
            log.info("ENCRYPTION_KEY loaded from system property (-D flag)");
        } else if (encryptionKey != null && !encryptionKey.isEmpty()) {
            key = encryptionKey;
            System.setProperty(ENCRYPTION_KEY_PROPERTY, encryptionKey);
            log.info("ENCRYPTION_KEY loaded from application.yml (app.encryption.key)");
        } else {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is not configured. Set the ENCRYPTION_KEY environment variable "
                            + "(or -DENCRYPTION_KEY=<key>) to a 32-character secret key. "
                            + "The dev and test profiles include a built-in key for local development.");
        }

        validateKey(key);

        if (DEV_DEFAULT_KEY.equals(key) && environment.acceptsProfiles(Profiles.of("prod"))) {
            log.error("The built-in development ENCRYPTION_KEY is active together with the prod "
                    + "profile. This key is public knowledge, so encrypted data is NOT protected. "
                    + "Set a unique 32-character ENCRYPTION_KEY immediately.");
        }
    }

    /**
     * Validates that the key is exactly 32 bytes (256 bits) as required for AES-256.
     *
     * @throws IllegalStateException if the key has any other length
     */
    static void validateKey(String key) {
        int length = key.getBytes(StandardCharsets.UTF_8).length;
        if (length != REQUIRED_KEY_BYTES) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY must be exactly " + REQUIRED_KEY_BYTES
                            + " bytes for AES-256, but the configured key is " + length + " bytes.");
        }
    }
}
