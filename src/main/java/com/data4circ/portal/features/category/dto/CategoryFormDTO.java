package com.data4circ.portal.features.category.dto;

import com.data4circ.portal.features.category.enums.CategoryStyle;
import jakarta.validation.constraints.NotBlank;

/**
 * Form-binding DTO for the category create/edit screens — kept separate from the {@code
 * Category} entity because the two forms bind different fields: "new" accepts an optional slug
 * (auto-generated from the title when left blank), "edit" never shows or binds slug at all
 * (immutable after creation). Binding straight to {@code Category} would force a single
 * validation shape onto both forms; this DTO keeps {@code slug} unvalidated at the bean level so
 * it stays optional on create and simply ignored on edit, while {@code CategoryService} still
 * only ever copies the fields it explicitly reads (never mass-assigns).
 */
public class CategoryFormDTO {

    /** Only read on create; ignored on edit even if a client posts one. */
    private String slug;

    @NotBlank
    private String title;

    private String description;

    private String icon;

    private CategoryStyle style;

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
}
