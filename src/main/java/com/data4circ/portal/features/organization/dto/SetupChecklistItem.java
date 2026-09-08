package com.data4circ.portal.features.organization.dto;

/**
 * One row in the "Organization setup status" checklist shown at the top of the organisation view
 * page to its own admin. Each item states whether a setup step is done and links to where the user
 * can act on it.
 */
public class SetupChecklistItem {

    private final String label;
    private final boolean done;
    private final String actionLabel;
    private final String actionUrl;

    public SetupChecklistItem(String label, boolean done, String actionLabel, String actionUrl) {
        this.label = label;
        this.done = done;
        this.actionLabel = actionLabel;
        this.actionUrl = actionUrl;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDone() {
        return done;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getActionUrl() {
        return actionUrl;
    }
}
