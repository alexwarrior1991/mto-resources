package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Donde esta cada vehiculo ahora: una fila por vehiculo con el ultimo evento observado. Solo se lee con
 * JPA: la escribe {@code VehiclePositionRepository.moveTo}, un upsert nativo con la marca de agua de
 * {@code observed_at} dentro de la sentencia, para que lo que llega tarde no mueva la posicion.
 */
@Entity
@Table(name = "vehicle_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehiclePosition {

    @Id
    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vehicle_position_event"))
    private VehicleLocationEvent locationEvent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "vehicle_location_kind")
    private VehicleLocationKind kind;

    @Column(name = "parking_site_id")
    private UUID parkingSiteId;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "kp", precision = 12, scale = 3)
    private BigDecimal kp;

    @Column(name = "carrier_vehicle_id")
    private UUID carrierVehicleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", nullable = false, columnDefinition = "vehicle_location_source")
    private VehicleLocationSource source;

    @Column(name = "source_reference", length = 150)
    private String sourceReference;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
