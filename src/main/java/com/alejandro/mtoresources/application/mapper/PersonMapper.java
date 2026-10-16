package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.person.PersonResponse;
import com.alejandro.mtoresources.application.dto.person.PersonSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface PersonMapper {

    @Mapping(target = "fullName", expression = "java(person.fullName())")
    @Mapping(target = "audit", source = "person")
    PersonResponse toResponse(Person person);

    @Mapping(target = "fullName", expression = "java(person.fullName())")
    PersonSummary toSummary(Person person);
}
