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
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Una ausencia de una persona (vacaciones, una baja, una formacion...), con las dos fechas incluidas. No
 * se solapa con otra de la misma persona. Su clase no sale de aqui: el evento {@code person.*} solo dice
 * el periodo ({@code unavailable}). Una dada por error se borra (Envers guarda el borrado).
 */
@Entity
@Audited
@Table(name = "person_absence")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class PersonAbsence extends VersionedEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_person_absence_person"))
    private Person person;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "absence_kind")
    @ToString.Include
    private AbsenceKind kind;

    @NotNull
    @Column(name = "starts_on", nullable = false)
    @ToString.Include
    private LocalDate startsOn;

    @NotNull
    @Column(name = "ends_on", nullable = false)
    @ToString.Include
    private LocalDate endsOn;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;
}
