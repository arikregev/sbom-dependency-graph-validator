# SBOM Dependency Graph Validator

A Quarkus REST service that validates [CycloneDX](https://cyclonedx.org/) Software Bill of Materials (SBOM) files by constructing and analyzing their dependency graphs. Validation runs asynchronously — submit an SBOM, get a job ID back immediately, and poll for results.

## Features

- **Cycle detection** — identifies circular dependencies in the graph using DFS with three-color marking
- **Missing component detection** — flags `bom-ref` values referenced in dependencies but not declared in the components list
- **Orphaned component detection** — warns about components declared but never appearing in any dependency relationship
- **Async by design** — validation runs on a Java 21 virtual thread; the HTTP call returns in microseconds
- **No database** — results are held in a configurable Caffeine in-memory cache
- **Extensible rules** — add a new validation rule by dropping in a single CDI bean; zero wiring required

## Tech Stack

| | |
|---|---|
| Runtime | Java 21 |
| Framework | Quarkus 3.31.0 |
| REST | Quarkus REST (RESTEasy Reactive) |
| SBOM parsing | [cyclonedx-core-java](https://github.com/CycloneDX/cyclonedx-core-java) |
| Cache | [Caffeine](https://github.com/ben-manes/caffeine) |
| Build | Maven |

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+

### Run in dev mode

```bash
mvn quarkus:dev
```

The service starts on `http://localhost:8080` with live reload enabled.

### Build and run

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## API Reference

### Submit an SBOM

```
POST /api/v1/sbom
Content-Type: application/json
```

Accepts a CycloneDX JSON SBOM in the request body. Returns `201 Created` immediately with a job ID.

**Response**
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

**Example**
```bash
curl -s -X POST http://localhost:8080/api/v1/sbom \
  -H "Content-Type: application/json" \
  -d @sbom.json
```

---

### Get validation results

```
GET /api/v1/sbom/{jobId}/results
```

Poll this endpoint after submitting. The `status` field transitions: `PENDING → IN_PROGRESS → COMPLETED | FAILED`.

**Response — in progress**
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "IN_PROGRESS",
  "result": null,
  "errorMessage": null,
  "createdAt": "2026-03-26T10:00:00Z"
}
```

**Response — completed (valid SBOM)**
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "COMPLETED",
  "createdAt": "2026-03-26T10:00:00Z",
  "result": {
    "valid": true,
    "componentCount": 12,
    "dependencyEdgeCount": 18,
    "violations": [],
    "completedAt": "2026-03-26T10:00:00.123Z"
  }
}
```

**Response — completed (violations found)**
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "COMPLETED",
  "createdAt": "2026-03-26T10:00:00Z",
  "result": {
    "valid": false,
    "componentCount": 3,
    "dependencyEdgeCount": 2,
    "violations": [
      {
        "ruleId": "CYCLE_DETECTED",
        "severity": "ERROR",
        "message": "Circular dependency detected: lib-a -> lib-b -> lib-a",
        "componentRef": "lib-a"
      }
    ],
    "completedAt": "2026-03-26T10:00:00.123Z"
  }
}
```

**Response — job not found**
```
404 Not Found
```

**Response — failed to parse SBOM**
```json
{
  "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "FAILED",
  "errorMessage": "Unexpected token at position 42",
  "result": null
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| `201` | SBOM accepted, validation enqueued |
| `400` | Empty or missing request body |
| `404` | Job ID not found (expired or never existed) |

## Validation Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| `CYCLE_DETECTED` | ERROR | A circular dependency exists in the graph. The violation message includes the full cycle path. |
| `MISSING_COMPONENT` | ERROR | A `bom-ref` appears in the dependency graph but is not declared in `components`. |
| `ORPHANED_COMPONENT` | WARNING | A component is declared but never appears in any dependency relationship (no incoming or outgoing edges). |

A result is marked `"valid": false` only when at least one `ERROR`-severity violation is present. Warnings alone do not fail validation.

## Configuration

Set in `src/main/resources/application.properties` or via environment variable.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `sbom.validator.cache.ttl-hours` | `SBOM_VALIDATOR_CACHE_TTL_HOURS` | `24` | How long completed job results are retained in memory |
| `quarkus.http.port` | `QUARKUS_HTTP_PORT` | `8080` | HTTP port |

## Architecture

```
resource/SbomResource           HTTP layer — validates input, spawns virtual thread, returns 201
service/ValidationService       Orchestrates parsing → graph build → rule execution
validator/ValidationRule        Interface implemented by each rule bean
validator/CycleDetectionRule    DFS three-color algorithm
validator/MissingComponentRule  Cross-references graph refs against declared components
validator/OrphanedComponentRule Finds declared components absent from all edges
cache/JobCacheService           Caffeine cache wrapper; single source of truth for job state
model/                          ValidationJob · ValidationResult · Violation · ValidationStatus
```

### Async flow

```
POST /api/v1/sbom
  │
  ├─ create ValidationJob (PENDING) in cache
  ├─ start virtual thread → ValidationService.validate()
  └─ return 201 { jobId }

Virtual thread:
  ├─ mark job IN_PROGRESS
  ├─ parse CycloneDX JSON → Bom
  ├─ build adjacency list from Bom.getDependencies()
  ├─ run all ValidationRule beans
  └─ mark job COMPLETED (or FAILED on exception)
```

### Adding a new validation rule

Create an `@ApplicationScoped` CDI bean that implements `ValidationRule`:

```java
@ApplicationScoped
public class MyCustomRule implements ValidationRule {

    @Override
    public String ruleId() {
        return "MY_RULE";
    }

    @Override
    public List<Violation> validate(Bom bom, Map<String, Set<String>> graph, Set<String> declared) {
        // your logic here
        return List.of();
    }
}
```

No registration or wiring needed. `ValidationService` discovers all `ValidationRule` beans via CDI `Instance<ValidationRule>`.

## Running Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=SbomResourceTest

# Single test method
mvn test -Dtest=SbomResourceTest#cyclicSbomProducesErrorViolation
```

## SBOM Format

Only CycloneDX JSON format is supported. The service accepts `Content-Type: application/json` or `application/vnd.cyclonedx+json`.

The dependency graph is built from the `dependencies` array in the SBOM. Each entry's `ref` becomes a graph node; its `dependsOn` list becomes the outgoing edges. The `metadata.component.bom-ref` is included in the declared-component set.

Refer to the [CycloneDX specification](https://cyclonedx.org/specification/overview/) for the full schema.
