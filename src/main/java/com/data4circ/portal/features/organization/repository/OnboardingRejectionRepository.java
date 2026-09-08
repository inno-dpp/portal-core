package com.data4circ.portal.features.organization.repository;

import com.data4circ.portal.features.organization.entity.OnboardingRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnboardingRejectionRepository extends JpaRepository<OnboardingRejection, Long> {

    /** Full rejection history for a request, most recent first. */
    List<OnboardingRejection> findByOnboardingRequestIdOrderByRejectedAtDesc(Long onboardingRequestId);
}
