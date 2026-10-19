package com.alejandro.mtoresources.application.dto.absence;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.AbsenceKind;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una ausencia de una persona. Su clase solo la ve quien lee este servicio: el evento lleva el periodo.
 *
 * <p>Ejemplo, una de {@code GET /people/{personId}/absences}:</p>
 * <pre>{@code
 * {"id": "…", "personId": "…", "kind": "VACATION", "startsOn": "2026-11-02", "endsOn": "2026-11-13",
 *  "notes": "Autumn leave", "version": 0, "audit": {…}}
 * }</pre>
 */
public record PersonAbsenceResponse(
        UUID id,
        UUID personId,
        AbsenceKind kind,
        LocalDate startsOn,
        LocalDate endsOn,
        String notes,
        Long version,
        AuditMetadataResponse audit
) {
}
