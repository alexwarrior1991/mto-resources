package com.alejandro.mtoresources.application.dto.qualification;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una cualificacion de una persona. {@code expired}: su caducidad es anterior a hoy (en el reloj del
 * servicio); una sin caducidad no caduca.
 *
 * <p>Ejemplo, una de {@code GET /people/{personId}/qualifications}:</p>
 * <pre>{@code
 * {"id": "…", "personId": "…", "qualificationType": {"id": "…", "code": "RAIL-DRIVER", "name": "Rail auxiliary vehicle driver",
 *  "kind": "VEHICLE_OPERATION"}, "reference": "RD-2024-0117", "issuedOn": "2024-04-01", "expiresOn": "2027-03-31",
 *  "notes": null, "expired": false, "version": 0, "audit": {…}}
 * }</pre>
 */
public record PersonQualificationResponse(
        UUID id,
        UUID personId,
        QualificationTypeSummary qualificationType,
        String reference,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String notes,
        Boolean expired,
        Long version,
        AuditMetadataResponse audit
) {
}
