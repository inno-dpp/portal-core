package com.data4circ.portal.common.util;

import java.security.SecureRandom;

/**
 * Single source of the platform's generated-credential recipe (SPIP/Keycloak/portal
 * temporary passwords): at least one uppercase, one lowercase and one numeral, plus
 * either exactly one or at least one character from the given specials set, shuffled
 * so the required characters are not at predictable positions.
 */
public final class PasswordGenerator {

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMERALS = "0123456789";

    private PasswordGenerator() {
    }

    /**
     * @param length            total password length (minimum 4)
     * @param specials          allowed special characters (must not be empty)
     * @param exactlyOneSpecial when true the password contains exactly one special
     *                          character (e.g. the SPIP password rule); when false at
     *                          least one, with specials allowed anywhere
     */
    public static String generate(int length, String specials, boolean exactlyOneSpecial) {
        if (length < 4) {
            throw new IllegalArgumentException("Password length must be at least 4");
        }
        SecureRandom random = new SecureRandom();

        String fillCharacters = exactlyOneSpecial
                ? UPPERCASE + LOWERCASE + NUMERALS
                : UPPERCASE + LOWERCASE + NUMERALS + specials;

        StringBuilder password = new StringBuilder(length);
        password.append(specials.charAt(random.nextInt(specials.length())));
        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(NUMERALS.charAt(random.nextInt(NUMERALS.length())));

        for (int i = 4; i < length; i++) {
            password.append(fillCharacters.charAt(random.nextInt(fillCharacters.length())));
        }

        // Shuffle so the required characters are not at predictable positions
        char[] characters = password.toString().toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = characters[i];
            characters[i] = characters[j];
            characters[j] = swap;
        }

        return new String(characters);
    }
}
