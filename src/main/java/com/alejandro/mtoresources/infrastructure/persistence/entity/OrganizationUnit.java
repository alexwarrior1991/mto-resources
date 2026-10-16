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
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Una unidad del organigrama: el proyecto, un departamento (mantenimiento, construccion), una jefatura
 * de supervisores, una supervision o un equipo. Su jefe es {@code head}; en un equipo es el jefe de
 * equipo, siempre presente, y sus linemen estan en {@link TeamMember}.
 */
@Entity
@Audited
@Table(name = "organization_unit")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class OrganizationUnit extends VersionedEntity {

    @NotBlank
    @Size(max = 16)
    @Column(name = "code", nullable = false, updatable = false, length = 16)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "organization_unit_kind")
    @ToString.Include
    private OrganizationUnitKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "discipline", columnDefinition = "work_discipline")
    private WorkDiscipline discipline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_organization_unit_parent"))
    private OrganizationUnit parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_person_id", foreignKey = @ForeignKey(name = "fk_organization_unit_head"))
    private Person head;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_vehicle_id", foreignKey = @ForeignKey(name = "fk_organization_unit_default_vehicle"))
    private Vehicle defaultVehicle;

    /** Ids (mto-configuration) de los EP de la unidad: los de una supervision, los que atiende un equipo. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "organization_unit_execution_package",
            joinColumns = @JoinColumn(name = "unit_id", foreignKey = @ForeignKey(name = "fk_organization_unit_execution_package_unit"))
    )
    @Column(name = "execution_package_id", nullable = false)
    @Builder.Default
    private Set<Long> executionPackageIds = new LinkedHashSet<>();

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public boolean isTeam() {
        return kind == OrganizationUnitKind.TEAM;
    }
}
