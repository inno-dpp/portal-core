package com.data4circ.portal.features.organization.entity;

public enum IndustrySector {
    //ELECTRONICS("Electronics and WEEE"),
    //AUTOMOTIVE("Automotive"),
    ELECTRICAL_ELECTRONICS("Electrical and Electronic Equipment"),
    CATALYTIC_CONVERTERS("Catalytic Converters"),
    PLASTIC_AGRICULTURE("Agricultural Plastic"),
    //PLASTICS("Plastics and Packaging"),
    METALS("Metals and Materials"),
    TEXTILES("Textiles"),
    CONSTRUCTION("Construction"),
    CHEMICALS("Chemicals"),
    //AGRICULTURE("Agriculture"),
    DATA_ANALYTICS("Data Analytics"),
    RESEARCH("Research"),
    OTHERS("Others");

    private final String displayName;

    IndustrySector(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
