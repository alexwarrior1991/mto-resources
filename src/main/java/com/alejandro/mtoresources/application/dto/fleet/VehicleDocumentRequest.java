package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleDocumentKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de un documento de un vehiculo: uno de cada tipo, y siempre con su caducidad.
 *
 * <p>Ejemplo, {@code POST /vehicles/{vehicleId}/documents}:</p>
 * <pre>{@code
 * {"kind": "ROAD_INSPECTION", "reference": "ITV-2026-118", "issuedOn": "2026-02-28", "expiresOn": "2027-02-28"}
 * }</pre>
 */
public record VehicleDocumentRequest(
        @NotNull VehicleDocumentKind kind,
        @Size(max = 60) String reference,
        LocalDate issuedOn,
        @NotNull LocalDate expiresOn,
        @Size(max = 500) String notes
) {
}
