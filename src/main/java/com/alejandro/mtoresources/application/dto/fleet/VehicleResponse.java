package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOwner;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un vehiculo, con el resumen de su modelo y donde esta ahora ({@code position}, ausente si nunca se ha
 * registrado su ubicacion).
 *
 * <p>Ejemplo, {@code GET /vehicles/{id}}:</p>
 * <pre>{@code
 * {"id": "…", "code": "DR-02", "name": "Tower car 2", "type": {"code": "DIC-30", "mobility": "RAIL_ONLY", …},
 *  "railVehicleNumber": "99 83 9128 002-4", "operationalStatus": "OPERATIONAL", "available": true,
 *  "position": {"kind": "PARKED", "trackId": 14, "kp": 12.400, "source": "SHIFT", "observedAt": "…", …},
 *  "active": true, "version": 3, "audit": {…}}
 * }</pre>
 */
public record VehicleResponse(
        UUID id,
        String code,
        String name,
        VehicleTypeSummary type,
        String registrationPlate,
        String railVehicleNumber,
        String serialNumber,
        Integer manufactureYear,
        VehicleOwner owner,
        String ownerCompany,
        VehicleOperationalStatus operationalStatus,
        String statusReason,
        Boolean available,
        Integer odometerKm,
        BigDecimal engineHours,
        String notes,
        String telematicsDeviceId,
        VehicleLocationResponse position,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
