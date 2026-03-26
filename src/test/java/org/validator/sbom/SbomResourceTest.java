package org.validator.sbom;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SbomResourceTest {

    private static final String VALID_SBOM = """
            {
              "bomFormat": "CycloneDX",
              "specVersion": "1.5",
              "version": 1,
              "metadata": {
                "component": {
                  "type": "application",
                  "name": "my-app",
                  "version": "1.0.0",
                  "bom-ref": "my-app"
                }
              },
              "components": [
                {
                  "type": "library",
                  "name": "lib-a",
                  "version": "2.0.0",
                  "bom-ref": "lib-a"
                },
                {
                  "type": "library",
                  "name": "lib-b",
                  "version": "1.3.0",
                  "bom-ref": "lib-b"
                }
              ],
              "dependencies": [
                {
                  "ref": "my-app",
                  "dependsOn": ["lib-a", "lib-b"]
                },
                {
                  "ref": "lib-a",
                  "dependsOn": []
                },
                {
                  "ref": "lib-b",
                  "dependsOn": []
                }
              ]
            }
            """;

    private static final String CYCLIC_SBOM = """
            {
              "bomFormat": "CycloneDX",
              "specVersion": "1.5",
              "version": 1,
              "components": [
                {"type": "library", "name": "lib-a", "version": "1.0", "bom-ref": "lib-a"},
                {"type": "library", "name": "lib-b", "version": "1.0", "bom-ref": "lib-b"}
              ],
              "dependencies": [
                {"ref": "lib-a", "dependsOn": ["lib-b"]},
                {"ref": "lib-b", "dependsOn": ["lib-a"]}
              ]
            }
            """;

    @Test
    void submitValidSbomReturns201WithJobId() {
        given()
                .contentType(ContentType.JSON)
                .body(VALID_SBOM)
                .when().post("/api/v1/sbom")
                .then()
                .statusCode(201)
                .body("jobId", notNullValue());
    }

    @Test
    void emptyBodyReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("")
                .when().post("/api/v1/sbom")
                .then()
                .statusCode(400);
    }

    @Test
    void unknownJobIdReturns404() {
        given()
                .when().get("/api/v1/sbom/nonexistent-id/results")
                .then()
                .statusCode(404);
    }

    @Test
    void resultsEventuallyAvailableAfterSubmission() throws InterruptedException {
        String jobId = given()
                .contentType(ContentType.JSON)
                .body(VALID_SBOM)
                .when().post("/api/v1/sbom")
                .then()
                .statusCode(201)
                .extract().path("jobId");

        // Poll until the job leaves PENDING/IN_PROGRESS (virtual thread is fast)
        for (int i = 0; i < 20; i++) {
            String status = given()
                    .when().get("/api/v1/sbom/" + jobId + "/results")
                    .then().statusCode(200)
                    .extract().path("status");

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) break;
            Thread.sleep(100);
        }

        given()
                .when().get("/api/v1/sbom/" + jobId + "/results")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("result.valid", equalTo(true));
    }

    @Test
    void cyclicSbomProducesErrorViolation() throws InterruptedException {
        String jobId = given()
                .contentType(ContentType.JSON)
                .body(CYCLIC_SBOM)
                .when().post("/api/v1/sbom")
                .then()
                .statusCode(201)
                .extract().path("jobId");

        for (int i = 0; i < 20; i++) {
            String status = given()
                    .when().get("/api/v1/sbom/" + jobId + "/results")
                    .then().statusCode(200)
                    .extract().path("status");

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) break;
            Thread.sleep(100);
        }

        given()
                .when().get("/api/v1/sbom/" + jobId + "/results")
                .then()
                .statusCode(200)
                .body("result.valid", equalTo(false))
                .body("result.violations.ruleId", hasItem("CYCLE_DETECTED"));
    }
}
