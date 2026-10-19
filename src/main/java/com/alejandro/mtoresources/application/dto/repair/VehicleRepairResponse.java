package com.alejandro.mtoresources.application.dto.repair;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una reparacion de un vehiculo.
 *
 * <p>Ejemplo, una de {@code GET /vehicles/{vehicleId}/repairs}:</p>
 * <pre>{@code
 * {"id": "…", "code": "REP-000012", "vehicleId": "…", "kind": "BREAKDOWN", "status": "OPEN", "immobilises": true,
 *  "description": "Hydraulic leak on the platform", "workshop": null, "plannedFrom": null, "plannedTo": "2026-10-24",
 *  "openedAt": "2026-10-19T06:10:00Z", "closedAt": null, "odometerKm": 48210, "engineHours": 3120.5, "cost": null,
 *  "notes": null, "version": 0, "audit": {…}}
 * }</pre>
 */
public record VehicleRepairResponse(
        UUID id,
        String code,
        UUID vehicleId,
        RepairKind kind,
        RepairStatus status,
        Boolean immobilises,
        String description,
        String workshop,
        LocalDate plannedFrom,
        LocalDate plannedTo,
        Instant openedAt,
        Instant closedAt,
        Integer odometerKm,
        BigDecimal engineHours,
        BigDecimal cost,
        String notes,
        Long version,
        AuditMetadataResponse audit
) {
}
