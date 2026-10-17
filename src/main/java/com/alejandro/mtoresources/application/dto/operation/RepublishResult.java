package com.alejandro.mtoresources.application.dto.operation;

/**
 * Lo que publico una republicacion: cuantas personas, equipos, vehiculos e instrumentos, y de que EP si se
 * pidio uno. Los instrumentos no son de ningun EP: solo salen al republicarlo todo.
 *
 * <p>Ejemplo, el {@code result} de {@code GET /actuator/republish/{id}} al terminar:</p>
 * <pre>{@code
 * {"executionPackageId": null, "people": 42, "teams": 6, "vehicles": 11, "instruments": 23}
 * }</pre>
 */
public record RepublishResult(Long executionPackageId, int people, int teams, int vehicles, int instruments) {
}
