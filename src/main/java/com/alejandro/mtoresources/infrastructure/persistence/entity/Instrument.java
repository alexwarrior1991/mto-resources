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
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Un instrumento calibrado que se lleva al tajo: una pertiga de puesta a tierra, un detector de tension,
 * un equipo de medida. Ficha propia, como un vehiculo: se elige en el turno de {@code mto-maintenance}, que
 * es quien decide que uno con la calibracion caducada no entra. Se retira, no se borra.
 *
 * <p>Las dos marcas de aviso las escribe solo {@code ExpiryWatchService} con SQL nativo: Hibernate las lee
 * pero no las escribe nunca, como en {@link PersonQualification}.</p>
 */
@Entity
@Audited
@Table(name = "instrument")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Instrument extends VersionedEntity {

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
    @Column(name = "kind", nullable = false, columnDefinition = "instrument_kind")
    @ToString.Include
    private InstrumentKind kind;

    @Size(max = 60)
    @Column(name = "serial_number", length = 60)
    private String serialNumber;

    @Column(name = "calibrated_on")
    private LocalDate calibratedOn;

    /** Nula: sin calibracion (un instrumento que no la necesita, o del que no se sabe). */
    @Column(name = "calibration_expires_on")
    @ToString.Include
    private LocalDate calibrationExpiresOn;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** La caducidad de la calibracion de la que ya se aviso que se acercaba. */
    @NotAudited
    @Setter(AccessLevel.NONE)
    @Column(name = "expiring_notified_for", insertable = false, updatable = false)
    private LocalDate expiringNotifiedFor;

    /** La caducidad de la calibracion de la que ya se aviso que habia pasado. */
    @NotAudited
    @Setter(AccessLevel.NONE)
    @Column(name = "expired_notified_for", insertable = false, updatable = false)
    private LocalDate expiredNotifiedFor;
}
