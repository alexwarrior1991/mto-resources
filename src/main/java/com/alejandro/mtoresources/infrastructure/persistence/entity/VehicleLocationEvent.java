package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Donde estuvo un vehiculo en un momento: el historico append-only de su ubicacion. No se modifica ni
 * se borra, y no se audita porque ya es la historia. La posicion actual es el ultimo observado
 * ({@code vehicle_position}).
 */
@Entity
@Table(name = "vehicle_location_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class VehicleLocationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_location_event_vehicle"))
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "vehicle_location_kind")
    @ToString.Include
    private VehicleLocationKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_site_id", updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_location_event_site"))
    private ParkingSite parkingSite;

    @Column(name = "execution_package_id", updatable = false)
    private Long executionPackageId;

    @Column(name = "track_id", updatable = false)
    private Long trackId;

    @Column(name = "kp", precision = 12, scale = 3, updatable = false)
    private BigDecimal kp;

    @Column(name = "latitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal longitude;

    /** Texto libre: lo que se escribio antes del catalogo, o un sitio que no esta en el. */
    @Column(name = "access_point", length = 150, updatable = false)
    private String accessPoint;

    /** Donde encarrilo o desencarrilo, del catalogo de {@code mto-configuration} (V9). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_point_id", updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_location_event_access_point"))
    private TrackAccessPoint trackAccessPoint;

    @Column(name = "description", length = 500, updatable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_vehicle_id", updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_location_event_carrier"))
    private Vehicle carrier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "attachment", updatable = false, columnDefinition = "vehicle_attachment")
    private VehicleAttachment attachment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", nullable = false, updatable = false, columnDefinition = "vehicle_location_source")
    @ToString.Include
    private VehicleLocationSource source;

    @Column(name = "source_reference", length = 150, updatable = false)
    private String sourceReference;

    @Column(name = "observed_at", nullable = false, updatable = false)
    @ToString.Include
    private Instant observedAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, updatable = false, length = 100)
    private String recordedBy;
}
