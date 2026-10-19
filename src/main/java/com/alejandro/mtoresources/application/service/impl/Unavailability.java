package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.domain.model.RepairRules;
import com.alejandro.mtoresources.domain.model.UnavailablePeriod;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonAbsence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.RepairStatus;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleRepair;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonAbsenceRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepairRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cuando no esta disponible cada persona y cada vehiculo, tal como viaja en el {@code unavailable} de sus
 * eventos: las ausencias de una persona y las reparaciones que inmovilizan un vehiculo, planificadas o
 * abiertas, que terminan hoy o despues. Lo que ya paso no viaja; el consumidor mira la fecha de su turno.
 *
 * <p>Todo quien publica un {@code person.*} o un {@code vehicle.*} lo pide aqui: un evento sin la lista
 * le diria al consumidor que ya no hay nada.</p>
 */
@Component
@RequiredArgsConstructor
class Unavailability {

    private static final Set<RepairStatus> LIVE = Set.of(RepairStatus.PLANNED, RepairStatus.OPEN);

    private final PersonAbsenceRepository absences;
    private final VehicleRepairRepository repairs;
    private final Clock clock;

    List<UnavailablePeriod> ofPerson(UUID personId) {
        return ofPeople(List.of(personId)).getOrDefault(personId, List.of());
    }

    Map<UUID, List<UnavailablePeriod>> ofPeople(Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return absences.findCurrentOfPeople(personIds, today()).stream()
                .collect(Collectors.groupingBy(absence -> absence.getPerson().getId(),
                        Collectors.mapping(Unavailability::period, Collectors.collectingAndThen(Collectors.toList(), Unavailability::sorted))));
    }

    List<UnavailablePeriod> ofVehicle(UUID vehicleId) {
        return ofVehicles(List.of(vehicleId)).getOrDefault(vehicleId, List.of());
    }

    Map<UUID, List<UnavailablePeriod>> ofVehicles(Collection<UUID> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = today();
        Map<UUID, List<UnavailablePeriod>> periods = new HashMap<>();
        for (VehicleRepair repair : repairs.findImmobilising(vehicleIds, LIVE)) {
            RepairRules.unavailablePeriod(repair.getStatus(), Boolean.TRUE.equals(repair.getImmobilises()), repair.getPlannedFrom(),
                            repair.getPlannedTo(), openedOn(repair), today)
                    .ifPresent(period -> periods.computeIfAbsent(repair.getVehicle().getId(), id -> new ArrayList<>()).add(period));
        }
        periods.replaceAll((id, list) -> sorted(list));
        return periods;
    }

    /** El dia en que se abrio, en la zona del reloj del servicio. */
    LocalDate openedOn(VehicleRepair repair) {
        return repair.getOpenedAt() == null ? null : LocalDate.ofInstant(repair.getOpenedAt(), clock.getZone());
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    private static UnavailablePeriod period(PersonAbsence absence) {
        return new UnavailablePeriod(absence.getStartsOn(), absence.getEndsOn());
    }

    private static List<UnavailablePeriod> sorted(List<UnavailablePeriod> periods) {
        return periods.stream().sorted(Comparator.comparing(UnavailablePeriod::from)).toList();
    }
}
