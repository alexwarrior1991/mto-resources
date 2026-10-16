package com.alejandro.mtoresources.application.dto.operation;

/**
 * Lo que publico una republicacion: cuantas personas, equipos y vehiculos, y de que EP si se pidio uno.
 *
 * <p>Ejemplo, el {@code result} de {@code GET /actuator/republish/{id}} al terminar:</p>
 * <pre>{@code
 * {"executionPackageId": null, "people": 42, "teams": 6, "vehicles": 11}
 * }</pre>
 */
public record RepublishResult(Long executionPackageId, int people, int teams, int vehicles) {
}
