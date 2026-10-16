package com.alejandro.mtoresources.application.dto.organization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Los linemen de un equipo, enteros: los que no estan salen y los que no estaban entran, de una vez. Es
 * como se cambian varios sin pasar por un equipo de menos de 2 o de mas de 6.
 *
 * <p>Ejemplo, {@code PUT /units/{id}/members}:</p>
 * <pre>{@code
 * {"memberPersonIds": ["…", "…", "…"], "effectiveOn": "2026-10-20"}
 * }</pre>
 */
public record TeamMembersRequest(
        @NotNull @Size(min = 2, max = 6) List<UUID> memberPersonIds,
        LocalDate effectiveOn
) {
}
