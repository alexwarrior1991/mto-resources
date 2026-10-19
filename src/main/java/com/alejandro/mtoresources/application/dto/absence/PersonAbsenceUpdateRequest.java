package com.alejandro.mtoresources.application.dto.absence;

import com.alejandro.mtoresources.infrastructure.persistence.entity.AbsenceKind;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Modificacion de una ausencia (alargarla, acortarla, cambiar su clase). Con merge-patch solo se vacian las
 * notas; las fechas y la clase se cambian, no se vacian.
 *
 * <p>Ejemplo, {@code PATCH /people/{personId}/absences/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"endsOn": "2026-11-20", "version": 0}
 * }</pre>
 */
public record PersonAbsenceUpdateRequest(
        AbsenceKind kind,
        LocalDate startsOn,
        LocalDate endsOn,
        @Size(max = 500) String notes,
        Long version
) {
}
