package com.alejandro.mtoresources.application.dto.qualification;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.QualificationKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un tipo de cualificacion. {@code drivingLicence} va si y solo si es un permiso de conducir
 * ({@code DRIVING_LICENCE}); {@code validityMonths} es la vigencia por defecto de las que no digan su
 * caducidad.
 *
 * <p>Ejemplo, {@code POST /qualification-types}:</p>
 * <pre>{@code
 * {"code": "MEWP", "name": "Mobile elevating work platform operator", "kind": "VEHICLE_OPERATION", "validityMonths": 60}
 * }</pre>
 */
public record QualificationTypeRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "letters, digits, '.', '-' and '_' only") String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull QualificationKind kind,
        DrivingLicence drivingLicence,
        @Min(1) Integer validityMonths,
        @Size(max = 500) String description
) {
}
