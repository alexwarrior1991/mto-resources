package com.alejandro.mtoresources.application.dto.fleet;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Modificacion de un documento: renovarlo es cambiar su caducidad. El tipo no cambia (se borra y se da
 * otro), y la caducidad no se vacia: un documento siempre caduca.
 *
 * <p>Ejemplo, {@code PATCH /vehicles/{vehicleId}/documents/{id}} con {@code application/merge-patch+json}:</p>
 * <pre>{@code
 * {"reference": "ITV-2027-031", "issuedOn": "2027-02-20", "expiresOn": "2028-02-29", "notes": null, "version": 0}
 * }</pre>
 */
public record VehicleDocumentUpdateRequest(
        @Size(max = 60) String reference,
        LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 500) String notes,
        Long version
) {
}
