package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.EnumSet;
import java.util.Set;

/**
 * Un modelo de vehiculo ({@code Plasser DIC-30 MTW}, {@code Unimog bivial}): lo comun a todas sus
 * unidades. Catalogo de cambio raro: no se audita.
 */
@Entity
@Table(name = "vehicle_type")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class VehicleType extends VersionedEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Size(max = 100)
    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Size(max = 100)
    @Column(name = "model", length = 100)
    private String model;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "mobility", nullable = false, columnDefinition = "vehicle_mobility")
    @ToString.Include
    private VehicleMobility mobility;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false, columnDefinition = "vehicle_category")
    private VehicleCategory category;

    @NotNull
    @Builder.Default
    @Column(name = "self_propelled", nullable = false)
    private Boolean selfPropelled = true;

    @NotNull
    @Builder.Default
    @Column(name = "portable", nullable = false)
    private Boolean portable = false;

    @Column(name = "length_m", precision = 6, scale = 2)
    private BigDecimal lengthM;

    @Column(name = "width_m", precision = 5, scale = 2)
    private BigDecimal widthM;

    @Column(name = "height_m", precision = 5, scale = 2)
    private BigDecimal heightM;

    @Column(name = "tare_t", precision = 8, scale = 2)
    private BigDecimal tareT;

    @Column(name = "max_gross_t", precision = 8, scale = 2)
    private BigDecimal maxGrossT;

    @Column(name = "max_axle_load_t", precision = 6, scale = 2)
    private BigDecimal maxAxleLoadT;

    @Column(name = "axles")
    private Integer axles;

    @Column(name = "seats")
    private Integer seats;

    @Column(name = "payload_t", precision = 8, scale = 2)
    private BigDecimal payloadT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "power", columnDefinition = "vehicle_power")
    private VehiclePower power;

    @Column(name = "track_gauge_mm")
    private Integer trackGaugeMm;

    @Column(name = "max_speed_track_working_kmh")
    private Integer maxSpeedTrackWorkingKmh;

    @Column(name = "max_speed_track_transport_kmh")
    private Integer maxSpeedTrackTransportKmh;

    @Column(name = "max_gradient_permille", precision = 5, scale = 1)
    private BigDecimal maxGradientPermille;

    @Column(name = "min_curve_radius_m")
    private Integer minCurveRadiusM;

    @Column(name = "max_working_cant_mm")
    private Integer maxWorkingCantMm;

    @NotNull
    @Builder.Default
    @Column(name = "transport_mode_authorised", nullable = false)
    private Boolean transportModeAuthorised = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rail_gear_type", columnDefinition = "rail_gear_type")
    private RailGearType railGearType;

    @Column(name = "on_tracking_minutes")
    private Integer onTrackingMinutes;

    @Column(name = "needs_prepared_access")
    private Boolean needsPreparedAccess;

    @Column(name = "max_speed_road_kmh")
    private Integer maxSpeedRoadKmh;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "required_licence", columnDefinition = "driving_licence")
    private DrivingLicence requiredLicence;

    @Column(name = "crane_capacity_t", precision = 6, scale = 2)
    private BigDecimal craneCapacityT;

    @Column(name = "crane_reach_m", precision = 5, scale = 2)
    private BigDecimal craneReachM;

    @Column(name = "platform_height_m", precision = 5, scale = 2)
    private BigDecimal platformHeightM;

    @Column(name = "platform_load_kg")
    private Integer platformLoadKg;

    @Column(name = "platform_insulation_kv", precision = 5, scale = 1)
    private BigDecimal platformInsulationKv;

    @Column(name = "towing_capacity_t", precision = 8, scale = 2)
    private BigDecimal towingCapacityT;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "vehicle_type_capability",
            joinColumns = @JoinColumn(name = "type_id", foreignKey = @ForeignKey(name = "fk_vehicle_type_capability_type")))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "capability", nullable = false, columnDefinition = "vehicle_capability")
    @Builder.Default
    private Set<VehicleCapability> capabilities = EnumSet.noneOf(VehicleCapability.class);

    /**
     * La habilitacion que exige conducirlo ({@code RAIL-DRIVER}, {@code RR-OPERATOR}, {@code LIC-C}...); nula,
     * ninguna. Quien decide si la dotacion de un turno la tiene es {@code mto-maintenance}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_driver_qualification_type_id",
            foreignKey = @ForeignKey(name = "fk_vehicle_type_required_driver_qualification"))
    private QualificationType requiredDriverQualification;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public boolean has(VehicleCapability capability) {
        return capabilities.contains(capability);
    }
}
