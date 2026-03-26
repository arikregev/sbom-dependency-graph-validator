package org.validator.sbom.validator;

import jakarta.enterprise.context.ApplicationScoped;
import org.cyclonedx.model.Bom;
import org.validator.sbom.model.Violation;

import java.util.*;

/**
 * Detects cycles in the dependency graph using DFS with three-color marking.
 * A cycle indicates a circular dependency, which is always an error.
 */
@ApplicationScoped
public class CycleDetectionRule implements ValidationRule {

    @Override
    public String ruleId() {
        return "CYCLE_DETECTED";
    }

    @Override
    public List<Violation> validate(Bom bom, Map<String, Set<String>> graph, Set<String> declared) {
        // 0 = unvisited, 1 = in current DFS stack, 2 = fully processed
        Map<String, Integer> state = new HashMap<>();
        List<Violation> violations = new ArrayList<>();

        for (String node : graph.keySet()) {
            if (state.getOrDefault(node, 0) == 0) {
                dfs(node, graph, state, violations, new ArrayDeque<>());
            }
        }
        return violations;
    }

    private void dfs(String node, Map<String, Set<String>> graph,
                     Map<String, Integer> state, List<Violation> violations,
                     Deque<String> path) {
        state.put(node, 1);
        path.push(node);

        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            int neighborState = state.getOrDefault(neighbor, 0);
            if (neighborState == 1) {
                // Back edge found — cycle
                List<String> cycle = buildCyclePath(path, neighbor);
                violations.add(new Violation(
                        ruleId(),
                        Violation.Severity.ERROR,
                        "Circular dependency detected: " + String.join(" -> ", cycle),
                        node
                ));
            } else if (neighborState == 0) {
                dfs(neighbor, graph, state, violations, path);
            }
        }

        path.pop();
        state.put(node, 2);
    }

    private List<String> buildCyclePath(Deque<String> path, String cycleStart) {
        List<String> stack = new ArrayList<>(path);
        Collections.reverse(stack);
        int idx = stack.indexOf(cycleStart);
        List<String> cycle = new ArrayList<>(stack.subList(idx, stack.size()));
        cycle.add(cycleStart); // close the loop
        return cycle;
    }
}
