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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una reparacion de un vehiculo en el taller: una averia, una revision, una inspeccion o una
 * modificacion. {@code PLANNED -> OPEN -> CLOSED}, o {@code CANCELLED} ({@code RepairRules}); nada se
 * borra. Una que inmoviliza manda en el estado del vehiculo mientras esta abierta, y planificada o abierta
 * viaja en el {@code unavailable} de su evento.
 */
@Entity
@Audited
@Table(name = "vehicle_repair")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class VehicleRepair extends VersionedEntity {

    @NotBlank
    @Size(max = 16)
    @Column(name = "code", nullable = false, updatable = false, length = 16)
    @ToString.Include
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_vehicle_repair_vehicle"))
    private Vehicle vehicle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "repair_kind")
    private RepairKind kind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "repair_status")
    @ToString.Include
    private RepairStatus status;

    @NotNull
    @Builder.Default
    @Column(name = "immobilises", nullable = false)
    private Boolean immobilises = Boolean.TRUE;

    @NotBlank
    @Size(max = 500)
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Size(max = 150)
    @Column(name = "workshop", length = 150)
    private String workshop;

    @Column(name = "planned_from")
    private LocalDate plannedFrom;

    @Column(name = "planned_to")
    private LocalDate plannedTo;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "engine_hours", precision = 10, scale = 1)
    private BigDecimal engineHours;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
