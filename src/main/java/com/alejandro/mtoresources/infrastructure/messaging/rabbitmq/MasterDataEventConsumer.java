package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

import com.alejandro.mtoresources.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtoresources.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataChangedMessage;
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
 * Lee de su cola los datos maestros de {@code mto-configuration} (hoy solo los puntos de acceso a la via) y
 * los entrega al procesador idempotente, por el mismo inbox ({@code source_service = mto-configuration}) y
 * el mismo despachador que los eventos de los demas servicios. Es la copia de {@link MaintenanceEventConsumer}
 * con el sobre de los datos maestros, que dice {@code operation} donde los otros dicen {@code eventName}:
 * comprueba la firma, que el sobre trae lo minimo y que viene de quien dice, lo convierte y delega.
 *
 * <p>Lo que no tiene arreglo (una firma que no cuadra, un sobre sin {@code data}, sin entidad o sin
 * operacion, un emisor que no es {@code mto-configuration}) va a la DLQ sin reintentos con
 * {@link AmqpRejectAndDontRequeueException}; lo que lance el manejador se propaga para que el contenedor
 * reintente. Nunca se traga una excepcion: el contenedor confirmaria un evento que no se ha aplicado.</p>
 */
@RequiredArgsConstructor
public class MasterDataEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MasterDataEventConsumer.class);

    private final IncomingEventProcessor processor;
    private final MessagePayloadSignatureVerifier signatureVerifier;

    @RabbitListener(
            queues = "${app.rabbitmq.master-data.queue:" + MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_QUEUE + "}",
            containerFactory = RabbitListenerContainerFactoryNames.MASTER_DATA)
    public void onMasterDataChanged(MasterDataChangedMessage message, Message rawMessage) {
        MessageProperties properties = rawMessage.getMessageProperties();

        LOGGER.info("Master data message received: exchange={}, routingKey={}, queue={}, messageId={}, eventType={}, "
                        + "aggregateType={}, aggregateId={}, sequenceNumber={}",
                properties.getReceivedExchange(), properties.getReceivedRoutingKey(), properties.getConsumerQueue(),
                properties.getMessageId(), properties.getHeader(SourceMessageHeaders.EVENT_TYPE),
                properties.getHeader(SourceMessageHeaders.AGGREGATE_TYPE), properties.getHeader(SourceMessageHeaders.AGGREGATE_ID),
                properties.getHeader(SourceMessageHeaders.SEQUENCE_NUMBER));

        rejectIfNotAuthentic(rawMessage, properties);
        rejectIfUnprocessable(message, properties);

        IncomingEventMessage incoming = message.toIncomingEvent();
        InboxMessageCommand command = InboxMessageCommandFactory.from(incoming, rawMessage);

        MessagingAuditContext.set(new MessagingAuditContext.Context(command.messageId(), command.sourceService()));
        InboxProcessingResult result;
        try {
            result = processor.process(command, incoming);
        } finally {
            MessagingAuditContext.clear();
        }

        LOGGER.info("Master data message settled: messageId={}, idempotencyKey={}, result={}",
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

    private static void rejectIfUnprocessable(MasterDataChangedMessage message, MessageProperties properties) {
        if (message == null || message.data() == null) {
            throw unprocessable("has no 'data' payload", properties);
        }
        if (!MasterDataRabbitMqNames.CONFIGURATION_SOURCE_SERVICE.equals(message.origin())) {
            throw unprocessable("comes from '" + message.origin() + "', not from mto-configuration", properties);
        }
        if (message.data().entityName() == null || message.data().entityName().isBlank()) {
            throw unprocessable("has no entityName, so there is no way to know what changed", properties);
        }
        if (message.data().operation() == null) {
            throw unprocessable("has no operation, so there is no way to know what happened to it", properties);
        }
    }

    private static String header(MessageProperties properties, String name) {
        Object value = properties.getHeader(name);
        return value == null ? null : value.toString();
    }

    private static AmqpRejectAndDontRequeueException unprocessable(String reason, MessageProperties properties) {
        LOGGER.error("Master data message sent to the dead letter queue, it {}: messageId={}, exchange={}, routingKey={}",
                reason, properties.getMessageId(), properties.getReceivedExchange(), properties.getReceivedRoutingKey());
        return new AmqpRejectAndDontRequeueException("Master data message " + reason + ": messageId=" + properties.getMessageId());
    }
}
