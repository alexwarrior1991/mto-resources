package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un tipo de cualificacion ({@code LIC-C}, {@code RAIL-DRIVER}): el catalogo del que salen las de cada
 * persona y la habilitacion que exige un modelo de vehiculo. El codigo es lo que viaja en los eventos y
 * no cambia. Catalogo de cambio raro: no se audita.
 */
@Entity
@Table(name = "qualification_type")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class QualificationType extends VersionedEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "qualification_kind")
    @ToString.Include
    private QualificationKind kind;

    /** Solo en un permiso de conducir ({@link QualificationKind#DRIVING_LICENCE}): cual. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "driving_licence", columnDefinition = "driving_licence")
    private DrivingLicence drivingLicence;

    /** La vigencia por defecto, en meses: la caducidad de una nueva que no la diga. */
    @Column(name = "validity_months")
    private Integer validityMonths;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
