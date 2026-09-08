package com.data4circ.portal.features.onboardingsync.keycloak;

import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step outcome of provisioning an organization in a Keycloak instance,
 * serialized into the onboarding tool sync details (same shape as
 * {@code SpipInitializationResult}).
 */
public class KeycloakInitializationResult {

    private boolean success;
    private String overallStatus;
    private String instance;
    private String realm;
    private String groupId;
    private String groupName;
    private String userId;
    private String username;
    private List<InitializationStep> steps;
    private String errorMessage;

    public KeycloakInitializationResult() {
        this.steps = new ArrayList<>();
    }

    public static class InitializationStep {
        private int step;
        private String name;
        private String status; // "pending", "completed", "failed"
        private String error;
        private Object data;

        public InitializationStep(int step, String name) {
            this.step = step;
            this.name = name;
            this.status = "pending";
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<InitializationStep> getSteps() {
        return steps;
    }

    public void setSteps(List<InitializationStep> steps) {
        this.steps = steps;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void addStep(int stepNumber, String stepName) {
        steps.add(new InitializationStep(stepNumber, stepName));
    }

    public InitializationStep getLastStep() {
        if (steps.isEmpty()) return null;
        return steps.get(steps.size() - 1);
    }

    public void markLastStepCompleted(Object data) {
        InitializationStep step = getLastStep();
        if (step != null) {
            step.setStatus("completed");
            step.setData(data);
        }
    }

    public void markLastStepFailed(String error) {
        InitializationStep step = getLastStep();
        if (step != null) {
            step.setStatus("failed");
            step.setError(error);
        }
    }
}
