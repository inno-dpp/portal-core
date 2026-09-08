package com.data4circ.portal.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordGeneratorTest {

    private static final String SPECIALS = "!@#$%^&*";

    private long countSpecials(String password) {
        return password.chars().filter(c -> SPECIALS.indexOf(c) >= 0).count();
    }

    @Test
    void containsAllRequiredCategories() {
        for (int i = 0; i < 50; i++) {
            String password = PasswordGenerator.generate(16, SPECIALS, false);
            assertThat(password).hasSize(16)
                    .matches(".*[A-Z].*")
                    .matches(".*[a-z].*")
                    .matches(".*[0-9].*");
            assertThat(countSpecials(password)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void exactlyOneSpecialWhenRequested() {
        for (int i = 0; i < 50; i++) {
            String password = PasswordGenerator.generate(16, SPECIALS, true);
            assertThat(countSpecials(password)).isEqualTo(1);
        }
    }

    @Test
    void supportsExtendedSpecialsSet() {
        String extended = "!@#$%^&*_+-=?";
        String password = PasswordGenerator.generate(12, extended, false);
        assertThat(password).hasSize(12);
        assertThat(password.chars().filter(c -> extended.indexOf(c) >= 0).count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsTooShortLength() {
        assertThatThrownBy(() -> PasswordGenerator.generate(3, SPECIALS, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
