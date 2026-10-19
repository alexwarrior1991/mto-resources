package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOwner;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modificacion de un vehiculo. El codigo no cambia; el modelo si (un vehiculo mal dado de alta). Pasarlo
 * a {@code UNDER_REPAIR} o {@code OUT_OF_SERVICE} lo deja no disponible para los turnos.
 * {@code active=false} lo retira, como el {@code DELETE}, y pide tambien {@code resources-delete}.
 *
 * <p>Ejemplo, {@code PATCH /vehicles/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"operationalStatus": "UNDER_REPAIR", "statusReason": "Hydraulic leak on the platform", "version": 5}
 * }</pre>
 */
public record VehicleUpdateRequest(
        @Size(max = 150) String name,
        UUID typeId,
        @Size(max = 20) String registrationPlate,
        @Size(max = 30) String railVehicleNumber,
        @Size(max = 60) String serialNumber,
        @Min(1900) @Max(2200) Integer manufactureYear,
        VehicleOwner owner,
        @Size(max = 150) String ownerCompany,
        VehicleOperationalStatus operationalStatus,
        @Size(max = 500) String statusReason,
        @Min(0) Integer odometerKm,
        @DecimalMin("0") @Digits(integer = 9, fraction = 1) BigDecimal engineHours,
        String notes,
        Boolean active,
        Long version,
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_.:-]+", message = "letters, digits, '.', ':', '-' and '_' only") String telematicsDeviceId
) {

    /** Retirar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
