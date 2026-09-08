package com.data4circ.portal.features.onboardingsync.tool;

import com.data4circ.portal.features.onboardingsync.tool.entity.OnboardingToolSync;
import com.data4circ.portal.features.onboardingsync.tool.repository.OnboardingToolSyncRepository;
import com.data4circ.portal.features.organization.entity.OrganizationOnboardingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Small facade over {@link OnboardingToolSyncRepository} so callers do not repeat
 * upsert/lookup plumbing for per-tool sync state.
 */
@Service
public class OnboardingToolSyncStateService {

    private final OnboardingToolSyncRepository repository;

    public OnboardingToolSyncStateService(OnboardingToolSyncRepository repository) {
        this.repository = repository;
    }

    public boolean isSynced(Long requestId, String toolKey) {
        return repository.existsByRequestIdAndToolKeyAndSyncedTrue(requestId, toolKey);
    }

    /** Sync flags for all tools of a request, keyed by tool key. */
    public Map<String, Boolean> syncMapFor(Long requestId) {
        return repository.findByRequestId(requestId).stream()
                .collect(Collectors.toMap(OnboardingToolSync::getToolKey, OnboardingToolSync::isSynced));
    }

    @Transactional
    public OnboardingToolSync markSynced(OrganizationOnboardingRequest request, String toolKey, String details) {
        OnboardingToolSync sync = repository.findByRequestIdAndToolKey(request.getId(), toolKey)
                .orElseGet(() -> {
                    OnboardingToolSync created = new OnboardingToolSync();
                    created.setRequest(request);
                    created.setToolKey(toolKey);
                    return created;
                });
        sync.setSynced(true);
        sync.setSyncedAt(LocalDateTime.now());
        sync.setDetails(details);
        return repository.save(sync);
    }
}
