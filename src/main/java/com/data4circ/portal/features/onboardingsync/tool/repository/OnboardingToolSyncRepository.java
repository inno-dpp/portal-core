package com.data4circ.portal.features.onboardingsync.tool.repository;

import com.data4circ.portal.features.onboardingsync.tool.entity.OnboardingToolSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingToolSyncRepository extends JpaRepository<OnboardingToolSync, Long> {

    Optional<OnboardingToolSync> findByRequestIdAndToolKey(Long requestId, String toolKey);

    List<OnboardingToolSync> findByRequestId(Long requestId);

    boolean existsByRequestIdAndToolKeyAndSyncedTrue(Long requestId, String toolKey);
}
