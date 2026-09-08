package com.data4circ.portal.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingPageTemplateTest {

    @Test
    void guidanceAppearsBeforeTheFormAndKeepsTheDeploymentBrandName() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/templates/public/join.html")) {
            assertThat(input).isNotNull();

            String page = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(page)
                    .contains("th:text=\"'Join ' + ${branding.brandName}\"")
                    .contains("<form id=\"joinForm\" aria-labelledby=\"application-form-heading\">")
                    .contains("<h2 id=\"application-form-heading\" class=\"visually-hidden\">Portal access application</h2>")
                    .doesNotContain("Integrate data catalog functionality with SPIP capabilities");

            int overviewStart = page.indexOf("<section class=\"onboarding-summary\"");
            int overviewEnd = page.indexOf("</section>", overviewStart);
            int formPosition = page.indexOf("<form id=\"joinForm\"");

            assertThat(overviewStart).isGreaterThanOrEqualTo(0);
            assertThat(overviewEnd).isGreaterThan(overviewStart);
            assertThat(formPosition).isGreaterThan(overviewEnd);

            String overview = page.substring(overviewStart, overviewEnd);

            assertThat(overview)
                    .contains("Request access to ' + ${branding.brandName}")
                    .contains("Allow about 10 minutes.")
                    .contains("organisation details, country, industry sector, NACE code")
                    .contains("What this form is for")
                    .contains("Who can apply")
                    .contains("What happens next")
                    .contains("approved project partners during the pilot phase")
                    .contains("our team will review your request")
                    .contains("aria-labelledby=\"before-you-start-heading\"");
        }
    }
}
