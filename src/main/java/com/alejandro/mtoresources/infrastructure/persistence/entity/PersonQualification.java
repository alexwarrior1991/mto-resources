package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDate;

/**
 * Una cualificacion de una persona: una por tipo, con sus fechas. Renovar es cambiar la caducidad; una
 * que se dio por error se borra (Envers guarda el borrado).
 *
 * <p>El tipo se lee como esta ahora en una revision ({@code NOT_AUDITED}): es un catalogo sin
 * historial. Las dos marcas de aviso las escribe solo {@code ExpiryWatchService} con SQL nativo y una
 * condicion dentro de la sentencia: Hibernate las lee pero no las escribe nunca
 * ({@code insertable = false, updatable = false}), para que guardar la fila no devuelva una marca vieja
 * leida antes del aviso.</p>
 */
@Entity
@Audited
@Table(name = "person_qualification")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class PersonQualification extends VersionedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_person_qualification_person"))
    private Person person;

    @NotNull
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_type_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_person_qualification_type"))
    private QualificationType qualificationType;

    @Size(max = 60)
    @Column(name = "reference", length = 60)
    private String reference;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** Nula: no caduca. */
    @Column(name = "expires_on")
    @ToString.Include
    private LocalDate expiresOn;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    /** La caducidad de la que ya se aviso que se acercaba. */
    @NotAudited
    @Setter(AccessLevel.NONE)
    @Column(name = "expiring_notified_for", insertable = false, updatable = false)
    private LocalDate expiringNotifiedFor;

    /** La caducidad de la que ya se aviso que habia pasado. */
    @NotAudited
    @Setter(AccessLevel.NONE)
    @Column(name = "expired_notified_for", insertable = false, updatable = false)
    private LocalDate expiredNotifiedFor;
}
