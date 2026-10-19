package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOperationalStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleOwner;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alta de un vehiculo de la flota. Su modelo decide lo que exige: la matricula si va por carretera, el
 * numero de vehiculo ferroviario si su modelo puede ir por via abierta en modo transporte.
 *
 * <p>{@code telematicsDeviceId}: su dispositivo GPS, por el que llegan sus posiciones
 * ({@code POST /telematics/positions}); unico, y nulo sin GPS.</p>
 *
 * <p>Ejemplo, {@code POST /vehicles}:</p>
 * <pre>{@code
 * {"code": "RR-07", "name": "Unimog 7", "typeId": "…", "registrationPlate": "12-345-67",
 *  "serialNumber": "WDB4374…", "manufactureYear": 2021, "owner": "LEASED", "ownerCompany": "Rail Rent Ltd."}
 * }</pre>
 */
public record VehicleRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "letters, digits, '.', '-' and '_' only") String code,
        @Size(max = 150) String name,
        @NotNull UUID typeId,
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
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_.:-]+", message = "letters, digits, '.', ':', '-' and '_' only") String telematicsDeviceId
) {
}
