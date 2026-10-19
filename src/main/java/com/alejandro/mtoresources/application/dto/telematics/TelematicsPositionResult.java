package com.alejandro.mtoresources.application.dto.telematics;

import java.time.Instant;

/** El resultado de una posicion del lote: su vehiculo (si el dispositivo es de alguno) y, si se ignoro, por que. */
public record TelematicsPositionResult(String deviceId, Instant observedAt, String vehicleCode, TelematicsOutcome outcome, String reason) {
}
