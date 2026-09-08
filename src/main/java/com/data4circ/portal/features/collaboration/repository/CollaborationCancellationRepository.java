package com.data4circ.portal.features.collaboration.repository;

import com.data4circ.portal.features.collaboration.entity.CollaborationCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollaborationCancellationRepository extends JpaRepository<CollaborationCancellation, Long> {

    /**
     * All cancellations between two organisations in either direction, newest first.
     * Used to populate the warning shown before a new collaboration request is sent.
     */
    @Query("SELECT cc FROM CollaborationCancellation cc " +
           "JOIN FETCH cc.requesterOrg " +
           "JOIN FETCH cc.targetOrg " +
           "JOIN FETCH cc.initiatingOrg " +
           "WHERE (cc.requesterOrg.id = :orgAId AND cc.targetOrg.id = :orgBId) " +
           "   OR (cc.requesterOrg.id = :orgBId AND cc.targetOrg.id = :orgAId) " +
           "ORDER BY cc.cancelledAt DESC")
    List<CollaborationCancellation> findBetweenOrgsOrderByCancelledAtDesc(
            @Param("orgAId") Long orgAId,
            @Param("orgBId") Long orgBId);
}
