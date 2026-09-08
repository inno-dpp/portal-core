package com.data4circ.portal.features.collaboration.repository;

import com.data4circ.portal.features.collaboration.entity.CollaborationRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborationRejectionRepository extends JpaRepository<CollaborationRejection, Long> {

    /**
     * All rejections between two organisations in either direction, newest first.
     * Used for the warning shown before a new request is sent and the full history panel.
     */
    @Query("SELECT cr FROM CollaborationRejection cr " +
           "JOIN FETCH cr.requesterOrg " +
           "JOIN FETCH cr.targetOrg " +
           "WHERE (cr.requesterOrg.id = :orgAId AND cr.targetOrg.id = :orgBId) " +
           "   OR (cr.requesterOrg.id = :orgBId AND cr.targetOrg.id = :orgAId) " +
           "ORDER BY cr.rejectedAt DESC")
    List<CollaborationRejection> findBetweenOrgsOrderByRejectedAtDesc(
            @Param("orgAId") Long orgAId,
            @Param("orgBId") Long orgBId);

    /**
     * Rejections issued BY a target organisation against a specific requester, newest first.
     * Used to show the reviewing admin the history for the requester whose new request is pending.
     */
    @Query("SELECT cr FROM CollaborationRejection cr " +
           "JOIN FETCH cr.requesterOrg " +
           "JOIN FETCH cr.targetOrg " +
           "WHERE cr.requesterOrg.id = :requesterOrgId AND cr.targetOrg.id = :targetOrgId " +
           "ORDER BY cr.rejectedAt DESC")
    List<CollaborationRejection> findByRequesterAndTargetOrderByRejectedAtDesc(
            @Param("requesterOrgId") Long requesterOrgId,
            @Param("targetOrgId") Long targetOrgId);

    /**
     * All requests this org has rejected (as the target), newest first. Backs the standalone
     * "Past Collaboration Requests" history section on the org edit page.
     */
    @Query("SELECT cr FROM CollaborationRejection cr " +
           "JOIN FETCH cr.requesterOrg " +
           "JOIN FETCH cr.targetOrg " +
           "WHERE cr.targetOrg.id = :targetOrgId " +
           "ORDER BY cr.rejectedAt DESC")
    List<CollaborationRejection> findByTargetOrgIdOrderByRejectedAtDesc(@Param("targetOrgId") Long targetOrgId);
}
