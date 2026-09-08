package com.data4circ.portal.integration.ckan.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for CKAN package_search endpoint
 * Represents the response from /api/3/action/package_search
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CkanPackageSearchResponseDTO {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("result")
    private SearchResult result;

    public CkanPackageSearchResponseDTO() {
    }

    public CkanPackageSearchResponseDTO(boolean success, SearchResult result) {
        this.success = success;
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public SearchResult getResult() {
        return result;
    }

    public void setResult(SearchResult result) {
        this.result = result;
    }

    /**
     * Nested class representing the result object
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {

        @JsonProperty("count")
        private int count;

        public SearchResult() {
        }

        public SearchResult(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
