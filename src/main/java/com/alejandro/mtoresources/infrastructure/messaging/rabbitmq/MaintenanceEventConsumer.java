package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

import com.alejandro.mtoresources.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtoresources.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtoresources.application.service.IncomingEventProcessor;
import com.alejandro.mtoresources.configuration.messaging.MessagePayloadSignatureVerifier;
import com.alejandro.mtoresources.infrastructure.persistence.audit.MessagingAuditContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Lee de su cola los eventos de turno de {@code mto-maintenance} y los entrega al procesador
 * idempotente. Es delgado a proposito, como el consumidor de datos maestros de {@code mto-maintenance}
 * del que es copia: comprueba la firma, que el sobre trae lo minimo y que viene de quien dice, traduce
 * los metadatos de AMQP a un comando y delega.
 *
 * <p>Lo que no tiene arreglo (una firma que no cuadra, un sobre sin {@code data}, un emisor que no es
 * {@code mto-maintenance}) va a la DLQ sin reintentos con {@link AmqpRejectAndDontRequeueException}; lo
 * que lance el manejador se propaga para que el contenedor reintente. Nunca se traga una excepcion:
 * el contenedor confirmaria un evento que no se ha aplicado.</p>
 */
@RequiredArgsConstructor
public class MaintenanceEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceEventConsumer.class);

    private final IncomingEventProcessor processor;
    private final MessagePayloadSignatureVerifier signatureVerifier;

    @RabbitListener(
            queues = "${app.rabbitmq.maintenance.queue:" + MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_QUEUE + "}",
            containerFactory = RabbitListenerContainerFactoryNames.MAINTENANCE)
    public void onMaintenanceEvent(IncomingEventMessage message, Message rawMessage) {
        MessageProperties properties = rawMessage.getMessageProperties();

        LOGGER.info("Maintenance event received: exchange={}, routingKey={}, queue={}, messageId={}, eventType={}, "
                        + "aggregateType={}, aggregateId={}, sequenceNumber={}",
                properties.getReceivedExchange(), properties.getReceivedRoutingKey(), properties.getConsumerQueue(),
                properties.getMessageId(), properties.getHeader(SourceMessageHeaders.EVENT_TYPE),
                properties.getHeader(SourceMessageHeaders.AGGREGATE_TYPE), properties.getHeader(SourceMessageHeaders.AGGREGATE_ID),
                properties.getHeader(SourceMessageHeaders.SEQUENCE_NUMBER));

        rejectIfNotAuthentic(rawMessage, properties);
        rejectIfUnprocessable(message, properties);

        InboxMessageCommand command = InboxMessageCommandFactory.from(message, rawMessage);

        MessagingAuditContext.set(new MessagingAuditContext.Context(command.messageId(), command.sourceService()));
        InboxProcessingResult result;
        try {
            result = processor.process(command, message);
        } finally {
            MessagingAuditContext.clear();
        }

        LOGGER.info("Maintenance event settled: messageId={}, idempotencyKey={}, result={}",
                properties.getMessageId(), command.messageId(), result);
    }

    private void rejectIfNotAuthentic(Message rawMessage, MessageProperties properties) {
        signatureVerifier
                .rejectionReason(rawMessage.getBody(),
                        header(properties, SourceMessageHeaders.SIGNATURE),
                        header(properties, SourceMessageHeaders.SIGNATURE_ALGORITHM))
                .ifPresent(reason -> {
                    throw unprocessable("was rejected because " + reason, properties);
                });
    }

    private static void rejectIfUnprocessable(IncomingEventMessage message, MessageProperties properties) {
        if (message == null || message.data() == null) {
            throw unprocessable("has no 'data' payload", properties);
        }
        if (!MaintenanceRabbitMqNames.MAINTENANCE_SOURCE_SERVICE.equals(message.origin())) {
            throw unprocessable("comes from '" + message.origin() + "', not from mto-maintenance", properties);
        }
        if (message.data().entityName() == null || message.data().entityName().isBlank()) {
            throw unprocessable("has no entityName", properties);
        }
        if (message.data().eventName() == null || message.data().eventName().isBlank()) {
            throw unprocessable("has no eventName", properties);
        }
    }

    private static String header(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        return value == null ? null : value.toString();
    }

    private static AmqpRejectAndDontRequeueException unprocessable(String reason, MessageProperties properties) {
        LOGGER.error("Maintenance event sent to the dead letter queue, it {}: messageId={}, exchange={}, routingKey={}",
                reason, properties.getMessageId(), properties.getReceivedExchange(), properties.getReceivedRoutingKey());
        return new AmqpRejectAndDontRequeueException("Maintenance event " + reason + ": messageId=" + properties.getMessageId());
    }
}
