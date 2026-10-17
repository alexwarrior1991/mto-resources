package com.alejandro.mtoresources.application.dto.qualification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de una cualificacion de una persona: una por tipo, de un tipo activo. Sin {@code expiresOn}, la
 * caducidad es {@code issuedOn} mas la vigencia del tipo ({@code validityMonths}), si el tipo la tiene;
 * si no, no caduca.
 *
 * <p>Ejemplo, {@code POST /people/{personId}/qualifications}:</p>
 * <pre>{@code
 * {"qualificationTypeId": "0195b000-0000-7000-8000-000000000104", "reference": "RD-2024-0117",
 *  "issuedOn": "2024-04-01", "expiresOn": "2027-03-31"}
 * }</pre>
 */
public record PersonQualificationRequest(
        @NotNull UUID qualificationTypeId,
        @Size(max = 60) String reference,
        LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 500) String notes
) {
}
