package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.report.PositionDiscrepancy;
import com.alejandro.mtoresources.application.dto.report.PositionDiscrepancyReport;
import com.alejandro.mtoresources.application.service.KeycloakDirectory;
import com.alejandro.mtoresources.application.service.PositionDiscrepancyService;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Person;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lee de Keycloak quien tiene cada perfil de puesto ({@code mto-position-*}, de
 * {@code mto-platform/keycloak/mto-positions-cross-service.json}) y lo compara con la categoria de cada
 * persona activa con usuario, por el usuario sin mayusculas. Primero Keycloak, fuera de la transaccion: si
 * no responde, no se ha leido nada de la base. {@code CHIEF_CONSTRUCTION} aun no tiene perfil: sin ninguno no
 * es discrepancia.
 */
@Service
@RequiredArgsConstructor
class PositionDiscrepancyServiceImpl implements PositionDiscrepancyService {

    /** El perfil de puesto de cada categoria. */
    static final Map<PersonCategory, String> POSITION_PROFILES = positionProfiles();

    private final KeycloakDirectory directory;
    private final PersonRepository personRepository;
    private final Clock clock;

    @Override
    public PositionDiscrepancyReport report() {
        // usuario -> sus perfiles de puesto, en orden
        Map<String, TreeSet<String>> holders = new TreeMap<>();
        for (String profile : new TreeSet<>(POSITION_PROFILES.values())) {
            directory.realmRoleMembers(profile).forEach(user ->
                    holders.computeIfAbsent(user.username().toLowerCase(Locale.ROOT), username -> new TreeSet<>()).add(profile));
        }
        return compare(holders);
    }

    /** La lectura de las fichas es una sola consulta de columnas simples: no hace falta transaccion. */
    private PositionDiscrepancyReport compare(Map<String, TreeSet<String>> holders) {
        Map<String, Person> byUsername = new HashMap<>();
        for (Person person : personRepository.findAllWithUsername()) {
            String username = person.getUsername().toLowerCase(Locale.ROOT);
            // Un usuario con una ficha activa y otra antigua: cuenta la activa.
            byUsername.merge(username, person, (stored, other) -> Boolean.TRUE.equals(stored.getActive()) ? stored : other);
        }
        List<PositionDiscrepancy> wrong = new ArrayList<>();
        List<PositionDiscrepancy> several = new ArrayList<>();
        List<PositionDiscrepancy> without = new ArrayList<>();
        List<PositionDiscrepancy> withoutRecord = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Person> entry : new TreeMap<>(byUsername).entrySet()) {
            Person person = entry.getValue();
            if (!Boolean.TRUE.equals(person.getActive())) {
                continue;
            }
            checked++;
            List<String> profiles = List.copyOf(holders.getOrDefault(entry.getKey(), new TreeSet<>()));
            String expected = POSITION_PROFILES.get(person.getCategory());
            if (profiles.size() > 1) {
                several.add(of(person, expected, profiles));
            } else if (profiles.size() == 1 && !profiles.getFirst().equals(expected)) {
                wrong.add(of(person, expected, profiles));
            } else if (profiles.isEmpty() && expected != null) {
                without.add(of(person, expected, profiles));
            }
        }
        holders.forEach((username, profiles) -> {
            Person person = byUsername.get(username);
            if (person == null || !Boolean.TRUE.equals(person.getActive())) {
                withoutRecord.add(person == null
                        ? new PositionDiscrepancy(null, null, null, username, null, null, null, List.copyOf(profiles))
                        : of(person, null, List.copyOf(profiles)));
            }
        });
        Comparator<PositionDiscrepancy> byUser = Comparator.comparing(PositionDiscrepancy::username);
        return new PositionDiscrepancyReport(clock.instant(), checked, holders.size(), sorted(wrong, byUser), sorted(several, byUser),
                sorted(without, byUser), sorted(withoutRecord, byUser));
    }

    private static PositionDiscrepancy of(Person person, String expected, List<String> profiles) {
        return new PositionDiscrepancy(person.getId(), person.getCode(), person.fullName(), person.getUsername(), person.getCategory(),
                person.getActive(), expected, profiles);
    }

    private static List<PositionDiscrepancy> sorted(List<PositionDiscrepancy> list, Comparator<PositionDiscrepancy> order) {
        return list.stream().sorted(order).toList();
    }

    private static Map<PersonCategory, String> positionProfiles() {
        Map<PersonCategory, String> profiles = new EnumMap<>(PersonCategory.class);
        profiles.put(PersonCategory.WORKER, "mto-position-worker");
        profiles.put(PersonCategory.TEAM_LEADER, "mto-position-team-leader");
        profiles.put(PersonCategory.SUPERVISOR, "mto-position-supervisor");
        profiles.put(PersonCategory.CHIEF_SUPERVISOR, "mto-position-chief-supervisor");
        profiles.put(PersonCategory.CHIEF_MAINTENANCE, "mto-position-chief-maintenance");
        profiles.put(PersonCategory.PROJECT_MANAGER, "mto-position-project-manager");
        return Map.copyOf(profiles);
    }
}
