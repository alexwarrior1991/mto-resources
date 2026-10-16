package com.alejandro.mtoresources.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

/**
 * Un lineman en un equipo, desde que entra hasta que sale. La fila no se borra al salir: queda con su
 * {@code leftOn}, y la historia del equipo es la de sus filas.
 */
@Entity
@Audited
@Table(name = "team_member")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class TeamMember extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_team_member_team"))
    private OrganizationUnit team;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_team_member_person"))
    private Person person;

    @NotNull
    @Column(name = "joined_on", nullable = false)
    @ToString.Include
    private LocalDate joinedOn;

    @Column(name = "left_on")
    @ToString.Include
    private LocalDate leftOn;

    public boolean isOpen() {
        return leftOn == null;
    }
}
