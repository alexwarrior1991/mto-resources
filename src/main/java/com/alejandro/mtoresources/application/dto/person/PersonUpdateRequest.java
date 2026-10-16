package com.alejandro.mtoresources.application.dto.person;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;
import jakarta.validation.constraints.Size;

/**
 * Modificacion de una persona: {@code PUT} (lo ausente no se toca) o {@code PATCH} merge-patch (ademas,
 * {@code null} vacia lo opcional). {@code active=false} desactiva, como el {@code DELETE}, y pide tambien
 * {@code resources-delete}.
 *
 * <p>Ejemplo, {@code PATCH /people/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"category": "SUPERVISOR", "phone": null, "version": 2}
 * }</pre>
 */
public record PersonUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 150) String lastName,
        PersonCategory category,
        @Size(max = 40) String employeeNumber,
        @Size(max = 100) String username,
        @Size(max = 100) String keycloakUserId,
        PersonEmployer employer,
        @Size(max = 150) String company,
        @Size(max = 40) String phone,
        Boolean active,
        Long version
) {

    /** Desactivar pide ademas resources-delete, como el DELETE: es la misma decision. */
    public boolean isDeactivating() {
        return Boolean.FALSE.equals(active);
    }
}
