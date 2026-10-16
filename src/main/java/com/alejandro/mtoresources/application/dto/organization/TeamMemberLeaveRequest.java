package com.alejandro.mtoresources.application.dto.organization;

import java.time.LocalDate;

/** Un lineman que sale de un equipo; sin fecha, hoy. 409 {@code UNI-001} si el equipo se quedaria con menos de 2. */
public record TeamMemberLeaveRequest(LocalDate leftOn) {
}
