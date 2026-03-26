package org.validator.sbom.model;

import java.time.Instant;
import java.util.List;

public class ValidationResult {

    private boolean valid;
    private List<Violation> violations;
    private int componentCount;
    private int dependencyEdgeCount;
    private Instant completedAt;

    public ValidationResult() {}

    public ValidationResult(boolean valid, List<Violation> violations,
                            int componentCount, int dependencyEdgeCount) {
        this.valid = valid;
        this.violations = violations;
        this.componentCount = componentCount;
        this.dependencyEdgeCount = dependencyEdgeCount;
        this.completedAt = Instant.now();
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<Violation> getViolations() { return violations; }
    public void setViolations(List<Violation> violations) { this.violations = violations; }

    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int componentCount) { this.componentCount = componentCount; }

    public int getDependencyEdgeCount() { return dependencyEdgeCount; }
    public void setDependencyEdgeCount(int dependencyEdgeCount) { this.dependencyEdgeCount = dependencyEdgeCount; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
