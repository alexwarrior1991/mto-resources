package com.alejandro.mtoresources.application.dto.organization;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Un lineman que entra en un equipo; sin fecha, hoy. 409 {@code UNI-001} si el equipo ya tiene 6. */
public record TeamMemberRequest(@NotNull UUID personId, LocalDate joinedOn) {
}
