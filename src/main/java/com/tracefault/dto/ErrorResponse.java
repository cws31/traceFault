package com.tracefault.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Standardized error response payload following RFC 7807 problem details semantics")
public class ErrorResponse {

    @Schema(description = "Timestamp when the error occurred", example = "2026-10-31T16:00:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code integer", example = "409")
    private int status;

    @Schema(description = "Short error category name", example = "Conflict")
    private String error;

    @Schema(description = "Human-readable explanation of the error", example = "Idempotency key 'KEY-DEMO-001' was already used for a different payment payload")
    private String message;

    @Schema(description = "Request URI path where the error occurred", example = "/api/payments")
    private String path;

    @Schema(description = "Field-level validation error details if applicable")
    private Map<String, String> validationErrors;

    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, String> validationErrors) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.validationErrors = validationErrors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
