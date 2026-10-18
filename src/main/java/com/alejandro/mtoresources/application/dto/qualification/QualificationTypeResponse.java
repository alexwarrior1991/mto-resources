package com.alejandro.mtoresources.application.dto.qualification;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;

import java.util.UUID;

/**
 * Un tipo de cualificacion.
 *
 * <p>Ejemplo, {@code GET /qualification-types/{id}}:</p>
 * <pre>{@code
 * {"id": "…", "code": "LIC-C", "name": "Driving licence C", "kind": "DRIVING_LICENCE", "drivingLicence": "C",
 *  "validityMonths": null, "description": null, "active": true, "version": 0, "audit": {…}}
 * }</pre>
 */
public record QualificationTypeResponse(
        UUID id,
        String code,
        String name,
        QualificationKind kind,
        DrivingLicence drivingLicence,
        Integer validityMonths,
        String description,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
