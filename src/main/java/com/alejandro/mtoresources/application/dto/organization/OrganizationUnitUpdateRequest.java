package com.alejandro.mtoresources.application.dto.organization;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Modificacion de una unidad. Los linemen de un equipo no van aqui sino en {@code /units/{id}/members},
 * salvo al reactivarlo: desactivar un equipo solto a sus linemen, y vuelve como nacio, completo
 * ({@code active=true} con {@code memberPersonIds}, de 2 a 6). El jefe de un equipo se sustituye, no se
 * vacia. {@code active=false} desactiva, como el {@code DELETE}.
 *
 * <p>Ejemplo, {@code PATCH /units/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"headPersonId": "…otro jefe…", "defaultVehicleId": null, "version": 4}
 * }</pre>
 */
public record OrganizationUnitUpdateRequest(
        @Size(max = 120) String name,
        UUID parentId,
        UUID headPersonId,
        Set<Long> executionPackageIds,
        UUID defaultVehicleId,
        Boolean active,
        @Size(min = 2, max = 6) List<UUID> memberPersonIds,
        LocalDate joinedOn,
        Long version
) {

    /** Desactivar pide ademas resources-delete, como el DELETE. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
