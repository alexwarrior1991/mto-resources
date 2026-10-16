package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.RailGearType;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;

import java.util.Set;
import java.util.UUID;

/** Lo que un vehiculo cuenta de su modelo: por donde va, que es, cuantos caben y que sabe hacer. */
public record VehicleTypeSummary(
        UUID id,
        String code,
        String name,
        VehicleMobility mobility,
        VehicleCategory category,
        Boolean selfPropelled,
        Integer seats,
        RailGearType railGearType,
        Boolean transportModeAuthorised,
        Set<VehicleCapability> capabilities
) {
}
