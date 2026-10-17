package com.alejandro.mtoresources.infrastructure.persistence.entity;

/**
 * Lo que es una persona en la jerarquia del dominio, de abajo arriba. El rango ordena: el jefe de una
 * unidad tiene una categoria de rango igual o mayor que la que pide su nivel
 * ({@link OrganizationUnitKind#minimumHeadCategory()}).
 *
 * <p>Los dos jefes de disciplina comparten rango: el de mantenimiento dirige el departamento de
 * mantenimiento y el de construccion el de construccion, y ninguno el del otro.</p>
 */
public enum PersonCategory {

    /** Operario, el lineman de un equipo. */
    WORKER(1),
    /** Jefe de equipo (foreman): dirige un equipo dentro de un vehiculo durante el turno. */
    TEAM_LEADER(2),
    /** Controla a la vez varios equipos y vehiculos, normalmente en un mismo EP. */
    SUPERVISOR(3),
    /** Controla el trabajo de los supervisores. */
    CHIEF_SUPERVISOR(4),
    /** Jefe de mantenimiento. */
    CHIEF_MAINTENANCE(5),
    /** Jefe de construccion, para cuando exista mto-construction. */
    CHIEF_CONSTRUCTION(5),
    /** Jefe de proyecto: de todo. */
    PROJECT_MANAGER(6);

    private final int rank;

    PersonCategory(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
