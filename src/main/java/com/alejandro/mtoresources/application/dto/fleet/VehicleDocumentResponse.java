package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un documento de un vehiculo. {@code expired}: su caducidad es anterior a hoy (en el reloj del servicio).
 *
 * <p>Ejemplo, uno de {@code GET /vehicles/{vehicleId}/documents}:</p>
 * <pre>{@code
 * {"id": "…", "vehicleId": "…", "kind": "ROAD_INSPECTION", "reference": "ITV-2026-118", "issuedOn": "2026-02-28",
 *  "expiresOn": "2027-02-28", "notes": null, "expired": false, "version": 0, "audit": {…}}
 * }</pre>
 */
public record VehicleDocumentResponse(
        UUID id,
        UUID vehicleId,
        VehicleDocumentKind kind,
        String reference,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String notes,
        Boolean expired,
        Long version,
        AuditMetadataResponse audit
) {
}
