package com.alejandro.mtoresources.domain.model;

import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;

import java.util.Optional;

/**
 * Las reglas del organigrama: de quien cuelga cada nivel y quien puede dirigirlo. Sin framework, como
 * las maquinas de estado de {@code mto-maintenance}.
 */
public final class OrganizationRules {

    private OrganizationRules() {
    }

    /**
     * Una unidad cuelga de otra de un nivel estrictamente superior: un equipo de una supervision, de una
     * jefatura o del departamento si no hay niveles intermedios; nunca de otro equipo. Un proyecto no cuelga
     * de nada.
     */
    public static Optional<String> parentViolation(OrganizationUnitKind kind, OrganizationUnitKind parentKind) {
        if (kind == OrganizationUnitKind.PROJECT) {
            return parentKind == null ? Optional.empty() : Optional.of("A project is the root of the organization: it has no parent");
        }
        if (parentKind == null) {
            return Optional.of("A " + label(kind) + " hangs from a unit of a higher level");
        }
        if (parentKind.level() <= kind.level()) {
            return Optional.of("A " + label(kind) + " cannot hang from a " + label(parentKind) + ": the parent has to be of a higher level");
        }
        return Optional.empty();
    }

    /**
     * Quien dirige una unidad tiene una categoria de rango igual o mayor que la de su nivel: un equipo, un
     * jefe de equipo o mas; una supervision, un supervisor o mas. Un departamento lo dirige el jefe de su
     * disciplina (o el de proyecto), nunca el de la otra.
     */
    public static Optional<String> headViolation(OrganizationUnitKind kind, WorkDiscipline discipline, PersonCategory headCategory) {
        if (headCategory == null) {
            return Optional.empty();
        }
        if (kind == OrganizationUnitKind.DEPARTMENT) {
            PersonCategory chief = discipline == WorkDiscipline.CONSTRUCTION ? PersonCategory.CHIEF_CONSTRUCTION : PersonCategory.CHIEF_MAINTENANCE;
            if (headCategory != chief && headCategory != PersonCategory.PROJECT_MANAGER) {
                return Optional.of("A " + (discipline == null ? "" : discipline.name().toLowerCase() + " ") + "department is led by a "
                        + chief + " (or the PROJECT_MANAGER), not by a " + headCategory);
            }
            return Optional.empty();
        }
        PersonCategory minimum = kind.minimumHeadCategory();
        if (headCategory.rank() < minimum.rank()) {
            return Optional.of("A " + label(kind) + " is led by a " + minimum + " or above, not by a " + headCategory);
        }
        return Optional.empty();
    }

    /** Un lineman es un operario o un jefe de equipo que va de lineman con otro. */
    public static boolean canBeLineman(PersonCategory category) {
        return category == PersonCategory.WORKER || category == PersonCategory.TEAM_LEADER;
    }

    private static String label(OrganizationUnitKind kind) {
        return kind.name().toLowerCase().replace('_', ' ');
    }
}
