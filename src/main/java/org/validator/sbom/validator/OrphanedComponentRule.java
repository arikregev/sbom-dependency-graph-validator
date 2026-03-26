package org.validator.sbom.validator;

import jakarta.enterprise.context.ApplicationScoped;
import org.cyclonedx.model.Bom;
import org.validator.sbom.model.Violation;

import java.util.*;

/**
 * Warns about components that are declared but never appear in any dependency relationship.
 * These are likely unused or forgotten entries.
 */
@ApplicationScoped
public class OrphanedComponentRule implements ValidationRule {

    @Override
    public String ruleId() {
        return "ORPHANED_COMPONENT";
    }

    @Override
    public List<Violation> validate(Bom bom, Map<String, Set<String>> graph, Set<String> declared) {
        // Collect all refs that appear anywhere in the graph
        Set<String> referenced = new HashSet<>(graph.keySet());
        graph.values().forEach(referenced::addAll);

        List<Violation> violations = new ArrayList<>();
        for (String ref : declared) {
            if (!referenced.contains(ref)) {
                violations.add(new Violation(
                        ruleId(),
                        Violation.Severity.WARNING,
                        "Component declared but not present in any dependency relationship: " + ref,
                        ref
                ));
            }
        }
        return violations;
    }
}
