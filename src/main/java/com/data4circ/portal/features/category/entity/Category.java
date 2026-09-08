package com.data4circ.portal.features.category.entity;

import com.data4circ.portal.features.category.enums.CategoryStyle;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * A portal-managed CKAN group ("use-case category" in the UI). The portal DB is the source of
 * truth: platform admins create/edit/deactivate these here, and {@code CategoryService} keeps
 * the corresponding CKAN group in sync. Replaces the old branding.yml-driven dashboard cards
 * and the hardcoded CKAN group slugs that used to live in {@code DashboardController}.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * CKAN group name. Set once at creation (generated from the title if left blank) and never
     * edited afterward — CKAN group renames are messy (break existing links/dataset filters),
     * so the admin form only accepts a slug on create.
     */
    @NotBlank
    @Column(name = "slug", nullable = false, unique = true, updatable = false, length = 100)
    private String slug;

    @NotBlank
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @NotBlank
    @Column(name = "icon", nullable = false, length = 100)
    private String icon = "fas fa-tags";

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "style", nullable = false, length = 20)
    private CategoryStyle style = CategoryStyle.PRIMARY;

    /**
     * Soft-delete flag. Deactivating a category hides it from the dashboard and from onboarding's
     * group-membership grant, and soft-deletes the CKAN group (state=deleted) — the row itself is
     * never removed, mirroring {@code Organization}'s {@code CertificationStatus} pattern.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Optional sort key for dashboard card order; falls back to id order when null. */
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Category() {
    }

    public Category(String slug, String title, String description, String icon, CategoryStyle style) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.style = style;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public CategoryStyle getStyle() {
        return style;
    }

    public void setStyle(CategoryStyle style) {
        this.style = style;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Helper methods (mirrors Connector.getStatusBadgeClass())

    public String getStatusBadgeClass() {
        return active ? "bg-success" : "bg-secondary";
    }

    public String getStatusLabel() {
        return active ? "Active" : "Inactive";
    }
}
