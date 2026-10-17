package com.alejandro.mtoresources.application.dto.organization;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Alta de una unidad del organigrama. Un equipo ({@code TEAM}) nace completo: con su jefe y de 2 a 6
 * linemen en la misma peticion. La disciplina la hereda del departamento del que cuelga si no se dice.
 *
 * <p>Ejemplo, {@code POST /units} de un equipo:</p>
 * <pre>{@code
 * {"code": "A", "name": "Team A", "kind": "TEAM", "parentId": "…supervision…", "headPersonId": "…",
 *  "memberPersonIds": ["…", "…", "…", "…"], "executionPackageIds": [4, 5, 6], "defaultVehicleId": "…"}
 * }</pre>
 */
public record OrganizationUnitRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "letters, digits, '-' and '_' only") String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull OrganizationUnitKind kind,
        WorkDiscipline discipline,
        UUID parentId,
        UUID headPersonId,
        Set<Long> executionPackageIds,
        UUID defaultVehicleId,
        List<UUID> memberPersonIds,
        LocalDate joinedOn
) {
}
