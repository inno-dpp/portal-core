package com.data4circ.portal.features.organization.service;

import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrganizationSpipUserService.
 *
 * Focus on testing the username generation logic that satisfies BOTH:
 * - S3 bucket naming rules: lowercase letters, numbers, hyphens
 * - SPIP username rules: letters, numbers, periods, underscores
 *
 * Intersection (compatible with both): lowercase letters and numbers only
 *
 * Final rules:
 * - Only lowercase letters and numbers (no separators)
 * - 3-30 characters length
 * - Defaults to "org" if empty after processing
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationSpipUserServiceTest {

    @Mock
    private OrganizationSpipUserRepository spipUserRepository;

    @Mock
    private OnboardingRequestRepository onboardingRequestRepository;

    @InjectMocks
    private OrganizationSpipUserService service;

    @BeforeEach
    void setUp() {
        // By default, no usernames exist
        when(spipUserRepository.existsBySpipUser(anyString())).thenReturn(false);
    }

    @Nested
    @DisplayName("Basic Transformations")
    class BasicTransformations {

        @Test
        @DisplayName("Should remove spaces and concatenate")
        void shouldRemoveSpaces() {
            String result = service.generateUsernameFromOrganizationName("Green Tech Solutions");
            assertThat(result).isEqualTo("greentechsolutions");
        }

        @Test
        @DisplayName("Should convert to lowercase")
        void shouldConvertToLowercase() {
            String result = service.generateUsernameFromOrganizationName("ACME Corporation");
            assertThat(result).isEqualTo("acmecorporation");
        }

        @Test
        @DisplayName("Should remove underscores")
        void shouldRemoveUnderscores() {
            String result = service.generateUsernameFromOrganizationName("my_company_name");
            assertThat(result).isEqualTo("mycompanyname");
        }

        @Test
        @DisplayName("Should remove dots")
        void shouldRemoveDots() {
            String result = service.generateUsernameFromOrganizationName("company.name.inc");
            assertThat(result).isEqualTo("companynameinc");
        }

        @Test
        @DisplayName("Should remove hyphens")
        void shouldRemoveHyphens() {
            String result = service.generateUsernameFromOrganizationName("my-company-name");
            assertThat(result).isEqualTo("mycompanyname");
        }

        @Test
        @DisplayName("Should handle mixed separators")
        void shouldHandleMixedSeparators() {
            String result = service.generateUsernameFromOrganizationName("my_company.name-here test");
            assertThat(result).isEqualTo("mycompanynameheretest");
        }
    }

    @Nested
    @DisplayName("Special Character Handling")
    class SpecialCharacterHandling {

        @Test
        @DisplayName("Should remove special characters")
        void shouldRemoveSpecialCharacters() {
            String result = service.generateUsernameFromOrganizationName("ABC & XYZ Co.");
            assertThat(result).isEqualTo("abcxyzco");
        }

        @Test
        @DisplayName("Should remove parentheses and brackets")
        void shouldRemoveParenthesesAndBrackets() {
            String result = service.generateUsernameFromOrganizationName("Company (Ltd) [UK]");
            assertThat(result).isEqualTo("companyltduk");
        }

        @Test
        @DisplayName("Should remove quotes and apostrophes")
        void shouldRemoveQuotesAndApostrophes() {
            String result = service.generateUsernameFromOrganizationName("O'Brien's \"Best\" Company");
            assertThat(result).isEqualTo("obriensbestcompany");
        }

        @Test
        @DisplayName("Should handle all special characters resulting in empty string")
        void shouldHandleAllSpecialCharacters() {
            String result = service.generateUsernameFromOrganizationName("!@#$%^&*()");
            assertThat(result).isEqualTo("org");
        }

        @ParameterizedTest
        @DisplayName("Should remove various special characters")
        @CsvSource({
            "'Company+Plus', 'companyplus'",
            "'Company=Equals', 'companyequals'",
            "'Company/Slash', 'companyslash'",
            "'Company\\Back', 'companyback'",
            "'Company:Colon', 'companycolon'",
            "'Company;Semi', 'companysemi'"
        })
        void shouldRemoveVariousSpecialCharacters(String input, String expected) {
            String result = service.generateUsernameFromOrganizationName(input);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Length Constraints")
    class LengthConstraints {

        @Test
        @DisplayName("Should pad short names to minimum 3 characters")
        void shouldPadShortNames() {
            String result = service.generateUsernameFromOrganizationName("AB");
            assertThat(result).isEqualTo("ab0");
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("Should pad single character names")
        void shouldPadSingleCharacter() {
            String result = service.generateUsernameFromOrganizationName("X");
            assertThat(result).isEqualTo("x00");
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("Should not pad names with exactly 3 characters")
        void shouldNotPadThreeCharNames() {
            String result = service.generateUsernameFromOrganizationName("ABC");
            assertThat(result).isEqualTo("abc");
        }

        @Test
        @DisplayName("Should truncate names longer than 30 characters")
        void shouldTruncateLongNames() {
            String longName = "This Is A Very Long Organization Name That Exceeds Limit";
            String result = service.generateUsernameFromOrganizationName(longName);
            assertThat(result.length()).isLessThanOrEqualTo(30);
        }

        @Test
        @DisplayName("Should handle exactly 30 character result")
        void shouldHandleExactly30CharResult() {
            // Input that produces exactly 30 chars after processing
            String name = "abcdefghijklmnopqrstuvwxyz1234"; // 30 chars, all valid
            String result = service.generateUsernameFromOrganizationName(name);
            assertThat(result).isEqualTo("abcdefghijklmnopqrstuvwxyz1234");
            assertThat(result).hasSize(30);
        }

        @Test
        @DisplayName("Should truncate result exceeding 30 characters")
        void shouldTruncateResultExceeding30Chars() {
            String name = "abcdefghijklmnopqrstuvwxyz12345678"; // 34 chars
            String result = service.generateUsernameFromOrganizationName(name);
            assertThat(result).isEqualTo("abcdefghijklmnopqrstuvwxyz1234");
            assertThat(result).hasSize(30);
        }
    }

    @Nested
    @DisplayName("Uniqueness Handling")
    class UniquenessHandling {

        @Test
        @DisplayName("Should append counter when username exists")
        void shouldAppendCounterWhenUsernameExists() {
            when(spipUserRepository.existsBySpipUser("greentech")).thenReturn(true);
            when(spipUserRepository.existsBySpipUser("greentech1")).thenReturn(false);

            String result = service.generateUsernameFromOrganizationName("GreenTech");
            assertThat(result).isEqualTo("greentech1");
        }

        @Test
        @DisplayName("Should increment counter until unique")
        void shouldIncrementCounterUntilUnique() {
            when(spipUserRepository.existsBySpipUser("company")).thenReturn(true);
            when(spipUserRepository.existsBySpipUser("company1")).thenReturn(true);
            when(spipUserRepository.existsBySpipUser("company2")).thenReturn(true);
            when(spipUserRepository.existsBySpipUser("company3")).thenReturn(false);

            String result = service.generateUsernameFromOrganizationName("Company");
            assertThat(result).isEqualTo("company3");
        }

        @Test
        @DisplayName("Should truncate base name to fit counter suffix")
        void shouldTruncateBaseNameToFitCounter() {
            String longName = "abcdefghijklmnopqrstuvwxyz1234"; // 30 chars

            when(spipUserRepository.existsBySpipUser("abcdefghijklmnopqrstuvwxyz1234")).thenReturn(true);
            when(spipUserRepository.existsBySpipUser("abcdefghijklmnopqrstuvwxyz12341")).thenReturn(false);

            String result = service.generateUsernameFromOrganizationName(longName);
            assertThat(result.length()).isLessThanOrEqualTo(30);
            // Should end with counter
            assertThat(result).endsWith("1");
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Should throw exception for null input")
        void shouldThrowExceptionForNullInput() {
            assertThatThrownBy(() -> service.generateUsernameFromOrganizationName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for empty input")
        void shouldThrowExceptionForEmptyInput() {
            assertThatThrownBy(() -> service.generateUsernameFromOrganizationName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for whitespace-only input")
        void shouldThrowExceptionForWhitespaceOnlyInput() {
            assertThatThrownBy(() -> service.generateUsernameFromOrganizationName("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("S3 and SPIP Naming Compliance")
    class NamingCompliance {

        @ParameterizedTest
        @DisplayName("Generated username should be compatible with both S3 and SPIP")
        @ValueSource(strings = {
            "Green Tech Solutions",
            "ACME Corporation",
            "O'Brien's Company",
            "Company (Ltd)",
            "XN--International",
            "192.168.1.1",
            "A",
            "Very Long Organization Name That Should Be Truncated Properly",
            "Special!@#$%Characters",
            "--Leading-Trailing--",
            "Mixed.Separators_And Spaces",
            "Company-With-Hyphens",
            "company_with_underscores",
            "company.with.dots"
        })
        void generatedUsernameShouldBeCompatible(String input) {
            String result = service.generateUsernameFromOrganizationName(input);

            // Must only contain lowercase letters and numbers (intersection of S3 and SPIP rules)
            assertThat(result).matches("^[a-z0-9]+$");

            // Must be between 3 and 30 characters
            assertThat(result.length()).isBetween(3, 30);
        }

        @Test
        @DisplayName("Should not contain hyphens (not in SPIP allowed chars)")
        void shouldNotContainHyphens() {
            String result = service.generateUsernameFromOrganizationName("Company-Name");
            assertThat(result).doesNotContain("-");
        }

        @Test
        @DisplayName("Should not contain underscores (not in S3 allowed chars)")
        void shouldNotContainUnderscores() {
            String result = service.generateUsernameFromOrganizationName("Company_Name");
            assertThat(result).doesNotContain("_");
        }

        @Test
        @DisplayName("Should not contain dots (not in S3 allowed chars)")
        void shouldNotContainDots() {
            String result = service.generateUsernameFromOrganizationName("Company.Name");
            assertThat(result).doesNotContain(".");
        }
    }

    @Nested
    @DisplayName("Real-World Examples")
    class RealWorldExamples {

        @ParameterizedTest
        @DisplayName("Should correctly transform real company names")
        @CsvSource({
            "'GreenTech Solutions', 'greentechsolutions'",
            "'EcoRecycle Corp', 'ecorecyclecorp'",
            "'BMW Group', 'bmwgroup'",
            "'L''Oréal Paris', 'loralparis'",
            "'Johnson & Johnson', 'johnsonjohnson'",
            "'3M Company', '3mcompany'",
            "'AT&T Inc.', 'attinc'",
            "'Ernst & Young (EY)', 'ernstyoungey'"
        })
        void shouldTransformRealCompanyNames(String input, String expected) {
            String result = service.generateUsernameFromOrganizationName(input);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should truncate long names correctly")
        void shouldTruncateLongNames() {
            // "pricewaterhousecoopersllp" is 25 chars - should fit
            String result = service.generateUsernameFromOrganizationName("PricewaterhouseCoopers LLP");
            assertThat(result).isEqualTo("pricewaterhousecoopersllp");
            assertThat(result.length()).isLessThanOrEqualTo(30);
        }

        @Test
        @DisplayName("Should handle Agricultural Plastics Institute")
        void shouldHandleAgriculturalPlasticsInstitute() {
            // "agriculturalplasticsinstitute" is 29 chars - should fit
            String result = service.generateUsernameFromOrganizationName("Agricultural Plastics Institute");
            assertThat(result).isEqualTo("agriculturalplasticsinstitute");
            assertThat(result.length()).isLessThanOrEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle names with only numbers")
        void shouldHandleNamesWithOnlyNumbers() {
            String result = service.generateUsernameFromOrganizationName("12345");
            assertThat(result).isEqualTo("12345");
        }

        @Test
        @DisplayName("Should handle Unicode characters")
        void shouldHandleUnicodeCharacters() {
            String result = service.generateUsernameFromOrganizationName("Société Générale");
            // Unicode characters are removed
            assertThat(result).matches("^[a-z0-9]+$");
            assertThat(result.length()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Should handle Chinese characters")
        void shouldHandleChineseCharacters() {
            String result = service.generateUsernameFromOrganizationName("中国公司");
            // All characters removed, defaults to "org"
            assertThat(result).isEqualTo("org");
        }

        @Test
        @DisplayName("Should handle mixed ASCII and Unicode")
        void shouldHandleMixedAsciiAndUnicode() {
            String result = service.generateUsernameFromOrganizationName("ABC中国公司XYZ");
            assertThat(result).isEqualTo("abcxyz");
        }

        @Test
        @DisplayName("Should handle multiple consecutive spaces")
        void shouldHandleMultipleConsecutiveSpaces() {
            String result = service.generateUsernameFromOrganizationName("Company    Name");
            assertThat(result).isEqualTo("companyname");
        }

        @Test
        @DisplayName("Should handle tabs and newlines")
        void shouldHandleTabsAndNewlines() {
            String result = service.generateUsernameFromOrganizationName("Company\tName\nInc");
            assertThat(result).isEqualTo("companynameinc");
        }

        @Test
        @DisplayName("Should handle IP address-like input")
        void shouldHandleIpAddressLikeInput() {
            // Dots are removed, so "192.168.1.1" becomes "19216811"
            String result = service.generateUsernameFromOrganizationName("192.168.1.1");
            assertThat(result).isEqualTo("19216811");
        }

        @Test
        @DisplayName("Should handle xn-- prefix")
        void shouldHandleXnPrefix() {
            // Hyphens are removed, so "xn--company" becomes "xncompany"
            String result = service.generateUsernameFromOrganizationName("xn--company");
            assertThat(result).isEqualTo("xncompany");
        }
    }
}
