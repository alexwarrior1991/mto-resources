package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una ubicacion de un vehiculo: una fila de su historico o su posicion actual. {@code source} dice de
 * donde salio ({@code MANUAL}, {@code SHIFT}, {@code FIELD}, {@code TELEMATICS}) y
 * {@code sourceReference}, que la provoco ({@code mto-maintenance:shift:<id>}).
 */
public record VehicleLocationResponse(
        UUID id,
        VehicleLocationKind kind,
        ParkingSiteSummary parkingSite,
        Long executionPackageId,
        Long trackId,
        BigDecimal kp,
        BigDecimal latitude,
        BigDecimal longitude,
        String accessPoint,
        String description,
        VehicleSummary carrier,
        VehicleAttachment attachment,
        VehicleLocationSource source,
        String sourceReference,
        Instant observedAt,
        Instant recordedAt,
        String recordedBy
) {
}
