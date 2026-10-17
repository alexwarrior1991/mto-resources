package com.alejandro.mtoresources.infrastructure.persistence.entity;

/**
 * Los niveles del organigrama, de arriba abajo. Una unidad cuelga de otra de un nivel estrictamente
 * superior (se admite saltar niveles: un equipo puede colgar del departamento si no hay supervision).
 */
public enum OrganizationUnitKind {

    PROJECT(5),
    DEPARTMENT(4),
    CHIEF_SUPERVISION(3),
    SUPERVISION(2),
    TEAM(1);

    private final int level;

    OrganizationUnitKind(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * La categoria minima de quien la dirige. Un departamento la pide segun su disciplina: lo dirige el
     * jefe de mantenimiento o el de construccion ({@code OrganizationRules}).
     */
    public PersonCategory minimumHeadCategory() {
        return switch (this) {
            case PROJECT -> PersonCategory.PROJECT_MANAGER;
            case DEPARTMENT -> PersonCategory.CHIEF_MAINTENANCE;
            case CHIEF_SUPERVISION -> PersonCategory.CHIEF_SUPERVISOR;
            case SUPERVISION -> PersonCategory.SUPERVISOR;
            case TEAM -> PersonCategory.TEAM_LEADER;
        };
    }
}
