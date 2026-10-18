package com.alejandro.mtoresources.application.service.impl;

import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataOperation;
import com.alejandro.mtoresources.application.service.IncomingEventHandler;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MasterDataRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.persistence.repository.TrackAccessPointRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.number;
import static com.alejandro.mtoresources.application.service.impl.MaintenanceShiftEventHandler.text;

/**
 * Los puntos de acceso a la via de {@code mto-configuration} ({@code mto.master-data.track-access-point.*}):
 * la instantanea {@code track_access_point} que miran las reglas de la flota al registrar donde encarrila
 * una bivial. Corre dentro de la transaccion del inbox, asi que una reentrega no lo repite, y escribe solo
 * con el upsert nativo de {@link TrackAccessPointRepository}, con la marca de agua ({@code sequenceNumber})
 * dentro de la sentencia: un evento anterior a lo guardado no cambia nada.
 *
 * <ul>
 *   <li>{@code created} y {@code updated}: el acceso tal como viene, habilitado o no segun {@code enabled}.</li>
 *   <li>{@code deleted} (un borrado logico alli, con los mismos {@code values}): desactivado. Si el acceso no
 *       se conocia queda dado de alta desactivado, para que un alta que llegue tarde no lo resucite; si los
 *       {@code values} no bastan, solo se desactiva lo que ya habia.</li>
 * </ul>
 *
 * <p>Un {@code created} o {@code updated} sin lo que nunca falta (id, nombre, via, clase y lado) no se puede
 * aplicar: falla, y el mensaje acaba en la DLQ tras sus reintentos con el motivo en el inbox.</p>
 */
@Component
@RequiredArgsConstructor
class TrackAccessPointMasterDataHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackAccessPointMasterDataHandler.class);

    static final String TRACK_ACCESS_POINT = "track-access-point";

    private final TrackAccessPointRepository repository;

    @Override
    public String sourceService() {
        return MasterDataRabbitMqNames.CONFIGURATION_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return TRACK_ACCESS_POINT;
    }

    /** Se une a la transaccion del inbox; llamado suelto, abre la suya. */
    @Override
    @Transactional
    public void handle(IncomingEvent event, IncomingEventContext context) {
        Map<String, Object> values = event.values() == null ? Map.of() : event.values();
        Long sequenceNumber = context.sequenceNumber();
        Optional<Long> id = number(values.get("id")).or(() -> number(event.entityId())).map(BigDecimal::longValue);
        boolean deleted = MasterDataOperation.DELETED.routingValue().equals(event.eventName());
        if (!deleted && !MasterDataOperation.CREATED.routingValue().equals(event.eventName())
                && !MasterDataOperation.UPDATED.routingValue().equals(event.eventName())) {
            LOGGER.debug("Track access point event {} ignored", event.eventName());
            return;
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Track access point event without an id: " + event.entityId());
        }

        Optional<String> name = text(values.get("name"));
        Map<?, ?> track = values.get("track") instanceof Map<?, ?> map ? map : Map.of();
        Optional<Long> trackId = number(track.get("id")).map(BigDecimal::longValue);
        Optional<String> kind = text(values.get("kind"));
        Optional<String> side = text(values.get("side"));
        if (name.isEmpty() || trackId.isEmpty() || kind.isEmpty() || side.isEmpty()) {
            if (deleted) {
                int written = repository.deactivateFromMasterData(id.get(), sequenceNumber);
                LOGGER.info("Track access point {} deleted at the source, without its values: {}", id.get(),
                        written == 1 ? "disabled" : "unknown or older than what is kept");
                return;
            }
            throw new IllegalArgumentException("Track access point " + id.get() + " " + event.eventName()
                    + " without its name, track, kind or side: " + values);
        }

        Map<?, ?> station = values.get("station") instanceof Map<?, ?> map ? map : Map.of();
        BigDecimal latitude = number(values.get("latitude")).orElse(null);
        BigDecimal longitude = number(values.get("longitude")).orElse(null);
        if (latitude == null || longitude == null) {
            // Viajan juntas; una sola no dice donde esta.
            latitude = null;
            longitude = null;
        }
        boolean enabled = !deleted && bool(values.get("enabled")).orElse(true);
        int written = repository.upsertFromMasterData(
                id.get(),
                name.get(),
                trackId.get(),
                number(track.get("executionPackageId")).map(BigDecimal::longValue).orElse(null),
                number(station.get("id")).map(BigDecimal::longValue).orElse(null),
                number(values.get("kp")).orElse(null),
                kind.get(),
                side.get(),
                bool(values.get("roadRailAccess")).orElse(true),
                number(values.get("maxAxleLoadT")).orElse(null),
                number(values.get("maxGrossWeightT")).orElse(null),
                latitude,
                longitude,
                enabled,
                sequenceNumber);
        if (written == 0) {
            LOGGER.info("Track access point {} {} with sequence {} is older than what is kept: ignored", id.get(), event.eventName(),
                    sequenceNumber);
        } else {
            LOGGER.debug("Track access point {} {}: {} on track {}, enabled={}", id.get(), event.eventName(), name.get(), trackId.get(),
                    enabled);
        }
    }

    /** Un booleano del evento: {@code true}/{@code false} o su texto; cualquier otra cosa no se sabe. */
    private static Optional<Boolean> bool(Object value) {
        if (value instanceof Boolean flag) {
            return Optional.of(flag);
        }
        return text(value).filter(flag -> flag.equalsIgnoreCase("true") || flag.equalsIgnoreCase("false")).map(Boolean::valueOf);
    }
}
