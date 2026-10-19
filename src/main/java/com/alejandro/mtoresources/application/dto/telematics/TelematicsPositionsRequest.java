package com.alejandro.mtoresources.application.dto.telematics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Un lote de posiciones de los dispositivos GPS, de uno o de varios, en cualquier orden: se aplican por
 * {@code observedAt}.
 *
 * <p>Ejemplo, {@code POST /telematics/positions} (con {@code resources-telematics-write}):</p>
 * <pre>{@code
 * {"positions": [
 *   {"deviceId": "GPS-4471", "observedAt": "2026-10-19T05:42:10Z", "latitude": 32.794046, "longitude": 34.989571}
 * ]}
 * }</pre>
 */
public record TelematicsPositionsRequest(@NotEmpty @Size(max = 500) List<@Valid TelematicsPosition> positions) {
}
