package com.alejandro.mtoresources.application.dto.report;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;

import java.util.List;
import java.util.UUID;

/**
 * Una persona (o un usuario de Keycloak) cuyo perfil de puesto no casa con su categoria.
 *
 * @param personId        nulo en un usuario con perfil de puesto sin ficha aqui
 * @param expectedProfile el perfil de su categoria; nulo si no hay ficha o si su categoria aun no tiene
 * @param profiles        los perfiles de puesto que tiene en Keycloak, en orden
 */
public record PositionDiscrepancy(
        UUID personId,
        String personCode,
        String fullName,
        String username,
        PersonCategory category,
        Boolean personActive,
        String expectedProfile,
        List<String> profiles
) {
}
