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
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Una unidad de la flota: su modelo, su matricula o su numero ferroviario, de quien es y si puede
 * trabajar. Donde esta no va aqui: lo dice {@code vehicle_position}. Sin base fija.
 *
 * <p>El modelo se lee como esta ahora en una revision ({@code NOT_AUDITED}): es un catalogo sin
 * historial.</p>
 */
@Entity
@Audited
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Vehicle extends VersionedEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    @ToString.Include
    private String code;

    @Size(max = 150)
    @Column(name = "name", length = 150)
    private String name;

    @NotNull
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vehicle_type"))
    private VehicleType type;

    @Size(max = 20)
    @Column(name = "registration_plate", length = 20)
    private String registrationPlate;

    @Size(max = 30)
    @Column(name = "rail_vehicle_number", length = 30)
    private String railVehicleNumber;

    @Size(max = 60)
    @Column(name = "serial_number", length = 60)
    private String serialNumber;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "owner", nullable = false, columnDefinition = "vehicle_owner")
    private VehicleOwner owner = VehicleOwner.OWN;

    @Size(max = 150)
    @Column(name = "owner_company", length = 150)
    private String ownerCompany;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "operational_status", nullable = false, columnDefinition = "vehicle_operational_status")
    @ToString.Include
    private VehicleOperationalStatus operationalStatus = VehicleOperationalStatus.OPERATIONAL;

    @Size(max = 500)
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "engine_hours", precision = 10, scale = 1)
    private BigDecimal engineHours;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /** El dispositivo GPS por el que llegan sus posiciones ({@code POST /telematics/positions}); nulo, sin GPS. */
    @Size(max = 64)
    @Column(name = "telematics_device_id", length = 64)
    private String telematicsDeviceId;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Si puede entrar en un turno: activo y operativo, aunque sea con limitaciones. */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(active) && operationalStatus.canWork();
    }
}
