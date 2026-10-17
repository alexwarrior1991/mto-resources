package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeResponse;
import com.alejandro.mtoresources.application.dto.qualification.QualificationTypeSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface QualificationTypeMapper {

    @Mapping(target = "audit", source = "type")
    QualificationTypeResponse toResponse(QualificationType type);

    QualificationTypeSummary toSummary(QualificationType type);
}
