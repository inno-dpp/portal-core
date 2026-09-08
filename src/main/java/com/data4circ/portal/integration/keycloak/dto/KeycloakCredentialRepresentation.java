package com.data4circ.portal.integration.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Keycloak Admin API credential representation used for the reset-password call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeycloakCredentialRepresentation {

    private String type;
    private String value;
    private Boolean temporary;

    public static KeycloakCredentialRepresentation password(String value, boolean temporary) {
        KeycloakCredentialRepresentation credential = new KeycloakCredentialRepresentation();
        credential.setType("password");
        credential.setValue(value);
        credential.setTemporary(temporary);
        return credential;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getTemporary() {
        return temporary;
    }

    public void setTemporary(Boolean temporary) {
        this.temporary = temporary;
    }
}
