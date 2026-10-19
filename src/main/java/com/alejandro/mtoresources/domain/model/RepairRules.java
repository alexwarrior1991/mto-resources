package com.alejandro.mtoresources.domain.model;

import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El taller de un vehiculo: que transicion admite una reparacion y cuando lo deja sin poder trabajar. Sin
 * framework; devuelve el motivo en vez de lanzar, como {@link FleetRules}.
 *
 * <ul>
 *   <li>{@code PLANNED -> OPEN -> CLOSED}; {@code CANCELLED} desde cualquiera de las dos primeras. Una
 *       cerrada o cancelada no vuelve.</li>
 *   <li>Una que inmoviliza, planificada o abierta, deja el vehiculo no disponible
 *       ({@link #unavailablePeriod}): una planificada desde su {@code plannedFrom} hasta su
 *       {@code plannedTo}; una abierta desde que se abrio hasta su {@code plannedTo}, o sin fin si no lo
 *       dice o ya paso (sigue en el taller).</li>
 * </ul>
 */
public final class RepairRules {

    private static final Map<RepairStatus, Set<RepairStatus>> TRANSITIONS = Map.of(
            RepairStatus.PLANNED, Set.of(RepairStatus.OPEN, RepairStatus.CANCELLED),
            RepairStatus.OPEN, Set.of(RepairStatus.CLOSED, RepairStatus.CANCELLED),
            RepairStatus.CLOSED, Set.of(),
            RepairStatus.CANCELLED, Set.of());

    private RepairRules() {
    }

    /** Vacio si la reparacion puede pasar de {@code from} a {@code to}; el motivo si no. */
    public static Optional<String> transitionViolation(String repair, RepairStatus from, RepairStatus to) {
        if (TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            return Optional.empty();
        }
        return Optional.of("Repair " + repair + " is " + from + ": it cannot become " + to);
    }

    /** Si sigue viva: planificada o abierta. */
    public static boolean isLive(RepairStatus status) {
        return status == RepairStatus.PLANNED || status == RepairStatus.OPEN;
    }

    /**
     * Cuando deja el vehiculo sin poder trabajar, si lo deja y aun cuenta hoy o despues.
     *
     * @param openedOn el dia en que se abrio, en la zona del servicio; solo cuenta en una abierta
     */
    public static Optional<UnavailablePeriod> unavailablePeriod(RepairStatus status, boolean immobilises, LocalDate plannedFrom,
                                                                LocalDate plannedTo, LocalDate openedOn, LocalDate today) {
        if (!immobilises) {
            return Optional.empty();
        }
        UnavailablePeriod period;
        if (status == RepairStatus.PLANNED && plannedFrom != null) {
            period = new UnavailablePeriod(plannedFrom, plannedTo);
        } else if (status == RepairStatus.OPEN && openedOn != null) {
            // Abierta: dura hasta que se cierre. Su fin previsto solo vale mientras no haya pasado.
            LocalDate to = plannedTo == null || plannedTo.isBefore(today) || plannedTo.isBefore(openedOn) ? null : plannedTo;
            period = new UnavailablePeriod(openedOn, to);
        } else {
            return Optional.empty();
        }
        return period.endsOnOrAfter(today) ? Optional.of(period) : Optional.empty();
    }
}
