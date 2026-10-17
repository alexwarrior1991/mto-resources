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

import java.math.BigDecimal;

/**
 * Un sitio conocido donde se puede aparcar o estacionar un vehiculo: un apartadero, una via de estacion,
 * una cochera, un aparcamiento. No hay base fija: un vehiculo se deja donde se puede, y este catalogo es
 * solo lo que ya se sabe que vale. Un aparcamiento puede no estar aqui (basta una via y un kp, o una
 * descripcion). No se audita.
 */
@Entity
@Table(name = "parking_site")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class ParkingSite extends VersionedEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "parking_site_kind")
    private ParkingSiteKind kind;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "kp", precision = 12, scale = 3)
    private BigDecimal kp;

    @NotNull
    @Column(name = "rail_access", nullable = false)
    private Boolean railAccess;

    @NotNull
    @Column(name = "road_access", nullable = false)
    private Boolean roadAccess;

    @Size(max = 255)
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
