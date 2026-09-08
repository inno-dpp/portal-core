package com.data4circ.portal.features.organization.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "nace_codes")
public class NaceCode {

    // 4-digit NACE "class" level (e.g. "01.11"). 2-digit "division" and 3-digit "group"
    // codes denote broader families and are not permitted for organisation selection.
    private static final java.util.regex.Pattern CLASS_CODE_PATTERN = java.util.regex.Pattern.compile("^\\d{2}\\.\\d{2}$");

    public static boolean isClassCode(String code) {
        return code != null && CLASS_CODE_PATTERN.matcher(code).matches();
    }

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 5)
    private String section;

    @Column(name = "section_label", length = 200)
    private String sectionLabel;

    public NaceCode() {
    }

    public NaceCode(String code, String description, String section, String sectionLabel) {
        this.code = code;
        this.description = description;
        this.section = section;
        this.sectionLabel = sectionLabel;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSectionLabel() {
        return sectionLabel;
    }

    public void setSectionLabel(String sectionLabel) {
        this.sectionLabel = sectionLabel;
    }

    public String getDisplayLabel() {
        return code + " - " + description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NaceCode naceCode = (NaceCode) o;
        return code != null && code.equals(naceCode.code);
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }
}
