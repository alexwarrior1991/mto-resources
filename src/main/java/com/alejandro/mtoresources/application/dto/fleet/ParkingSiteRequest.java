package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta de un sitio donde se sabe que se puede aparcar. Uno con acceso por via ({@code railAccess}) lleva
 * su via; el EP, la estacion y la via son ids de {@code mto-configuration}, sin comprobar aqui.
 *
 * <p>Ejemplo, {@code POST /parking-sites} de un apartadero:</p>
 * <pre>{@code
 * {"code": "SID-KP12", "name": "Siding at kp 12.4", "kind": "SIDING", "executionPackageId": 4, "trackId": 14,
 *  "kp": 12.400, "railAccess": true, "roadAccess": true}
 * }</pre>
 */
public record ParkingSiteRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "letters, digits, '.', '-' and '_' only") String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull ParkingSiteKind kind,
        Long executionPackageId,
        Long stationId,
        Long trackId,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        @NotNull Boolean railAccess,
        @NotNull Boolean roadAccess,
        @Size(max = 255) String address,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        String notes
) {
}
