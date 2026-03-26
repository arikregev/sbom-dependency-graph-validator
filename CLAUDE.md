# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Quarkus REST service that accepts CycloneDX JSON SBOMs via HTTP, builds a dependency graph, and validates it asynchronously. Results are stored in a Caffeine cache (no database).

**Tech stack:** Java 21 · Maven · Quarkus 3.31.0 · Quarkus REST (RESTEasy Reactive) · cyclonedx-core-java · Caffeine

## Commands

```bash
# Dev mode (live reload)
mvn quarkus:dev

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=SbomResourceTest

# Production build
mvn package

# Run the built jar
java -jar target/quarkus-app/quarkus-run.jar
```

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/sbom` | Submit a CycloneDX JSON SBOM. Returns `201` with `{"jobId": "<uuid>"}` |
| `GET`  | `/api/v1/sbom/{jobId}/results` | Poll for validation status and results |

The POST endpoint returns immediately (201). Validation runs on a Java 21 virtual thread. Poll the results endpoint; `status` transitions `PENDING → IN_PROGRESS → COMPLETED | FAILED`.

## Architecture

```
resource/SbomResource         — REST endpoints, spawns virtual thread for async validation
service/ValidationService     — parses BOM, builds graph, runs all ValidationRule beans
validator/ValidationRule      — interface; CDI injects all implementations into ValidationService
validator/CycleDetectionRule  — DFS three-color cycle detection (ERROR)
validator/MissingComponentRule — refs in graph must exist in components list (ERROR)
validator/OrphanedComponentRule — declared components absent from all dependency edges (WARNING)
cache/JobCacheService         — Caffeine cache wrapper; TTL via sbom.validator.cache.ttl-hours
model/                        — ValidationJob, ValidationResult, Violation, ValidationStatus
```

**Adding a new validation rule:** create a `@ApplicationScoped` bean that implements `ValidationRule`. It is automatically picked up via CDI `Instance<ValidationRule>` injection in `ValidationService` — no wiring needed.

**Graph representation:** adjacency list `Map<String, Set<String>>` where keys/values are CycloneDX `bom-ref` strings, built from `Bom.getDependencies()`.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `sbom.validator.cache.ttl-hours` | `24` | How long completed jobs are retained in memory |

## SBOM Format Notes

- CycloneDX spec: https://cyclonedx.org/specification/overview/
- Only JSON format is currently supported (`application/json` or `application/vnd.cyclonedx+json`)
- The metadata component's `bom-ref` is included in the declared-components set for dependency resolution
