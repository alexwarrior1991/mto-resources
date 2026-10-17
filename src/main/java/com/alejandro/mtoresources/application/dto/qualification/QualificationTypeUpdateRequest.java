package com.alejandro.mtoresources.application.dto.qualification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Modificacion de un tipo de cualificacion. El codigo no cambia: es lo que viaja en los eventos. Se
 * vacian {@code drivingLicence}, {@code validityMonths} y {@code description}; pasar a otro tipo que no
 * sea un permiso de conducir quita el permiso sin decirlo. {@code active=false} lo retira (pide
 * ademas {@code resources-delete}): las que ya tiene cada persona se quedan, pero no se dan nuevas.
 *
 * <p>Ejemplo, {@code PATCH /qualification-types/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"validityMonths": 36, "description": null, "version": 0}
 * }</pre>
 */
public record QualificationTypeUpdateRequest(
        @Size(max = 120) String name,
        QualificationKind kind,
        DrivingLicence drivingLicence,
        @Min(1) Integer validityMonths,
        @Size(max = 500) String description,
        Boolean active,
        Long version
) {

    /** Retirar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
