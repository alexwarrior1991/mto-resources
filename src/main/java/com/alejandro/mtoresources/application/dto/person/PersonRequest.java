package com.alejandro.mtoresources.application.dto.person;

import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonEmployer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una persona. El codigo ({@code PER-000001}) lo pone el servicio. {@code username} es su
 * {@code preferred_username} de Keycloak si entra en las aplicaciones; un operario sin login no lo lleva.
 *
 * <p>Ejemplo, {@code POST /people}:</p>
 * <pre>{@code
 * {"firstName": "Yossi", "lastName": "Cohen", "category": "TEAM_LEADER", "employeeNumber": "E-1042",
 *  "username": "equipo.jefe", "phone": "+972 50 000 0000"}
 * }</pre>
 */
public record PersonRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 150) String lastName,
        @NotNull PersonCategory category,
        @Size(max = 40) String employeeNumber,
        @Size(max = 100) String username,
        @Size(max = 100) String keycloakUserId,
        PersonEmployer employer,
        @Size(max = 150) String company,
        @Size(max = 40) String phone
) {
}
