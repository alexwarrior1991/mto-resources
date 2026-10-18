package com.alejandro.mtoresources.application.dto.qualification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;

import java.util.UUID;

/** Un tipo de cualificacion dentro de otra respuesta: la de una persona, la que exige un modelo. */
public record QualificationTypeSummary(UUID id, String code, String name, QualificationKind kind) {
}
