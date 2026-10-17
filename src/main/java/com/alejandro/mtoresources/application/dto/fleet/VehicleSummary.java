package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;

import java.util.UUID;

/** Un vehiculo dentro de otra respuesta: el vehiculo por defecto de un equipo, el portador de otro. */
public record VehicleSummary(UUID id, String code, String name, VehicleMobility mobility, VehicleCategory category) {
}
