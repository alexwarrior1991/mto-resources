package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;

/**
 * Una fila del tablero de la flota ({@code GET /vehicles/positions}): el vehiculo y donde esta. Uno
 * {@code ATTACHED} trae ademas {@code effective}, la posicion de su portador, que es donde esta de verdad.
 *
 * <p>Ejemplo:</p>
 * <pre>{@code
 * {"vehicle": {"code": "TR-01", "mobility": "RAIL_ONLY", "category": "RAIL_WAGON", …},
 *  "operationalStatus": "OPERATIONAL", "available": true,
 *  "position": {"kind": "ATTACHED", "carrier": {"code": "DR-02", …}, "attachment": "TOWED", …},
 *  "effective": {"kind": "ON_TRACK", "trackId": 14, "kp": 12.400, "source": "SHIFT", …}}
 * }</pre>
 */
public record FleetPositionResponse(
        VehicleSummary vehicle,
        VehicleOperationalStatus operationalStatus,
        Boolean available,
        VehicleLocationResponse position,
        VehicleLocationResponse effective
) {
}
