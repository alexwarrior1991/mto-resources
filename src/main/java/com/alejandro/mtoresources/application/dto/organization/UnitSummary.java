package com.alejandro.mtoresources.application.dto.organization;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;

import java.util.UUID;

/** Una unidad dentro de otra respuesta: el padre de una unidad. */
public record UnitSummary(UUID id, String code, String name, OrganizationUnitKind kind) {
}
