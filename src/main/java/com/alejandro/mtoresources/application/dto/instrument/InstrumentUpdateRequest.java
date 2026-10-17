package com.alejandro.mtoresources.application.dto.instrument;

import com.alejandro.mtoresources.infrastructure.persistence.entity.InstrumentKind;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Modificacion de un instrumento: recalibrarlo es cambiar sus fechas. El codigo no cambia. Se vacian
 * {@code serialNumber}, {@code calibratedOn}, {@code calibrationExpiresOn} y {@code notes};
 * {@code active=false} lo retira (pide ademas {@code resources-delete}).
 *
 * <p>Ejemplo, {@code PATCH /instruments/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"calibratedOn": "2027-03-25", "calibrationExpiresOn": "2028-03-24", "version": 2}
 * }</pre>
 */
public record InstrumentUpdateRequest(
        @Size(max = 120) String name,
        InstrumentKind kind,
        @Size(max = 60) String serialNumber,
        LocalDate calibratedOn,
        LocalDate calibrationExpiresOn,
        @Size(max = 500) String notes,
        Boolean active,
        Long version
) {

    /** Retirar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
