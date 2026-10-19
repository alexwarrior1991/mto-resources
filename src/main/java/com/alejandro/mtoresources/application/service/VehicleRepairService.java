package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairRequest;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairResponse;
import com.alejandro.mtoresources.application.dto.repair.VehicleRepairUpdateRequest;

import java.util.List;
import java.util.UUID;

/**
 * El taller de un vehiculo. Una reparacion que inmoviliza manda en su estado: abrirla lo pone
 * {@code UNDER_REPAIR}, y cerrar o cancelar la ultima abierta lo devuelve a {@code OPERATIONAL} si sigue
 * {@code UNDER_REPAIR}. Toda escritura publica el {@code vehicle.updated} del vehiculo, con su
 * {@code unavailable} entero. Nada se borra: se cancela.
 */
public interface VehicleRepairService {

    /** Todas, la mas reciente primero. */
    List<VehicleRepairResponse> findAll(UUID vehicleId);

    VehicleRepairResponse create(UUID vehicleId, VehicleRepairRequest request);

    VehicleRepairResponse patch(UUID vehicleId, UUID repairId, MergePatch<VehicleRepairUpdateRequest> patch);

    /** {@code PLANNED -> OPEN}: el vehiculo entra en el taller ahora. */
    VehicleRepairResponse open(UUID vehicleId, UUID repairId);

    /** {@code OPEN -> CLOSED}: sale del taller ahora. */
    VehicleRepairResponse close(UUID vehicleId, UUID repairId);

    /** {@code PLANNED} u {@code OPEN} {@code -> CANCELLED}. */
    VehicleRepairResponse cancel(UUID vehicleId, UUID repairId);
}
