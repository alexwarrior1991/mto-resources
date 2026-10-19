package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.repair.VehicleRepairResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleRepair;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface VehicleRepairMapper {

    @Mapping(target = "audit", source = "repair")
    @Mapping(target = "vehicleId", source = "repair.vehicle.id")
    VehicleRepairResponse toResponse(VehicleRepair repair);
}
