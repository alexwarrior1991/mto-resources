package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.absence.PersonAbsenceResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonAbsence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface PersonAbsenceMapper {

    @Mapping(target = "audit", source = "absence")
    @Mapping(target = "personId", source = "absence.person.id")
    PersonAbsenceResponse toResponse(PersonAbsence absence);
}
