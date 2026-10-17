package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtoresources.application.exception.BusinessException;
import com.alejandro.mtoresources.application.service.IncomingEventHandler;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehiclePosition;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehiclePositionRepository;
import com.alejandro.mtoresources.infrastructure.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Donde quedan los vehiculos de un turno de {@code mto-maintenance} ({@code mto.maintenance.shift.*}).
 * Corre dentro de la transaccion del inbox, asi que una reentrega no lo repite; y cada ubicacion lleva
 * {@code sourceReference = mto-maintenance:shift:<id>} y su {@code observedAt}, asi que una republicacion
 * tampoco.
 *
 * <ul>
 *   <li>{@code started}: los de via y los biviales quedan {@code ON_TRACK} en la primera via del turno,
 *       en su {@code startKp} (en una bivial es encarrilar); los de carretera, {@code ON_ROAD} en su EP.</li>
 *   <li>{@code closed}, y {@code cancelled} de un turno que llego a empezar: {@code PARKED} donde el turno
 *       dice que se dejo cada uno ({@code vehicles[].parking}), si no en su {@code parkingPlace} (y, en uno de
 *       solo via, en su primera via), y si no {@code UNKNOWN}. Solo los que siguen donde los dejo ese
 *       turno: uno que alguien ya movio a mano no se toca. Lo enganchado a ellos los sigue solo.</li>
 * </ul>
 *
 * <p>Un evento sin {@code vehicleIds} (un mantenimiento anterior) no mueve nada. Lo que una regla de la
 * flota rechaza se apunta como {@code UNKNOWN} con el motivo, en lugar de mandar el mensaje a la DLQ:
 * lo fino (donde encarrilo, en que kp desencarrilo) se corrige a mano.</p>
 */
@Component
@RequiredArgsConstructor
class MaintenanceShiftEventHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceShiftEventHandler.class);

    static final String SHIFT = "shift";
    static final String STARTED = "started";
    static final String CLOSED = "closed";
    static final String CANCELLED = "cancelled";

    private final VehicleRepository vehicleRepository;
    private final VehiclePositionRepository positionRepository;
    private final VehicleLocationService locationService;
    private final Clock clock;

    @Override
    public String sourceService() {
        return MaintenanceRabbitMqNames.MAINTENANCE_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return SHIFT;
    }

    /** Se une a la transaccion del inbox; llamado suelto, abre la suya. */
    @Override
    @Transactional
    public void handle(IncomingEvent event, IncomingEventContext context) {
        Map<String, Object> values = event.values() == null ? Map.of() : event.values();
        List<UUID> vehicleIds = uuids(values.get("vehicleIds"));
        if (vehicleIds.isEmpty()) {
            LOGGER.debug("Shift {} {} carries no vehicles: nothing moves", event.entityId(), event.eventName());
            return;
        }
        String reference = "mto-maintenance:shift:" + event.entityId();
        String code = text(values.get("code")).orElse(event.entityId());
        switch (event.eventName()) {
            case STARTED -> started(values, vehicleIds, reference);
            case CLOSED -> ended(values, vehicleIds, reference, code);
            case CANCELLED -> {
                if (values.get("actualStart") != null) {
                    ended(values, vehicleIds, reference, code);
                }
            }
            default -> LOGGER.debug("Shift event {} ignored", event.eventName());
        }
    }

    private void started(Map<String, Object> values, List<UUID> vehicleIds, String reference) {
        Instant observedAt = observedAt(values.get("actualStart"));
        Long trackId = firstTrack(values);
        Long executionPackageId = number(values.get("executionPackageId")).map(BigDecimal::longValue).orElse(null);
        BigDecimal kp = number(values.get("startKp")).orElse(null);
        for (UUID vehicleId : vehicleIds) {
            vehicle(vehicleId).ifPresent(vehicle -> {
                VehicleMobility mobility = vehicle.getType().getMobility();
                VehicleLocationRequest request = mobility.runsOnTrack() && trackId != null
                        ? location(VehicleLocationKind.ON_TRACK, null, executionPackageId, trackId, kp, null, observedAt)
                        : location(VehicleLocationKind.ON_ROAD, null, executionPackageId, null, null, null, observedAt);
                record(vehicle, request, reference, observedAt);
            });
        }
    }

    private void ended(Map<String, Object> values, List<UUID> vehicleIds, String reference, String code) {
        Instant observedAt = observedAt(values.get("actualEnd"));
        Long executionPackageId = number(values.get("executionPackageId")).map(BigDecimal::longValue).orElse(null);
        Long firstTrack = firstTrack(values);
        Optional<String> parkingPlace = text(values.get("parkingPlace"));
        Map<UUID, Map<?, ?>> parkings = parkings(values.get("vehicles"));
        for (UUID vehicleId : vehicleIds) {
            Optional<Vehicle> found = vehicle(vehicleId);
            if (found.isEmpty()) {
                continue;
            }
            Vehicle vehicle = found.get();
            Optional<VehiclePosition> position = positionRepository.findById(vehicleId);
            if (position.isPresent() && !reference.equals(position.get().getSourceReference())) {
                LOGGER.debug("Vehicle {} was moved after shift {}: it stays where it is", vehicle.getCode(), code);
                continue;
            }
            Map<?, ?> parking = parkings.get(vehicleId);
            VehicleLocationRequest request;
            if (parking != null && (parking.get("siteId") != null || parking.get("trackId") != null || parking.get("description") != null)) {
                request = location(VehicleLocationKind.PARKED, uuid(parking.get("siteId")).orElse(null), executionPackageId,
                        number(parking.get("trackId")).map(BigDecimal::longValue).orElse(null),
                        number(parking.get("kp")).orElse(null), text(parking.get("description")).orElse(null), observedAt);
            } else if (parkingPlace.isPresent()) {
                Long track = vehicle.getType().getMobility() == VehicleMobility.RAIL_ONLY ? firstTrack : null;
                request = location(VehicleLocationKind.PARKED, null, executionPackageId, track, null, parkingPlace.get(), observedAt);
            } else {
                request = location(VehicleLocationKind.UNKNOWN, null, executionPackageId, null, null, "After shift " + code, observedAt);
            }
            record(vehicle, request, reference, observedAt);
        }
    }

    /** Lo que una regla rechaza queda como desconocido, con el motivo: el turno no se reintenta por ello. */
    private void record(Vehicle vehicle, VehicleLocationRequest request, String reference, Instant observedAt) {
        try {
            locationService.record(vehicle.getId(), request, VehicleLocationSource.SHIFT, reference);
        } catch (BusinessException rejected) {
            LOGGER.warn("Vehicle {}: {} from the shift was not accepted ({}); recorded as UNKNOWN", vehicle.getCode(), request.kind(),
                    rejected.getMessage());
            if (request.kind() != VehicleLocationKind.UNKNOWN && Boolean.TRUE.equals(vehicle.getActive())) {
                String description = truncate(rejected.getMessage(), 500);
                locationService.record(vehicle.getId(),
                        location(VehicleLocationKind.UNKNOWN, null, request.executionPackageId(), null, null, description, observedAt),
                        VehicleLocationSource.SHIFT, reference);
            }
        }
    }

    /** Lo que dice el turno, nunca despues de ahora: un reloj adelantado alla no deja la ubicacion en el futuro. */
    private Instant observedAt(Object value) {
        Instant now = clock.instant();
        return instant(value).filter(instant -> instant.isBefore(now)).orElse(now);
    }

    private Optional<Vehicle> vehicle(UUID id) {
        Optional<Vehicle> vehicle = vehicleRepository.findById(id);
        if (vehicle.isEmpty()) {
            LOGGER.warn("Shift names vehicle {}, which is not in the fleet: ignored", id);
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(vehicle.get().getActive())) {
            LOGGER.warn("Shift names vehicle {}, which is retired: ignored", vehicle.get().getCode());
            return Optional.empty();
        }
        return vehicle;
    }

    private static VehicleLocationRequest location(VehicleLocationKind kind, UUID siteId, Long executionPackageId, Long trackId,
                                                   BigDecimal kp, String description, Instant observedAt) {
        return new VehicleLocationRequest(kind, siteId, executionPackageId, trackId, kp, null, null, null, description, null, null,
                observedAt);
    }

    private static Long firstTrack(Map<String, Object> values) {
        if (values.get("trackIds") instanceof List<?> tracks && !tracks.isEmpty()) {
            return number(tracks.getFirst()).map(BigDecimal::longValue).orElse(null);
        }
        return null;
    }

    private static Map<UUID, Map<?, ?>> parkings(Object vehicles) {
        Map<UUID, Map<?, ?>> parkings = new HashMap<>();
        if (vehicles instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> vehicle && vehicle.get("parking") instanceof Map<?, ?> parking) {
                    uuid(vehicle.get("id")).ifPresent(id -> parkings.put(id, parking));
                }
            }
        }
        return parkings;
    }

    private static List<UUID> uuids(Object value) {
        List<UUID> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> uuid(item).ifPresent(ids::add));
        }
        return ids;
    }

    private static Optional<UUID> uuid(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.toString()));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> number(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.toString()));
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> instant(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.toString()));
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<String> text(Object value) {
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.toString().trim());
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
