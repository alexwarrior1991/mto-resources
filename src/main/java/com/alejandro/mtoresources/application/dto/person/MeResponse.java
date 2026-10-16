package com.alejandro.mtoresources.application.dto.person;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La persona del token y su sitio en el organigrama: las unidades que dirige o en las que esta, y los EP
 * de esas unidades. Es el gancho del ambito por EP (un supervisor que solo ve lo de su EP), que llega en
 * una fase posterior.
 *
 * <p>Ejemplo, {@code GET /me} de un jefe de equipo:</p>
 * <pre>{@code
 * {"person": {"code": "PER-000003", "username": "equipo.jefe", "category": "TEAM_LEADER", …},
 *  "units": [{"code": "A", "kind": "TEAM", "role": "HEAD", …}], "executionPackageIds": [4, 5, 6]}
 * }</pre>
 */
public record MeResponse(PersonResponse person, List<Unit> units, Set<Long> executionPackageIds) {

    /** Lo que la persona es en esa unidad: quien la dirige o uno de sus linemen. */
    public enum Role {
        HEAD,
        LINEMAN
    }

    public record Unit(UUID id, String code, String name, OrganizationUnitKind kind, WorkDiscipline discipline, Role role) {
    }
}
