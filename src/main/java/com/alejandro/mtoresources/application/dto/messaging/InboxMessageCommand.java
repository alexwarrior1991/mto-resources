package com.alejandro.mtoresources.application.dto.messaging;

/**
 * Everything the inbox needs to record one incoming message, already free of AMQP types.
 *
 * <p>Lo construye la capa de infraestructura, que es la que sabe leer cabeceras y metadatos de
 * RabbitMQ; el servicio de inbox trabaja solo con este record y no conoce el transporte. Si mañana
 * el mismo evento llega por otro canal, el inbox no cambia.</p>
 *
 * <p>Ejemplo, lo que {@code InboxMessageCommandFactory.from} saca de un {@code shift.started} de mto-maintenance:</p>
 * <pre>{@code
 * new InboxMessageCommand(operationId, "mto-maintenance", "MAINTENANCE_SHIFT_STARTED", "shift", "…",
 *         "mto.maintenance.exchange", "mto.maintenance.shift.started", "mto.resources.maintenance.queue",
 *         sha256(payload), payload, 1842L)
 * }</pre>
 *
 * @param messageId    clave de idempotencia; obligatoria y nunca truncada
 * @param sourceService servicio emisor; obligatorio, forma parte de la clave única
 * @param eventType    tipo de evento, si el mensaje lo trae
 * @param aggregateType tipo de la entidad publicada
 * @param aggregateId  identificador de la entidad publicada
 * @param exchangeName exchange por el que llegó
 * @param routingKey   routing key con la que llegó
 * @param queueName    cola de la que se consumió
 * @param payloadHash  huella del payload; dato auxiliar, nunca clave de idempotencia
 * @param payload      JSON original recibido, sin reserializar
 * @param sequenceNumber número del mensaje en la secuencia del emisor, o {@code null} si no viajaba
 */
public record InboxMessageCommand(
        String messageId,
        String sourceService,
        String eventType,
        String aggregateType,
        String aggregateId,
        String exchangeName,
        String routingKey,
        String queueName,
        String payloadHash,
        String payload,
        Long sequenceNumber
) {
}
