package com.alejandro.mtoresources.application.dto.organization;

import com.alejandro.mtoresources.application.dto.person.PersonSummary;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un nodo del arbol del organigrama ({@code GET /units/tree}), con sus hijos. En un equipo, cuantos
 * linemen tiene.
 */
public record OrganizationTreeNode(
        UUID id,
        String code,
        String name,
        OrganizationUnitKind kind,
        WorkDiscipline discipline,
        PersonSummary head,
        Set<Long> executionPackageIds,
        Integer linemen,
        Boolean active,
        List<OrganizationTreeNode> children
) {
}
