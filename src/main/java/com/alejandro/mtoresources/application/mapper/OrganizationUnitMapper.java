package com.alejandro.mtoresources.application.mapper;

import com.alejandro.mtoresources.application.dto.organization.TeamMemberResponse;
import com.alejandro.mtoresources.application.dto.organization.UnitSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnit;
import com.alejandro.mtoresources.infrastructure.persistence.entity.TeamMember;
import org.mapstruct.Mapper;

/**
 * Lo simple de una unidad. La respuesta entera ({@code OrganizationUnitResponse}) la monta a mano el
 * servicio, porque lleva lo que se calcula: los linemen, el supervisor y si el equipo esta completo.
 */
@Mapper(config = MapStructCentralConfig.class, uses = PersonMapper.class)
public interface OrganizationUnitMapper {

    UnitSummary toSummary(OrganizationUnit unit);

    TeamMemberResponse toMemberResponse(TeamMember member);
}
