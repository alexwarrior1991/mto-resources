package com.alejandro.mtoresources.application.dto.instrument;

import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de un instrumento. Sin {@code calibrationExpiresOn}, no tiene calibracion que caduque.
 *
 * <p>Ejemplo, {@code POST /instruments}:</p>
 * <pre>{@code
 * {"code": "PT-012", "name": "Earthing pole 25 kV", "kind": "EARTHING_POLE", "serialNumber": "EP25-88412",
 *  "calibratedOn": "2026-04-01", "calibrationExpiresOn": "2027-03-31"}
 * }</pre>
 */
public record InstrumentRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "letters, digits, '.', '-' and '_' only") String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull InstrumentKind kind,
        @Size(max = 60) String serialNumber,
        LocalDate calibratedOn,
        LocalDate calibrationExpiresOn,
        @Size(max = 500) String notes
) {
}
