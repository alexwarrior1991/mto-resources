package com.alejandro.mtoresources.infrastructure.messaging;

import com.alejandro.mtoresources.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtoresources.application.dto.messaging.MessageActor;
import com.alejandro.mtoresources.application.service.IncomingEventProcessor;
import com.alejandro.mtoresources.configuration.messaging.MessagePayloadSignatureVerifier;
import com.alejandro.mtoresources.configuration.messaging.MessageSignatureMode;
import com.alejandro.mtoresources.configuration.messaging.MessageSignatureProperties;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.SourceMessageHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El consumidor de los turnos de {@code mto-maintenance} y los nombres del canal propio. */
class MessagingLayerTest {

    private final IncomingEventProcessor processor = mock(IncomingEventProcessor.class);
    private final MaintenanceEventConsumer consumer = new MaintenanceEventConsumer(processor,
            new MessagePayloadSignatureVerifier(new MessageSignatureProperties(null, MessageSignatureMode.OPTIONAL)));

    @Test
    void aShiftEventOfMaintenanceReachesTheInboxWithItsSequenceNumber() {
        IncomingEventMessage message = message("mto-maintenance", new IncomingEvent("shift", UUID.randomUUID().toString(), "started", Map.of()));

        consumer.onMaintenanceEvent(message, raw(42L));

        ArgumentCaptor<InboxMessageCommand> command = ArgumentCaptor.forClass(InboxMessageCommand.class);
        verify(processor).process(command.capture(), any());
        assertEquals("mto-maintenance", command.getValue().sourceService());
        assertEquals(message.operationId().toString(), command.getValue().messageId());
        assertEquals(42L, command.getValue().sequenceNumber());
    }

    @Test
    void whatComesFromAnotherServiceOrWithoutDataGoesToTheDeadLetterQueue() {
        IncomingEvent event = new IncomingEvent("shift", UUID.randomUUID().toString(), "started", Map.of());
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.onMaintenanceEvent(message("mto-stock", event), raw(1L)));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.onMaintenanceEvent(message("mto-maintenance", null), raw(1L)));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.onMaintenanceEvent(
                message("mto-maintenance", new IncomingEvent("shift", "1", " ", Map.of())), raw(1L)));
        verify(processor, never()).process(any(), any());
    }

    @Test
    void theOwnEventsGoToTheirExchangeWithTheirRoutingKeyAndType() {
        assertEquals("mto.resources.exchange", ResourcesRabbitMqNames.RESOURCES_EXCHANGE);
        assertEquals("mto.resources.team.updated", ResourcesRabbitMqNames.routingKey("team", "updated"));
        assertEquals("RESOURCES_VEHICLE_DEACTIVATED", ResourcesRabbitMqNames.eventType("vehicle", "deactivated"));
        assertEquals("mto.resources.maintenance.queue", MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_QUEUE);
    }

    private static IncomingEventMessage message(String origin, IncomingEvent event) {
        return new IncomingEventMessage(UUID.randomUUID(), "shift-1", origin, Instant.now(), "MAINTENANCE_SHIFT_STARTED", event, "hash",
                MessageActor.of("u-1", "mantenimiento.tecnico"), "corr-1");
    }

    private static Message raw(Long sequenceNumber) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(UUID.randomUUID().toString());
        properties.setHeader(SourceMessageHeaders.SEQUENCE_NUMBER, sequenceNumber);
        properties.setHeader(SourceMessageHeaders.EVENT_TYPE, "MAINTENANCE_SHIFT_STARTED");
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
