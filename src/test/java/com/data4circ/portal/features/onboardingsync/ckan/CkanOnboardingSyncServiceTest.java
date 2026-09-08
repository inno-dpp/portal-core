package com.data4circ.portal.features.onboardingsync.ckan;

import com.data4circ.portal.features.category.entity.Category;
import com.data4circ.portal.features.category.enums.CategoryStyle;
import com.data4circ.portal.features.category.repository.CategoryRepository;
import com.data4circ.portal.features.organization.entity.Organization;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import com.data4circ.portal.features.organization.repository.OnboardingRequestRepository;
import com.data4circ.portal.features.organization.entity.OrganizationSpipUser;
import com.data4circ.portal.features.organization.repository.OrganizationSpipUserRepository;
import com.data4circ.portal.integration.ckan.client.CkanApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CKAN group-membership step added to onboarding sync — see
 * docs/developer/ckan/CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md. Covers only the new
 * behaviour (the category list comes from the portal's own active {@code Category} rows,
 * membership grants are non-fatal per group, and the step no-ops cleanly when there are no
 * active categories); the pre-existing org/user/membership/token steps are exercised by the
 * integration tests that mock this service wholesale.
 */
@ExtendWith(MockitoExtension.class)
class CkanOnboardingSyncServiceTest {

    private static final List<String> GROUPS = List.of(
            "catalytic-converters", "electrical-and-electronic-equipment", "plastic-agriculture", "others");

    @Mock
    private CkanApiClient ckanApiClient;

    @Mock
    private OnboardingRequestRepository onboardingRequestRepository;

    @Mock
    private OrganizationSpipUserRepository organizationSpipUserRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private CkanOnboardingSyncService service;
    private OrganizationOnboardingRequest request;

    @BeforeEach
    void setUp() {
        service = new CkanOnboardingSyncService(
                ckanApiClient, onboardingRequestRepository, organizationSpipUserRepository, categoryRepository);

        request = new OrganizationOnboardingRequest();
        request.setCompanyName("Acme Corp");
        request.setSpipUser("acmeuser");
        request.setEmail("acme@example.com");
        request.setSpipPassword("plaintext-password");
    }

    private static List<Category> activeCategories(List<String> slugs) {
        return slugs.stream()
                .map(slug -> new Category(slug, slug, "desc", "fas fa-tags", CategoryStyle.PRIMARY))
                .toList();
    }

    @Test
    void grantsEditorMembershipOnEveryActiveCategory() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(activeCategories(GROUPS));

        CkanSyncResult result = service.synchronizeOnboardingRequest(request, "acme-corp");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupMembershipsGranted()).containsExactlyInAnyOrderElementsOf(GROUPS);
        for (String slug : GROUPS) {
            verify(ckanApiClient).addUserToGroup(slug, "acmeuser", "editor");
        }
    }

    @Test
    void treatsAlreadyAMemberAsGranted() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(activeCategories(GROUPS));
        doAnswer(invocation -> {
            if ("catalytic-converters".equals(invocation.getArgument(0))) {
                throw new CkanApiClient.CkanApiException("User is already a member of group: catalytic-converters");
            }
            return null;
        }).when(ckanApiClient).addUserToGroup(anyString(), anyString(), anyString());

        CkanSyncResult result = service.synchronizeOnboardingRequest(request, "acme-corp");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupMembershipsGranted()).containsExactlyInAnyOrderElementsOf(GROUPS);
    }

    @Test
    void groupFailureIsNonFatalAndOmitsOnlyThatGroup() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(activeCategories(GROUPS));
        doAnswer(invocation -> {
            if ("others".equals(invocation.getArgument(0))) {
                throw new CkanApiClient.CkanApiException("Access denied: User acmeuser not authorized to edit these groups");
            }
            return null;
        }).when(ckanApiClient).addUserToGroup(anyString(), anyString(), anyString());

        CkanSyncResult result = service.synchronizeOnboardingRequest(request, "acme-corp");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupMembershipsGranted())
                .containsExactlyInAnyOrder("catalytic-converters", "electrical-and-electronic-equipment", "plastic-agriculture")
                .doesNotContain("others");
    }

    @Test
    void skipsGroupStepWhenNoActiveCategories() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(Collections.emptyList());

        CkanSyncResult result = service.synchronizeOnboardingRequest(request, "acme-corp");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupMembershipsGranted()).isEmpty();
        verify(ckanApiClient, never()).addUserToGroup(anyString(), anyString(), anyString());
    }

    @Test
    void directCreationAlsoGrantsGroupMembership() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(activeCategories(GROUPS));
        when(onboardingRequestRepository.findByCkanOrganizationShortName(anyString()))
                .thenReturn(Collections.emptyList());
        when(organizationSpipUserRepository.findByCkanOrganizationName(anyString()))
                .thenReturn(Collections.emptyList());

        CkanSyncResult result = service.synchronizeDirectCreation(
                "Acme Corp", "acmeuser", "plaintext-password", "acme@example.com");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGroupMembershipsGranted()).containsExactlyInAnyOrderElementsOf(GROUPS);
    }

    private static OrganizationSpipUser spipUser(String orgName, String username, boolean ckanSynchronized) {
        Organization org = new Organization();
        org.setName(orgName);
        OrganizationSpipUser spipUser = new OrganizationSpipUser(org, username, "plaintext-password");
        spipUser.setCkanSynchronized(ckanSynchronized);
        return spipUser;
    }

    @Test
    void backfillGrantsActiveCategoriesToEveryOrgWithACkanAccount() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(activeCategories(GROUPS));
        when(organizationSpipUserRepository.findAll()).thenReturn(List.of(
                spipUser("Acme Corp", "acmeuser", true),
                spipUser("Beta Inc", "betauser", true)));

        List<GroupMembershipBackfillResult> results = service.backfillGroupMembershipForAllOrganizations();

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(GroupMembershipBackfillResult::isSuccess);
        assertThat(results).extracting(GroupMembershipBackfillResult::categoriesGranted)
                .allMatch(granted -> granted.containsAll(GROUPS));
        verify(ckanApiClient, org.mockito.Mockito.times(GROUPS.size()))
                .addUserToGroup(anyString(), org.mockito.ArgumentMatchers.eq("acmeuser"), anyString());
        verify(ckanApiClient, org.mockito.Mockito.times(GROUPS.size()))
                .addUserToGroup(anyString(), org.mockito.ArgumentMatchers.eq("betauser"), anyString());
    }

    @Test
    void backfillSkipsOrgsThatNeverGotACkanAccount() {
        when(organizationSpipUserRepository.findAll()).thenReturn(List.of(
                spipUser("Gamma LLC", "gammauser", false)));

        List<GroupMembershipBackfillResult> results = service.backfillGroupMembershipForAllOrganizations();

        assertThat(results).isEmpty();
        verify(ckanApiClient, never()).addUserToGroup(anyString(), anyString(), anyString());
    }

    @Test
    void backfillIsEmptyWhenThereAreNoOrganizations() {
        when(organizationSpipUserRepository.findAll()).thenReturn(Collections.emptyList());

        List<GroupMembershipBackfillResult> results = service.backfillGroupMembershipForAllOrganizations();

        assertThat(results).isEmpty();
    }

    @Test
    void grantCategoryToAllOrganizationsGrantsOnlyThatCategory() {
        when(organizationSpipUserRepository.findAll()).thenReturn(List.of(
                spipUser("Acme Corp", "acmeuser", true),
                spipUser("Beta Inc", "betauser", true)));

        List<GroupMembershipBackfillResult> results = service.grantCategoryToAllOrganizations("others");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(GroupMembershipBackfillResult::isSuccess);
        assertThat(results).extracting(GroupMembershipBackfillResult::categoriesGranted)
                .containsOnly(List.of("others"));
        verify(ckanApiClient).addUserToGroup("others", "acmeuser", "editor");
        verify(ckanApiClient).addUserToGroup("others", "betauser", "editor");
        verify(ckanApiClient, never()).addUserToGroup(org.mockito.ArgumentMatchers.eq("catalytic-converters"), anyString(), anyString());
    }

    @Test
    void grantCategoryToAllOrganizationsSkipsOrgsWithoutACkanAccount() {
        when(organizationSpipUserRepository.findAll()).thenReturn(List.of(
                spipUser("Gamma LLC", "gammauser", false)));

        List<GroupMembershipBackfillResult> results = service.grantCategoryToAllOrganizations("others");

        assertThat(results).isEmpty();
        verify(ckanApiClient, never()).addUserToGroup(anyString(), anyString(), anyString());
    }

    @Test
    void grantCategoryToAllOrganizationsTreatsAlreadyAMemberAsSuccess() {
        when(organizationSpipUserRepository.findAll()).thenReturn(List.of(spipUser("Acme Corp", "acmeuser", true)));
        doThrow(new CkanApiClient.CkanApiException("User is already a member of group: others"))
                .when(ckanApiClient).addUserToGroup("others", "acmeuser", "editor");

        List<GroupMembershipBackfillResult> results = service.grantCategoryToAllOrganizations("others");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(0).categoriesGranted()).containsExactly("others");
    }
}
