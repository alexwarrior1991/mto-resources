package com.alejandro.mtoresources.application.dto.absence;

import com.alejandro.mtoresources.infrastructure.persistence.entity.AbsenceKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de una ausencia de una persona, con las dos fechas incluidas. No se solapa con otra suya (409
 * {@code ABS-001}); un fin anterior al inicio es 400.
 *
 * <p>Ejemplo, {@code POST /people/{personId}/absences}:</p>
 * <pre>{@code
 * {"kind": "VACATION", "startsOn": "2026-11-02", "endsOn": "2026-11-13", "notes": "Autumn leave"}
 * }</pre>
 */
public record PersonAbsenceRequest(
        @NotNull AbsenceKind kind,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @Size(max = 500) String notes
) {
}
