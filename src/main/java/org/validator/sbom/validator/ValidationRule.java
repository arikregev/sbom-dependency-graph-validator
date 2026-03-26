package org.validator.sbom.validator;

import org.cyclonedx.model.Bom;
import org.validator.sbom.model.Violation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ValidationRule {

    String ruleId();

    /**
     * @param bom      the parsed CycloneDX BOM
     * @param graph    adjacency list: bomRef -> set of dependency bomRefs
     * @param declared set of all bomRefs declared in bom.getComponents()
     */
    List<Violation> validate(Bom bom, Map<String, Set<String>> graph, Set<String> declared);
}
