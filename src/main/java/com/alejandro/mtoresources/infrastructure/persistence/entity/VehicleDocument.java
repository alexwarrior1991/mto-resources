package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Un documento con caducidad de un vehiculo (la ITV, la inspeccion ferroviaria, el seguro, el certificado
 * de la grua o de la plataforma): uno de cada tipo, y siempre caduca. Renovarlo es cambiar su caducidad;
 * uno dado por error se borra (Envers guarda el borrado).
 *
 * <p>Las dos marcas de aviso las escribe solo {@code ExpiryWatchService} con SQL nativo: Hibernate las lee
 * pero no las escribe nunca, como en {@link PersonQualification}.</p>
 */
@Entity
@Audited
@Table(name = "vehicle_document")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class VehicleDocument extends VersionedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_document_vehicle"))
    private Vehicle vehicle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "vehicle_document_kind")
    @ToString.Include
    private VehicleDocumentKind kind;

    @Size(max = 60)
    @Column(name = "reference", length = 60)
    private String reference;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @NotNull
    @Column(name = "expires_on", nullable = false)
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
