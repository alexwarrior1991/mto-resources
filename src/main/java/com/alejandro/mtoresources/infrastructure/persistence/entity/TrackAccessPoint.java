package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un punto de acceso a la via de {@code mto-configuration}: por donde un vehiculo bivial sale de la
 * carretera y se encarrila. Es la instantanea de lo que piden las reglas de la flota, no el maestro: solo
 * se lee con JPA, y la escribe {@code TrackAccessPointRepository} con SQL nativo desde el evento
 * {@code track-access-point}, con la marca de agua de {@code sequenceNumber} dentro de la sentencia. No se
 * audita (Envers no ve el SQL nativo; su historia es la de {@code mto-configuration}).
 *
 * <p>{@code kind} y {@code side} son texto y no enumerados: son de otro servicio, y un valor nuevo alli no
 * debe impedir leer la fila aqui. Hoy {@code kind} es {@code PREPARED_ACCESS}, {@code LEVEL_CROSSING} o
 * {@code DEPOT}, y {@code side} {@code LEFT}, {@code RIGHT} o {@code BOTH}.</p>
 */
@Entity
@Immutable
@Table(name = "track_access_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class TrackAccessPoint {

    /** El id de {@code mto-configuration}, no uno propio. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @ToString.Include
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    @ToString.Include
    private String name;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "station_id")
    private Long stationId;

    /** En metros, como lo publica {@code mto-configuration}. */
    @Column(name = "kp", precision = 12, scale = 3)
    private BigDecimal kp;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "road_rail_access", nullable = false)
    private Boolean roadRailAccess;

    @Column(name = "max_axle_load_t", precision = 8, scale = 3)
    private BigDecimal maxAxleLoadT;

    @Column(name = "max_gross_weight_t", precision = 8, scale = 3)
    private BigDecimal maxGrossWeightT;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
