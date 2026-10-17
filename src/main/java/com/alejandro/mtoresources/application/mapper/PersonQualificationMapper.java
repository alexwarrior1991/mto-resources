package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.qualification.PersonQualificationResponse;
import com.alejandro.mtoresources.domain.model.ExpiryRules;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonQualification;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;

/** {@code expired} depende de hoy, que lo da el reloj del servicio: llega como contexto. */
@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, QualificationTypeMapper.class}, imports = ExpiryRules.class)
public interface PersonQualificationMapper {

    @Mapping(target = "audit", source = "qualification")
    @Mapping(target = "personId", source = "qualification.person.id")
    @Mapping(target = "expired", expression = "java(ExpiryRules.isExpired(qualification.getExpiresOn(), today))")
    PersonQualificationResponse toResponse(PersonQualification qualification, @Context LocalDate today);
}
