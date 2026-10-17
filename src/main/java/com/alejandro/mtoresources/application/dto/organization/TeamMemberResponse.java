package com.alejandro.mtoresources.application.dto.organization;

import com.alejandro.mtoresources.application.dto.person.PersonSummary;

import java.time.LocalDate;
import java.util.UUID;

/** Un lineman de un equipo, con su entrada y, si ya salio, su salida. */
public record TeamMemberResponse(UUID id, PersonSummary person, LocalDate joinedOn, LocalDate leftOn) {
}
