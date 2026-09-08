package com.data4circ.portal.features.collaboration.repository;

import com.data4circ.portal.features.collaboration.entity.CollaborationRequest;
import com.data4circ.portal.features.collaboration.entity.CollaborationStatus;
import com.data4circ.portal.features.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationRequestRepository extends JpaRepository<CollaborationRequest, Long> {

    /**
     * Find an active request between two organizations in either direction.
     * Used to prevent duplicates.
     */
    @Query("SELECT cr FROM CollaborationRequest cr " +
           "WHERE cr.status IN :statuses " +
           "AND ((cr.requesterOrg.id = :orgAId AND cr.targetOrg.id = :orgBId) " +
           "  OR (cr.requesterOrg.id = :orgBId AND cr.targetOrg.id = :orgAId))")
    Optional<CollaborationRequest> findActiveRequestBetween(
            @Param("orgAId") Long orgAId,
            @Param("orgBId") Long orgBId,
            @Param("statuses") List<CollaborationStatus> statuses);

    /**
     * Find the latest request between two organizations in either direction.
     * Used to determine button state on the view page.
     */
    @Query("SELECT cr FROM CollaborationRequest cr " +
           "JOIN FETCH cr.requesterOrg " +
           "JOIN FETCH cr.targetOrg " +
           "WHERE (cr.requesterOrg.id = :orgAId AND cr.targetOrg.id = :orgBId) " +
           "   OR (cr.requesterOrg.id = :orgBId AND cr.targetOrg.id = :orgAId) " +
           "ORDER BY cr.createdAt DESC")
    List<CollaborationRequest> findLatestBetween(
            @Param("orgAId") Long orgAId,
            @Param("orgBId") Long orgBId);

    /**
     * Find pending incoming requests for a target organization (edit page).
     * JOIN FETCH ensures requesterOrg is loaded within the session.
     */
    @Query("SELECT cr FROM CollaborationRequest cr " +
           "JOIN FETCH cr.requesterOrg " +
           "JOIN FETCH cr.targetOrg " +
           "WHERE cr.targetOrg.id = :targetOrgId AND cr.status = :status " +
           "ORDER BY cr.createdAt DESC")
    List<CollaborationRequest> findByTargetOrgIdAndStatusOrderByCreatedAtDesc(
            @Param("targetOrgId") Long targetOrgId,
            @Param("status") CollaborationStatus status);

    /**
     * Find approved collaborators where this org was the requester.
     */
    @Query("SELECT cr.targetOrg FROM CollaborationRequest cr " +
           "WHERE cr.status = com.data4circ.portal.features.collaboration.entity.CollaborationStatus.APPROVED " +
           "AND cr.requesterOrg.id = :orgId")
    List<Organization> findApprovedCollaboratorsByRequester(@Param("orgId") Long orgId);

    /**
     * Find approved collaborators where this org was the target.
     */
    @Query("SELECT cr.requesterOrg FROM CollaborationRequest cr " +
           "WHERE cr.status = com.data4circ.portal.features.collaboration.entity.CollaborationStatus.APPROVED " +
           "AND cr.targetOrg.id = :orgId")
    List<Organization> findApprovedCollaboratorsByTarget(@Param("orgId") Long orgId);

    /**
     * Approved collaboration requests involving this org (either side), so the partner org and
     * the approval timestamp can both be shown. Newest approvals first.
     */
    @Query("SELECT cr FROM CollaborationRequest cr " +
           "JOIN FETCH cr.requesterOrg JOIN FETCH cr.targetOrg " +
           "WHERE cr.status = com.data4circ.portal.features.collaboration.entity.CollaborationStatus.APPROVED " +
           "AND (cr.requesterOrg.id = :orgId OR cr.targetOrg.id = :orgId) " +
           "ORDER BY cr.processedAt DESC NULLS LAST")
    List<CollaborationRequest> findApprovedInvolvingOrg(@Param("orgId") Long orgId);

    /**
     * Count pending incoming requests for a target org (notification badge).
     */
    long countByTargetOrgIdAndStatus(Long targetOrgId, CollaborationStatus status);

    /**
     * All requests ordered by created date (PLATFORM_ADMIN view).
     */
    List<CollaborationRequest> findAllByOrderByCreatedAtDesc();

    /**
     * All requests of a given status (PLATFORM_ADMIN filtered view).
     */
    List<CollaborationRequest> findByStatusOrderByCreatedAtDesc(CollaborationStatus status);
}
