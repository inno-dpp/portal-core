package com.data4circ.portal.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

/**
 * The portal UI is English-only. Pin the request locale to British English so locale-sensitive
 * rendering (month names in dates, etc.) is consistent regardless of the browser's Accept-Language
 * or the server's default locale — e.g. avoids "fevereiro 2026" appearing in an English interface.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.UK);
    }
}
