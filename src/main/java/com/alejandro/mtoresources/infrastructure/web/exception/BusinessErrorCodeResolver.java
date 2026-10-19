package com.alejandro.mtoresources.infrastructure.web.exception;

import com.alejandro.mtoresources.application.exception.AbsenceRuleException;
import com.alejandro.mtoresources.application.exception.BusinessException;
import com.alejandro.mtoresources.application.exception.DuplicateCodeException;
import com.alejandro.mtoresources.application.exception.FleetRuleException;
import com.alejandro.mtoresources.application.exception.KeycloakUnavailableException;
import com.alejandro.mtoresources.application.exception.NotFoundException;
import com.alejandro.mtoresources.application.exception.OrganizationRuleException;
import com.alejandro.mtoresources.application.exception.QualificationRuleException;
import com.alejandro.mtoresources.application.exception.RepairRuleException;
import com.alejandro.mtoresources.application.exception.StaleVersionException;
import com.alejandro.mtoresources.application.exception.TooManyOperationsException;
import com.alejandro.mtoresources.application.exception.ValidationException;

import java.util.Map;

/**
 * Codigo de error estable por tipo de excepcion, para que un cliente distinga la causa sin
 * analizar el mensaje. El prefijo sale del agregado en los 404 y 409 de codigo duplicado.
 */
final class BusinessErrorCodeResolver {

    private static final Map<String, String> AGGREGATE_PREFIXES = Map.ofEntries(
            Map.entry("Person", "PER"),
            Map.entry("Organization unit", "UNI"),
            Map.entry("Vehicle", "VEH"),
            Map.entry("Vehicle type", "VTY"),
            Map.entry("Parking site", "PRK"),
            Map.entry("Qualification type", "QTY"),
            Map.entry("Person qualification", "QUA"),
            Map.entry("Person absence", "ABS"),
            Map.entry("Vehicle repair", "REP"),
            Map.entry("Vehicle document", "VDO"),
            Map.entry("Instrument", "INT"),
            Map.entry("Track access point", "TAP"),
            Map.entry("Background operation", "OPS")
    );

    private BusinessErrorCodeResolver() {
    }

    static String resolve(BusinessException exception) {
        return switch (exception) {
            case NotFoundException notFound -> aggregateCode(notFound.getAggregate(), "404", "APP-404");
            case DuplicateCodeException duplicate -> aggregateCode(duplicate.getAggregate(), "409", "APP-409");
            case OrganizationRuleException ignored -> "UNI-001";
            case FleetRuleException ignored -> "VEH-001";
            case QualificationRuleException ignored -> "QUA-001";
            case AbsenceRuleException ignored -> "ABS-001";
            case RepairRuleException ignored -> "REP-001";
            case KeycloakUnavailableException ignored -> "KCK-503";
            case TooManyOperationsException ignored -> "OPS-429";
            case StaleVersionException ignored -> "CON-001";
            case ValidationException ignored -> "VAL-001";
            default -> "BUS-001";
        };
    }

    private static String aggregateCode(String aggregate, String suffix, String fallback) {
        String prefix = aggregate == null ? null : AGGREGATE_PREFIXES.get(aggregate);
        return prefix == null ? fallback : "%s-%s".formatted(prefix, suffix);
    }
}
