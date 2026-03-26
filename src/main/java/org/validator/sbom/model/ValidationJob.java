package org.validator.sbom.model;

import java.time.Instant;

public class ValidationJob {

    private String jobId;
    private ValidationStatus status;
    private ValidationResult result;   // null until COMPLETED
    private String errorMessage;       // set on FAILED
    private Instant createdAt;

    public ValidationJob() {}

    public ValidationJob(String jobId) {
        this.jobId = jobId;
        this.status = ValidationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public ValidationStatus getStatus() { return status; }
    public void setStatus(ValidationStatus status) { this.status = status; }

    public ValidationResult getResult() { return result; }
    public void setResult(ValidationResult result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
