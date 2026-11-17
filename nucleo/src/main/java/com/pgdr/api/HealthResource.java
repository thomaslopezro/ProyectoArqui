package com.pgdr.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(
            "{\"status\":\"UP\",\"service\":\"plataforma-desastres\",\"version\":\"0.1.0\"}"
        ).build();
    }
}
