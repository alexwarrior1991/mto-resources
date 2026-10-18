package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleTypeSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, QualificationTypeMapper.class})
public interface VehicleTypeMapper {

    @Mapping(target = "audit", source = "type")
    @Mapping(target = "capabilities", expression = "java(capabilities(type))")
    VehicleTypeResponse toResponse(VehicleType type);

    @Mapping(target = "capabilities", expression = "java(capabilities(type))")
    VehicleTypeSummary toSummary(VehicleType type);

    /** Ordenadas como el enumerado: el JSON sale igual cada vez. */
    default Set<VehicleCapability> capabilities(VehicleType type) {
        return type.getCapabilities().isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(type.getCapabilities()));
    }
}
