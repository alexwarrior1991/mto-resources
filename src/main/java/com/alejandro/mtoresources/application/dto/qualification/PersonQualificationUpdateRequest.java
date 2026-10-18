package com.alejandro.mtoresources.application.dto.qualification;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Modificacion de una cualificacion: renovarla es cambiar su caducidad. El tipo no cambia (se borra y se
 * da otra). Con merge-patch se vacia todo menos la version; {@code expiresOn: null} es que ya no caduca.
 *
 * <p>Ejemplo, {@code PATCH /people/{personId}/qualifications/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"reference": "RD-2027-0042", "issuedOn": "2027-03-20", "expiresOn": "2030-03-19", "version": 1}
 * }</pre>
 */
public record PersonQualificationUpdateRequest(
        @Size(max = 60) String reference,
        LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 500) String notes,
        Long version
) {
}
