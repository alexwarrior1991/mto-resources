package com.alejandro.mtoresources.application.dto.repair;

import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de una reparacion de un vehiculo. Nace {@code PLANNED} (por defecto; dice desde cuando,
 * {@code plannedFrom}) u {@code OPEN} (ya esta en el taller: se abre ahora). Sin {@code immobilises}, la
 * inmoviliza: abierta, el vehiculo pasa a {@code UNDER_REPAIR}; planificada o abierta, no entra en un turno
 * de esas fechas.
 *
 * <p>Ejemplo, {@code POST /vehicles/{vehicleId}/repairs}:</p>
 * <pre>{@code
 * {"kind": "SERVICE", "description": "Annual service of the platform", "workshop": "Plasser Haifa",
 *  "plannedFrom": "2026-11-09", "plannedTo": "2026-11-11"}
 * }</pre>
 */
public record VehicleRepairRequest(
        @NotNull RepairKind kind,
        RepairStatus status,
        Boolean immobilises,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 150) String workshop,
        LocalDate plannedFrom,
        LocalDate plannedTo,
        @Min(0) Integer odometerKm,
        @DecimalMin("0") @Digits(integer = 9, fraction = 1) BigDecimal engineHours,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal cost,
        String notes
) {
}
