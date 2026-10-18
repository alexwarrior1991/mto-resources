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
 *   <li>{@code ON_TRACK}: su via (y su kp); en una bivial, {@code accessPointId} dice donde encarrilo, del
 *       catalogo de {@code mto-configuration} ({@code GET /track-access-points}), o {@code accessPoint} con
 *       texto libre.</li>
 *   <li>{@code ATTACHED}: {@code carrierVehicleId} y {@code attachment} ({@code TOWED} o {@code CARRIED}).</li>
 * </ul>
 *
 * <p>Con {@code accessPointId} se aplican las reglas del acceso ({@code FleetRules.accessPointViolation}, 409
 * {@code VEH-001}): habilitado, en la via de la ubicacion si la dice, que admita biviales si lo es el
 * vehiculo, preparado si el modelo lo exige y que aguante su peso. Un id que no esta en el catalogo es 404
 * {@code TAP-404}. Nunca es obligatorio.</p>
 *
 * <p>Ejemplo, una bivial que desencarrila y se queda en un aparcamiento de carretera:</p>
 * <pre>{@code
 * {"kind": "PARKED", "description": "Car park next to level crossing LC-31", "latitude": 32.123456,
 *  "longitude": 34.876543, "accessPoint": "LC-31", "observedAt": "2026-10-16T03:40:00Z"}
 * }</pre>
 *
 * <p>Y una que encarrila en un acceso del catalogo:</p>
 * <pre>{@code
 * {"kind": "ON_TRACK", "trackId": 3, "kp": 110176.000, "accessPointId": 77, "observedAt": "2026-10-16T23:10:00Z"}
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
        Long accessPointId,
        @Size(max = 500) String description,
        UUID carrierVehicleId,
        VehicleAttachment attachment,
        Instant observedAt
) {
}
