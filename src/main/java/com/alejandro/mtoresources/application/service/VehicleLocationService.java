package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.fleet.FleetPositionResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/**
 * Donde esta cada vehiculo: el historico append-only y la posicion actual, que es el ultimo observado.
 * Hoy se registra a mano y desde los turnos de {@code mto-maintenance}; manana desde el dispositivo de
 * campo y la telematica, por la misma puerta ({@link #record}).
 */
public interface VehicleLocationService {

    /** Una ubicacion registrada a mano. */
    VehicleLocationResponse recordManual(UUID vehicleId, VehicleLocationRequest request);

    /**
     * Una ubicacion de cualquier origen. {@code sourceReference} dice que la provoco; con la misma
     * referencia y el mismo {@code observedAt} no se apunta dos veces. Devuelve {@code null} si ya
     * estaba.
     */
    VehicleLocationResponse record(UUID vehicleId, VehicleLocationRequest request, VehicleLocationSource source,
                                   String sourceReference);

    PageResponse<VehicleLocationResponse> history(UUID vehicleId, Instant from, Instant to, Pageable pageable);

    /**
     * El tablero de la flota: cada vehiculo activo con posicion conocida. Los filtros miran donde esta de
     * verdad: uno remolcado o transportado sale donde esta su portador.
     */
    PageResponse<FleetPositionResponse> positions(Long executionPackageId, Long trackId, VehicleLocationKind kind, Pageable pageable);
}
