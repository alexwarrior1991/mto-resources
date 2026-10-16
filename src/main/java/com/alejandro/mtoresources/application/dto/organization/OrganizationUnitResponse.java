package com.alejandro.mtoresources.application.dto.organization;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.application.dto.fleet.VehicleSummary;
import com.alejandro.mtoresources.application.dto.person.PersonSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Una unidad del organigrama. En un equipo trae ademas sus linemen, su supervisor y su jefe de
 * supervisores (los jefes de las unidades de arriba mas cercanas) y si esta completo.
 *
 * <p>Ejemplo, {@code GET /units/{id}} de un equipo:</p>
 * <pre>{@code
 * {"id": "…", "code": "A", "name": "Team A", "kind": "TEAM", "discipline": "MAINTENANCE",
 *  "head": {"code": "PER-000003", "fullName": "Yossi Cohen", "category": "TEAM_LEADER", …},
 *  "members": [{"person": {…}, "joinedOn": "2026-10-01"}, …], "linemen": 4, "complete": true,
 *  "supervisor": {…}, "executionPackageIds": [4, 5, 6], "defaultVehicle": {"code": "DR-02", …}, "version": 1, …}
 * }</pre>
 */
public record OrganizationUnitResponse(
        UUID id,
        String code,
        String name,
        OrganizationUnitKind kind,
        WorkDiscipline discipline,
        UnitSummary parent,
        PersonSummary head,
        Set<Long> executionPackageIds,
        VehicleSummary defaultVehicle,
        List<TeamMemberResponse> members,
        Integer linemen,
        Boolean complete,
        PersonSummary supervisor,
        PersonSummary chiefSupervisor,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
