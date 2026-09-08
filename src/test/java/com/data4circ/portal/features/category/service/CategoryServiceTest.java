package com.data4circ.portal.features.category.service;

import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.features.category.dto.CategoryFormDTO;
import com.data4circ.portal.features.category.dto.CategorySaveResult;
import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.enums.CategoryStyle;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.features.onboardingsync.ckan.CkanOnboardingSyncService;
import com.data4circ.portal.features.onboardingsync.ckan.GroupMembershipBackfillResult;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategoryService}: slug resolution/uniqueness on create, the
 * anti-mass-assignment field copy on update (slug/active are never touched), and that a CKAN
 * sync failure on any of the four operations never blocks the DB write — it's surfaced as a
 * warning on the returned {@link CategorySaveResult} instead.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CkanApiClient ckanApiClient;

    @Mock
    private CkanOnboardingSyncService ckanOnboardingSyncService;

    @InjectMocks
    private CategoryService categoryService;

    private Category persisted;

    @BeforeEach
    void setUp() {
        persisted = new Category("catalytic-converters", "Catalytic Converters",
                "Old description", "fas fa-car", CategoryStyle.PRIMARY);
        persisted.setId(1L);
        // save() is called with the entity being mutated in place; return whatever was passed in
        // so assertions can inspect the same object the service worked on. lenient() because not
        // every test reaches save() (e.g. the duplicate-slug and not-found tests throw first).
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Only create() reaches this; lenient() so the other tests don't need to care about it.
        lenient().when(ckanOnboardingSyncService.grantCategoryToAllOrganizations(anyString())).thenReturn(List.of());
    }

    @Test
    void createSlugifiesTitleWhenSlugLeftBlank() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Plastic & Agriculture!");
        form.setDescription("desc");

        CategorySaveResult result = categoryService.create(form);

        assertThat(result.getCategory().getSlug()).isEqualTo("plastic-agriculture");
        assertThat(result.hasCkanWarning()).isFalse();
        verify(ckanApiClient).createGroup("plastic-agriculture", "Plastic & Agriculture!", "desc");
    }

    @Test
    void createRejectsDuplicateSlug() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Catalytic Converters");
        when(categoryRepository.existsBySlug("catalytic-converters")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalytic-converters");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createSkipsCkanCreateWhenGroupAlreadyExistsInCkan() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Others");
        when(ckanApiClient.groupExists("others")).thenReturn(true);

        CategorySaveResult result = categoryService.create(form);

        assertThat(result.hasCkanWarning()).isFalse();
        verify(ckanApiClient, never()).createGroup(anyString(), anyString(), anyString());
    }

    @Test
    void createSurfacesCkanFailureAsWarningButStillSavesDbRow() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Others");
        form.setDescription("desc");
        doThrow(new CkanApiClient.CkanApiException("CKAN unavailable"))
                .when(ckanApiClient).createGroup(anyString(), anyString(), anyString());

        CategorySaveResult result = categoryService.create(form);

        assertThat(result.getCategory()).isNotNull();
        assertThat(result.hasCkanWarning()).isTrue();
        assertThat(result.getCkanWarning()).contains("CKAN unavailable");
    }

    @Test
    void createGrantsNewCategoryToExistingOrganizations() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Others");
        List<GroupMembershipBackfillResult> grants = List.of(
                new GroupMembershipBackfillResult("Acme Corp", "acmeuser", List.of("others"), null));
        when(ckanOnboardingSyncService.grantCategoryToAllOrganizations("others")).thenReturn(grants);

        CategorySaveResult result = categoryService.create(form);

        assertThat(result.getExistingOrganizationsGranted()).isEqualTo(grants);
        verify(ckanOnboardingSyncService).grantCategoryToAllOrganizations("others");
    }

    @Test
    void createSwallowsGrantFailureWithoutFailingTheRequest() {
        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("Others");
        doThrow(new RuntimeException("unexpected"))
                .when(ckanOnboardingSyncService).grantCategoryToAllOrganizations(anyString());

        CategorySaveResult result = categoryService.create(form);

        assertThat(result.getCategory()).isNotNull();
        assertThat(result.getExistingOrganizationsGranted()).isEmpty();
    }

    @Test
    void updateCopiesOnlyDisplayFieldsNeverSlugOrActive() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(persisted));

        CategoryFormDTO form = new CategoryFormDTO();
        form.setTitle("New Title");
        form.setDescription("New description");
        form.setIcon("fas fa-recycle");
        form.setStyle(CategoryStyle.SUCCESS);

        CategorySaveResult result = categoryService.update(1L, form);

        Category updated = result.getCategory();
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getIcon()).isEqualTo("fas fa-recycle");
        assertThat(updated.getStyle()).isEqualTo(CategoryStyle.SUCCESS);
        // Untouched:
        assertThat(updated.getSlug()).isEqualTo("catalytic-converters");
        assertThat(updated.isActive()).isTrue();

        verify(ckanApiClient).patchGroup(eq("catalytic-converters"), any());
    }

    @Test
    void updateThrowsWhenCategoryNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, new CategoryFormDTO()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deactivateFlipsActiveAndSoftDeletesInCkan() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(persisted));

        CategorySaveResult result = categoryService.deactivate(1L);

        assertThat(result.getCategory().isActive()).isFalse();
        verify(ckanApiClient).deleteGroup("catalytic-converters");
    }

    @Test
    void deactivateCkanFailureDoesNotBlockDbFlip() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(persisted));
        doThrow(new CkanApiClient.CkanApiException("CKAN down"))
                .when(ckanApiClient).deleteGroup(anyString());

        CategorySaveResult result = categoryService.deactivate(1L);

        assertThat(result.getCategory().isActive()).isFalse();
        assertThat(result.hasCkanWarning()).isTrue();
    }

    @Test
    void reactivateFlipsActiveAndRestoresInCkan() {
        persisted.setActive(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(persisted));

        CategorySaveResult result = categoryService.reactivate(1L);

        assertThat(result.getCategory().isActive()).isTrue();
        verify(ckanApiClient).patchGroup("catalytic-converters", Map.of("state", "active"));
    }
}
