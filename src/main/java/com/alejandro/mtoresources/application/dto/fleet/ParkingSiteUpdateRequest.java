package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Modificacion de un sitio de aparcamiento. {@code active=false} lo retira del catalogo; lo que ya se
 * registro alli se queda.
 *
 * <p>Ejemplo, {@code PATCH /parking-sites/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"roadAccess": false, "address": null, "version": 0}
 * }</pre>
 */
public record ParkingSiteUpdateRequest(
        @Size(max = 150) String name,
        ParkingSiteKind kind,
        Long executionPackageId,
        Long stationId,
        Long trackId,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        Boolean railAccess,
        Boolean roadAccess,
        @Size(max = 255) String address,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        String notes,
        Boolean active,
        Long version
) {

    /** Retirar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
