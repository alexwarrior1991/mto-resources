package com.alejandro.mtoresources.application.dto.fleet;

import java.math.BigDecimal;

/**
 * Un punto de acceso a la via, como lo tiene este servicio de {@code mto-configuration}
 * ({@code GET /track-access-points}): lo que ofrece el dialogo de ubicacion y lo que miran las reglas de
 * la flota. Los ids son los de {@code mto-configuration}; {@code kp} va en metros, como alli.
 *
 * <p>Ejemplo:</p>
 * <pre>{@code
 * {"id": 77, "name": "Acceso km 110+176", "trackId": 3, "executionPackageId": 14, "stationId": 12, "kp": 110176.000,
 *  "kind": "PREPARED_ACCESS", "side": "LEFT", "roadRailAccess": true, "maxAxleLoadT": 12.500, "maxGrossWeightT": 40.000,
 *  "latitude": 31.669342, "longitude": 34.574119, "enabled": true}
 * }</pre>
 */
public record TrackAccessPointResponse(
        Long id,
        String name,
        Long trackId,
        Long executionPackageId,
        Long stationId,
        BigDecimal kp,
        String kind,
        String side,
        Boolean roadRailAccess,
        BigDecimal maxAxleLoadT,
        BigDecimal maxGrossWeightT,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean enabled
) {
}
