package com.alejandro.mtoresources.application.dto.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard immutable HTTP error payload used by every API failure response.
 *
 * <p>Ejemplo, {@code POST /units/{id}/members} en un equipo que ya tiene 6 linemen:</p>
 * <pre>{@code
 * {"timestamp": "2026-10-16T21:40:12Z", "status": 409, "error": "CONFLICT", "errorCode": "UNI-001",
 *  "message": "Team A already has 6 linemen, the most a team can have; take one out first",
 *  "path": "/api/v1/resources/units/…/members", "method": "POST",
 *  "correlationId": "8c3b8c1a-1111-4222-8333-444444444444", "validationErrors": []}
 * }</pre>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String method,
        String errorCode,
        String correlationId,
        List<ValidationError> validationErrors
) {

    public ApiErrorResponse {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}