package org.validator.sbom.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.parsers.JsonParser;
import org.jboss.logging.Logger;
import org.validator.sbom.cache.JobCacheService;
import org.validator.sbom.model.ValidationResult;
import org.validator.sbom.model.Violation;
import org.validator.sbom.validator.ValidationRule;

import java.nio.charset.StandardCharsets;
import java.util.*;

@ApplicationScoped
public class ValidationService {

    private static final Logger LOG = Logger.getLogger(ValidationService.class);

    @Inject
    JobCacheService jobCacheService;

    @Inject
    Instance<ValidationRule> rules; // CDI injects all ValidationRule beans

    /**
     * Parses and validates the SBOM synchronously. Intended to be called
     * from a virtual thread so the caller is not blocked.
     */
    public void validate(String jobId, String sbomJson) {
        jobCacheService.markInProgress(jobId);
        try {
            Bom bom = parse(sbomJson);
            Map<String, Set<String>> graph = buildGraph(bom);
            Set<String> declared = buildDeclaredSet(bom);

            List<Violation> allViolations = new ArrayList<>();
            for (ValidationRule rule : rules) {
                allViolations.addAll(rule.validate(bom, graph, declared));
            }

            int edgeCount = graph.values().stream().mapToInt(Set::size).sum();
            boolean valid = allViolations.stream()
                    .noneMatch(v -> v.getSeverity() == Violation.Severity.ERROR);

            jobCacheService.completeJob(jobId,
                    new ValidationResult(valid, allViolations, declared.size(), edgeCount));

        } catch (Exception e) {
            LOG.errorf(e, "Validation failed for job %s", jobId);
            jobCacheService.failJob(jobId, e.getMessage());
        }
    }

    private Bom parse(String sbomJson) throws Exception {
        byte[] bytes = sbomJson.getBytes(StandardCharsets.UTF_8);
        JsonParser parser = new JsonParser();
        return parser.parse(bytes);
    }

    /**
     * Builds adjacency list: bomRef -> set of dependency bomRefs.
     */
    private Map<String, Set<String>> buildGraph(Bom bom) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        if (bom.getDependencies() == null) return graph;

        for (Dependency dep : bom.getDependencies()) {
            Set<String> deps = new LinkedHashSet<>();
            if (dep.getDependencies() != null) {
                for (Dependency child : dep.getDependencies()) {
                    deps.add(child.getRef());
                }
            }
            graph.put(dep.getRef(), deps);
        }
        return graph;
    }

    /**
     * Collects all bomRefs from the components list, including the metadata component if present.
     */
    private Set<String> buildDeclaredSet(Bom bom) {
        Set<String> declared = new LinkedHashSet<>();
        if (bom.getMetadata() != null && bom.getMetadata().getComponent() != null) {
            String ref = bom.getMetadata().getComponent().getBomRef();
            if (ref != null) declared.add(ref);
        }
        if (bom.getComponents() != null) {
            for (Component c : bom.getComponents()) {
                if (c.getBomRef() != null) declared.add(c.getBomRef());
            }
        }
        return declared;
    }
}
