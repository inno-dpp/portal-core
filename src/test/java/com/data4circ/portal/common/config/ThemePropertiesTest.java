package com.data4circ.portal.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the derived branding getters used by transactional emails.
 */
class ThemePropertiesTest {

    @Test
    void teamNameDerivesFromBrandNameWhenUnset() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");

        assertEquals("The Acme Portal Team", props.getTeamName());
    }

    @Test
    void teamNameTreatsBlankAsUnset() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");
        props.setTeamName("");

        assertEquals("The Acme Portal Team", props.getTeamName());
    }

    @Test
    void teamNameUsesExplicitValueWhenSet() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");
        props.setTeamName("Acme Support Crew");

        assertEquals("Acme Support Crew", props.getTeamName());
    }

    @Test
    void copyrightHolderDerivesFromBrandNameWhenUnset() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");

        assertEquals("Acme Portal", props.getCopyrightHolder());
    }

    @Test
    void copyrightHolderTreatsBlankAsUnset() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");
        props.setCopyrightHolder(" ");

        assertEquals("Acme Portal", props.getCopyrightHolder());
    }

    @Test
    void copyrightHolderUsesExplicitValueWhenSet() {
        ThemeProperties props = new ThemeProperties();
        props.setBrandName("Acme Portal");
        props.setCopyrightHolder("Acme Corp GmbH");

        assertEquals("Acme Corp GmbH", props.getCopyrightHolder());
    }

    @Test
    void supportEmailDefaultsToEmpty() {
        ThemeProperties props = new ThemeProperties();

        assertEquals("", props.getSupportEmail());
    }
}
