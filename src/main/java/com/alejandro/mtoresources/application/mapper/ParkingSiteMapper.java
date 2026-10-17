package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteResponse;
import com.alejandro.mtoresources.application.dto.fleet.ParkingSiteSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface ParkingSiteMapper {

    @Mapping(target = "audit", source = "site")
    ParkingSiteResponse toResponse(ParkingSite site);

    ParkingSiteSummary toSummary(ParkingSite site);
}
