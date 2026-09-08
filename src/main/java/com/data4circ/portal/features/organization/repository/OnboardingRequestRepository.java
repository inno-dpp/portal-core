package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.OnboardingRequestStatus;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingRequestRepository extends JpaRepository<OrganizationOnboardingRequest, Long> {

    List<OrganizationOnboardingRequest> findByStatus(OnboardingRequestStatus status);

    List<OrganizationOnboardingRequest> findByStatusOrderByCreatedAtDesc(OnboardingRequestStatus status);

    Optional<OrganizationOnboardingRequest> findByEmail(String email);

    Optional<OrganizationOnboardingRequest> findByCompanyName(String companyName);

    boolean existsByEmail(String email);

    boolean existsByCompanyName(String companyName);

    /**
     * Company names only (no other columns), used to pre-check SPIP access-policy label
     * collisions before a new onboarding request is created. Cheap projection query rather than
     * loading full entities, since this runs on every request submission.
     */
    @Query("SELECT r.companyName FROM OrganizationOnboardingRequest r")
    List<String> findAllCompanyNames();

    @Query("SELECT r FROM OrganizationOnboardingRequest r WHERE r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<OrganizationOnboardingRequest> findRecentRequests(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM OrganizationOnboardingRequest r WHERE r.status = :status")
    long countByStatus(@Param("status") OnboardingRequestStatus status);

    @Query("SELECT r FROM OrganizationOnboardingRequest r WHERE r.status IN :statuses ORDER BY r.createdAt DESC")
    List<OrganizationOnboardingRequest> findByStatusIn(@Param("statuses") List<OnboardingRequestStatus> statuses);

    boolean existsBySpipUser(String spipUser);

    /**
     * Find onboarding requests by CKAN organization short name.
     * Used to check uniqueness of CKAN organization names.
     *
     * @param ckanOrganizationShortName the CKAN organization short name to search for
     * @return list of onboarding requests with the given CKAN organization name
     */
    List<OrganizationOnboardingRequest> findByCkanOrganizationShortName(String ckanOrganizationShortName);

    /**
     * Find the onboarding request that originated a given (approved) organization, i.e. the one
     * carrying the applicant's original dataspace-participation answers. An organization is
     * linked to at most one request in practice; ordering by processedAt guards against the
     * theoretical case of more than one row referencing it.
     */
    Optional<OrganizationOnboardingRequest> findFirstByOrganizationIdOrderByProcessedAtDesc(Long organizationId);
}