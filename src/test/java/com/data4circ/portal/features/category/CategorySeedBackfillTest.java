package com.data4circ.portal.features.category;

import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategorySeedBackfill}: idempotent seeding, and the "CKAN down at
 * startup" case — a failed CKAN sync must never stop the DB rows from being seeded or abort
 * the runner.
 */
@ExtendWith(MockitoExtension.class)
class CategorySeedBackfillTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CkanApiClient ckanApiClient;

    @InjectMocks
    private CategorySeedBackfill backfill;

    @Captor
    private ArgumentCaptor<Category> categoryCaptor;

    @Test
    void seedsAllFourDefaultsWhenDbIsEmpty() {
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);

        backfill.run(null);

        verify(categoryRepository, times(4)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getAllValues())
                .extracting(Category::getSlug)
                .containsExactlyInAnyOrder(
                        "catalytic-converters", "electrical-and-electronic-equipment",
                        "plastic-agriculture", "others");
        verify(ckanApiClient, times(4)).createGroup(anyString(), anyString(), anyString());
    }

    @Test
    void skipsSlugsAlreadyPresent() {
        when(categoryRepository.existsBySlug("catalytic-converters")).thenReturn(true);
        when(categoryRepository.existsBySlug("electrical-and-electronic-equipment")).thenReturn(false);
        when(categoryRepository.existsBySlug("plastic-agriculture")).thenReturn(false);
        when(categoryRepository.existsBySlug("others")).thenReturn(false);

        backfill.run(null);

        verify(categoryRepository, never()).save(argThatSlugIs("catalytic-converters"));
        verify(categoryRepository, times(3)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getAllValues())
                .extracting(Category::getSlug)
                .containsExactlyInAnyOrder("electrical-and-electronic-equipment", "plastic-agriculture", "others");
        verify(ckanApiClient, never()).createGroup(org.mockito.ArgumentMatchers.eq("catalytic-converters"), anyString(), anyString());
    }

    @Test
    void ckanFailureOnOneSeedStillPersistsDbRowAndProcessesTheRest() {
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        doThrow(new CkanApiClient.CkanApiException("CKAN unavailable at startup"))
                .when(ckanApiClient).createGroup(org.mockito.ArgumentMatchers.eq("catalytic-converters"), anyString(), anyString());

        assertThatCode(() -> backfill.run(null)).doesNotThrowAnyException();

        // All 4 DB rows still get saved, including the one whose CKAN sync failed.
        verify(categoryRepository, times(4)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getAllValues())
                .extracting(Category::getSlug)
                .containsExactlyInAnyOrder(
                        "catalytic-converters", "electrical-and-electronic-equipment",
                        "plastic-agriculture", "others");
        // And the other 3 still got their CKAN sync attempted.
        verify(ckanApiClient, times(4)).createGroup(anyString(), anyString(), anyString());
    }

    private static Category argThatSlugIs(String slug) {
        return org.mockito.ArgumentMatchers.argThat(c -> c != null && slug.equals(c.getSlug()));
    }
}
