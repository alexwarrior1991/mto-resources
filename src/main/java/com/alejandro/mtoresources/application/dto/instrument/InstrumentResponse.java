package com.alejandro.mtoresources.application.dto.instrument;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un instrumento. {@code calibrationExpired}: la caducidad de su calibracion es anterior a hoy (en el
 * reloj del servicio); uno sin calibracion no caduca.
 *
 * <p>Ejemplo, {@code GET /instruments/{id}}:</p>
 * <pre>{@code
 * {"id": "…", "code": "PT-012", "name": "Earthing pole 25 kV", "kind": "EARTHING_POLE", "serialNumber": "EP25-88412",
 *  "calibratedOn": "2026-04-01", "calibrationExpiresOn": "2027-03-31", "calibrationExpired": false, "notes": null,
 *  "active": true, "version": 0, "audit": {…}}
 * }</pre>
 */
public record InstrumentResponse(
        UUID id,
        String code,
        String name,
        InstrumentKind kind,
        String serialNumber,
        LocalDate calibratedOn,
        LocalDate calibrationExpiresOn,
        Boolean calibrationExpired,
        String notes,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
