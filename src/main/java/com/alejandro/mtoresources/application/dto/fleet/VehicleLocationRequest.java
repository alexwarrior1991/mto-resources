package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Donde esta un vehiculo, registrado a mano ({@code POST /vehicles/{id}/locations}). Sin
 * {@code observedAt}, ahora; en el futuro, 400. Lo que llega con un {@code observedAt} anterior al de la
 * posicion actual queda en el historico y no la mueve.
 *
 * <ul>
 *   <li>{@code PARKED}: un sitio del catalogo, o una via y un kp, o una descripcion o unas coordenadas.</li>
 *   <li>{@code ON_TRACK}: su via (y su kp); en una bivial, {@code accessPoint} dice donde encarrilo.</li>
 *   <li>{@code ATTACHED}: {@code carrierVehicleId} y {@code attachment} ({@code TOWED} o {@code CARRIED}).</li>
 * </ul>
 *
 * <p>Ejemplo, una bivial que desencarrila y se queda en un aparcamiento de carretera:</p>
 * <pre>{@code
 * {"kind": "PARKED", "description": "Car park next to level crossing LC-31", "latitude": 32.123456,
 *  "longitude": 34.876543, "accessPoint": "LC-31", "observedAt": "2026-10-16T03:40:00Z"}
 * }</pre>
 */
public record VehicleLocationRequest(
        @NotNull VehicleLocationKind kind,
        UUID parkingSiteId,
        Long executionPackageId,
        Long trackId,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Size(max = 150) String accessPoint,
        @Size(max = 500) String description,
        UUID carrierVehicleId,
        VehicleAttachment attachment,
        Instant observedAt
) {
}
