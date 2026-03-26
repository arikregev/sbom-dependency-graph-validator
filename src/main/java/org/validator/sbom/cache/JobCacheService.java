package org.validator.sbom.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.validator.sbom.model.ValidationJob;
import org.validator.sbom.model.ValidationResult;
import org.validator.sbom.model.ValidationStatus;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class JobCacheService {

    @ConfigProperty(name = "sbom.validator.cache.ttl-hours", defaultValue = "24")
    long cacheTtlHours;

    private Cache<String, ValidationJob> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlHours, TimeUnit.HOURS)
                .build();
    }

    public ValidationJob createJob(String jobId) {
        ValidationJob job = new ValidationJob(jobId);
        cache.put(jobId, job);
        return job;
    }

    public Optional<ValidationJob> getJob(String jobId) {
        return Optional.ofNullable(cache.getIfPresent(jobId));
    }

    public void markInProgress(String jobId) {
        getJob(jobId).ifPresent(job -> job.setStatus(ValidationStatus.IN_PROGRESS));
    }

    public void completeJob(String jobId, ValidationResult result) {
        getJob(jobId).ifPresent(job -> {
            job.setResult(result);
            job.setStatus(ValidationStatus.COMPLETED);
        });
    }

    public void failJob(String jobId, String errorMessage) {
        getJob(jobId).ifPresent(job -> {
            job.setErrorMessage(errorMessage);
            job.setStatus(ValidationStatus.FAILED);
        });
    }
}
