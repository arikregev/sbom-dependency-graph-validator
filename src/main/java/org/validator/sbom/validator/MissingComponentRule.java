package org.validator.sbom.validator;

import jakarta.enterprise.context.ApplicationScoped;
import org.cyclonedx.model.Bom;
import org.validator.sbom.model.Violation;

import java.util.*;

/**
 * Checks that every bomRef referenced in the dependency graph
 * is actually declared as a component in the BOM's component list.
 */
@ApplicationScoped
public class MissingComponentRule implements ValidationRule {

    @Override
    public String ruleId() {
        return "MISSING_COMPONENT";
    }

    @Override
    public List<Violation> validate(Bom bom, Map<String, Set<String>> graph, Set<String> declared) {
        List<Violation> violations = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            checkRef(entry.getKey(), declared, violations);
            for (String dep : entry.getValue()) {
                checkRef(dep, declared, violations);
            }
        }
        return violations;
    }

    private void checkRef(String ref, Set<String> declared, List<Violation> violations) {
        if (!declared.contains(ref)) {
            violations.add(new Violation(
                    ruleId(),
                    Violation.Severity.ERROR,
                    "Dependency references undeclared component: " + ref,
                    ref
            ));
        }
    }
}
