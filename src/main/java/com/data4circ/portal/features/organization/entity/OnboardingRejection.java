package com.data4circ.portal.features.organization.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A single rejection event for an {@link OrganizationOnboardingRequest}. A request can be rejected,
 * re-opened, and rejected again, so rejections are kept as a history rather than overwriting a
 * single field. Displayed on the admin onboarding request page.
 */
@Entity
@Table(name = "onboarding_rejections")
public class OnboardingRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onboarding_request_id")
    private OrganizationOnboardingRequest onboardingRequest;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    public OnboardingRejection() {}

    public OnboardingRejection(OrganizationOnboardingRequest onboardingRequest, String reason,
                               String rejectedBy, LocalDateTime rejectedAt) {
        this.onboardingRequest = onboardingRequest;
        this.reason = reason;
        this.rejectedBy = rejectedBy;
        this.rejectedAt = rejectedAt;
    }

    public Long getId() {
        return id;
    }

    public OrganizationOnboardingRequest getOnboardingRequest() {
        return onboardingRequest;
    }

    public void setOnboardingRequest(OrganizationOnboardingRequest onboardingRequest) {
        this.onboardingRequest = onboardingRequest;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
}
