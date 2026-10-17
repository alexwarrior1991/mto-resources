package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.fleet.VehicleLocationRequest;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtoresources.application.exception.BusinessException;
import com.alejandro.mtoresources.application.service.IncomingEventHandler;
import com.alejandro.mtoresources.application.service.VehicleLocationService;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.FieldRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.persistence.entity.Vehicle;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationSource;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.instant;
import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.number;
import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.text;
import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.uuid;

/**
 * El kp en el que un dispositivo de {@code mto-field} dice que esta el vehiculo de su turno
 * ({@code mto.field.position.reported}): queda {@code ON_TRACK} en esa via y ese kp, con origen
 * {@code FIELD} y la misma referencia que el turno ({@code mto-maintenance:shift:<id>}), para que el
 * cierre del turno lo siga aparcando. La marca de agua de la posicion hace el resto: una posicion que
 * llega tarde queda en el historico y no mueve nada.
 *
 * <p>Lo que no se puede aplicar se registra con un WARN y se ignora, nunca va a la DLQ ni se reintenta:
 * un vehiculo que no esta en la flota o esta retirado, una posicion sin via o sin turno, lo que
 * {@code FleetRules} rechaza (un vehiculo de carretera no esta en la via) y la de un turno que ya ha
 * salido de la via (su vehiculo esta aparcado, o sin saber donde, por ese mismo turno): el campo deja de
 * publicar al salir, y lo que llegue despues no puede devolver el vehiculo a la via.</p>
 */
@Component
@RequiredArgsConstructor
class FieldPositionHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FieldPositionHandler.class);

    static final String POSITION = "position";
    static final String REPORTED = "reported";

    private final VehicleRepository vehicleRepository;
    private final VehiclePositionRepository positionRepository;
    private final VehicleLocationService locationService;
    private final Clock clock;

    @Override
    public String sourceService() {
        return FieldRabbitMqNames.FIELD_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return POSITION;
    }

    /** Se une a la transaccion del inbox; llamado suelto, abre la suya. */
    @Override
    @Transactional
    public void handle(IncomingEvent event, IncomingEventContext context) {
        if (!REPORTED.equals(event.eventName())) {
            LOGGER.debug("Field position event {} ignored", event.eventName());
            return;
        }
        Map<String, Object> values = event.values() == null ? Map.of() : event.values();
        Optional<UUID> vehicleId = uuid(values.get("vehicleId"));
        Optional<UUID> shiftId = uuid(values.get("shiftId"));
        Optional<Long> trackId = number(values.get("trackId")).map(BigDecimal::longValue);
        if (vehicleId.isEmpty() || shiftId.isEmpty() || trackId.isEmpty()) {
            LOGGER.warn("Field position {} without a vehicle, a shift or a track: ignored ({})", event.entityId(), values);
            return;
        }
        Optional<Vehicle> vehicle = vehicleRepository.findById(vehicleId.get());
        if (vehicle.isEmpty() || !Boolean.TRUE.equals(vehicle.get().getActive())) {
            LOGGER.warn("Field position of vehicle {}, which is {}: ignored", vehicleId.get(), vehicle.isEmpty() ? "not in the fleet" : "retired");
            return;
        }
        String reference = "mto-maintenance:shift:" + shiftId.get();
        String code = vehicle.get().getCode();
        boolean shiftLeftTheTrack = positionRepository.findById(vehicleId.get())
                .filter(position -> reference.equals(position.getSourceReference()))
                .filter(position -> position.getKind() == VehicleLocationKind.PARKED || position.getKind() == VehicleLocationKind.UNKNOWN)
                .isPresent();
        if (shiftLeftTheTrack) {
            LOGGER.warn("Field position of vehicle {} after shift {} left the track: ignored", code, shiftId.get());
            return;
        }
        Instant now = clock.instant();
        Instant observedAt = instant(values.get("observedAt")).filter(instant -> instant.isBefore(now)).orElse(now);
        VehicleLocationRequest request = new VehicleLocationRequest(VehicleLocationKind.ON_TRACK, null,
                number(values.get("executionPackageId")).map(BigDecimal::longValue).orElse(null), trackId.get(),
                number(values.get("kp")).orElse(null), null, null, null,
                text(values.get("code")).map(possession -> "Reported by the field device of possession " + possession).orElse(null),
                null, null, observedAt);
        try {
            locationService.record(vehicleId.get(), request, VehicleLocationSource.FIELD, reference);
        } catch (BusinessException rejected) {
            LOGGER.warn("Field position of vehicle {} not accepted: {}", code, rejected.getMessage());
        }
    }
}
