package com.alejandro.mtoresources.application.dto.person;

import com.alejandro.mtoresources.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;

import java.util.UUID;

/**
 * Una persona.
 *
 * <p>Ejemplo, {@code GET /people/{id}}:</p>
 * <pre>{@code
 * {"id": "…", "code": "PER-000003", "firstName": "Yossi", "lastName": "Cohen", "fullName": "Yossi Cohen",
 *  "username": "equipo.jefe", "category": "TEAM_LEADER", "employer": "OWN", "active": true, "version": 0, "audit": {…}}
 * }</pre>
 */
public record PersonResponse(
        UUID id,
        String code,
        String employeeNumber,
        String firstName,
        String lastName,
        String fullName,
        String username,
        String keycloakUserId,
        PersonCategory category,
        PersonEmployer employer,
        String company,
        String phone,
        Boolean active,
        Long version,
        AuditMetadataResponse audit
) {
}
