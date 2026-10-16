package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtoresources.application.dto.common.MergePatch;
import com.alejandro.mtoresources.application.dto.common.PageResponse;
import com.alejandro.mtoresources.application.dto.organization.OrganizationTreeNode;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitRequest;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitResponse;
import com.alejandro.mtoresources.application.dto.organization.OrganizationUnitUpdateRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberLeaveRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberRequest;
import com.alejandro.mtoresources.application.dto.organization.TeamMemberResponse;
import com.alejandro.mtoresources.application.dto.organization.TeamMembersRequest;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * El organigrama: proyecto, departamentos, jefaturas de supervisores, supervisiones y equipos. Un
 * equipo tiene siempre su jefe y de 2 a 6 linemen ({@code CrewComposition}).
 */
public interface OrganizationUnitService {

    OrganizationUnitResponse create(OrganizationUnitRequest request);

    OrganizationUnitResponse update(UUID id, OrganizationUnitUpdateRequest request);

    OrganizationUnitResponse patch(UUID id, MergePatch<OrganizationUnitUpdateRequest> patch);

    OrganizationUnitResponse findById(UUID id);

    PageResponse<OrganizationUnitResponse> search(String search, OrganizationUnitKind kind, WorkDiscipline discipline, UUID parentId,
                                                  Long executionPackageId, UUID headPersonId, Boolean active, Pageable pageable);

    /** El arbol entero, desde cada proyecto. */
    List<OrganizationTreeNode> tree(boolean includeInactive);

    /** Desactiva la unidad; un equipo suelta a sus linemen. 409 si tiene hijos activos. */
    void deactivate(UUID id);

    List<TeamMemberResponse> members(UUID teamId, boolean includeFormer);

    /** Sustituye los linemen de una vez: los que faltan salen, los nuevos entran. */
    OrganizationUnitResponse replaceMembers(UUID teamId, TeamMembersRequest request);

    OrganizationUnitResponse addMember(UUID teamId, TeamMemberRequest request);

    OrganizationUnitResponse removeMember(UUID teamId, UUID personId, TeamMemberLeaveRequest request);

    PageResponse<EntityRevisionResponse<OrganizationUnitResponse>> findRevisions(UUID id, Pageable pageable);
}
