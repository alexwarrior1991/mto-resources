package com.alejandro.mtoresources.application.dto.fleet;

import com.alejandro.mtoresources.infrastructure.persistence.entity.ParkingSiteKind;

import java.util.UUID;

/** Un sitio de aparcamiento dentro de una ubicacion. */
public record ParkingSiteSummary(UUID id, String code, String name, ParkingSiteKind kind) {
}
