package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointResponse;
import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TrackAccessPoint;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface TrackAccessPointMapper {

    TrackAccessPointResponse toResponse(TrackAccessPoint accessPoint);

    TrackAccessPointSummary toSummary(TrackAccessPoint accessPoint);
}
