package com.alejandro.mtoresources.application.dto.person;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;

import java.util.UUID;

/** Una persona dentro de otra respuesta: el jefe de un equipo, un lineman, el supervisor. */
public record PersonSummary(UUID id, String code, String fullName, String username, PersonCategory category) {
}
