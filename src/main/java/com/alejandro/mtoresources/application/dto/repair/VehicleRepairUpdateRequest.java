package com.alejandro.mtoresources.application.dto.repair;

import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modificacion de una reparacion. El estado no cambia aqui: tiene sus transiciones
 * ({@code POST .../open}, {@code /close}, {@code /cancel}). Las fechas previstas se cambian mientras esta
 * planificada o abierta, y si inmoviliza, solo mientras esta planificada (409 {@code REP-001} si no). Con
 * merge-patch se vacian el taller, el fin previsto, los contadores, el coste y las notas.
 *
 * <p>Ejemplo, {@code PATCH /vehicles/{vehicleId}/repairs/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"plannedTo": "2026-11-14", "cost": 18450.00, "version": 1}
 * }</pre>
 */
public record VehicleRepairUpdateRequest(
        RepairKind kind,
        Boolean immobilises,
        @Size(max = 500) String description,
        @Size(max = 150) String workshop,
        LocalDate plannedFrom,
        LocalDate plannedTo,
        @Min(0) Integer odometerKm,
        @DecimalMin("0") @Digits(integer = 9, fraction = 1) BigDecimal engineHours,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal cost,
        String notes,
        Long version
) {
}
