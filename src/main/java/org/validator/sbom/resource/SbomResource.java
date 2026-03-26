package org.validator.sbom.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.validator.sbom.cache.JobCacheService;
import org.validator.sbom.service.ValidationService;

import java.util.Map;
import java.util.UUID;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class SbomResource {

    @Inject
    ValidationService validationService;

    @Inject
    JobCacheService jobCacheService;

    /**
     * Accepts a CycloneDX JSON SBOM, enqueues async validation,
     * and immediately returns 201 with the generated job ID.
     */
    @POST
    @Path("/sbom")
    @Consumes({MediaType.APPLICATION_JSON, "application/vnd.cyclonedx+json"})
    public Response submitSbom(String sbomBody) {
        if (sbomBody == null || sbomBody.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Request body must not be empty")).build();
        }

        String jobId = UUID.randomUUID().toString();
        jobCacheService.createJob(jobId);

        // Run validation on a virtual thread — returns immediately to the caller
        Thread.ofVirtual().start(() -> validationService.validate(jobId, sbomBody));

        return Response.status(201)
                .entity(Map.of("jobId", jobId))
                .build();
    }

    /**
     * Returns the current status and results (if complete) for a validation job.
     */
    @GET
    @Path("/sbom/{jobId}/results")
    public Response getResults(@PathParam("jobId") String jobId) {
        return jobCacheService.getJob(jobId)
                .map(job -> Response.ok(job).build())
                .orElseGet(() -> Response.status(404)
                        .entity(Map.of("error", "Job not found: " + jobId))
                        .build());
    }
}
