package com.data4circ.portal.common.exception;

public class AccessDeniedException extends RuntimeException {

    private final String resource;
    private final String action;

    public AccessDeniedException(String resource, String action) {
        super(String.format("Access denied: Cannot %s %s", action, resource));
        this.resource = resource;
        this.action = action;
    }

    public AccessDeniedException(String message) {
        super(message);
        this.resource = null;
        this.action = null;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }
}