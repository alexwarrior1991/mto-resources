package com.alejandro.mtoresources.application.dto.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * El sobre de un cambio de datos maestros de {@code mto-configuration} ({@code mto.master-data.exchange}):
 * el {@code AsynchronousMessage} comun, fijado a {@link MasterDataChangedEvent}.
 *
 * <p>Se convierte en un {@link IncomingEventMessage} ({@link #toIncomingEvent()}) para pasar por el mismo
 * inbox y el mismo despachador que los eventos de los demas servicios: la operacion, en minusculas, hace de
 * {@code eventName} ({@code created}, {@code updated}, {@code deleted}), como en la clave de enrutado.</p>
 *
 * <p>{@code messageHash} no es una firma: la que se comprueba viaja en la cabecera
 * {@code messageSignature}, sobre los bytes reales.</p>
 */
public record MasterDataChangedMessage(
        UUID operationId,
        String referenceId,
        String origin,
        Instant creationDate,
        String eventType,
        MasterDataChangedEvent data,
        String messageHash,
        MessageActor actor,
        String correlationId
) {

    public IncomingEventMessage toIncomingEvent() {
        IncomingEvent event = data == null ? null : new IncomingEvent(data.entityName(), data.entityId(),
                data.operation() == null ? null : data.operation().routingValue(), data.values());
        return new IncomingEventMessage(operationId, referenceId, origin, creationDate, eventType, event, messageHash, actor, correlationId);
    }
}
