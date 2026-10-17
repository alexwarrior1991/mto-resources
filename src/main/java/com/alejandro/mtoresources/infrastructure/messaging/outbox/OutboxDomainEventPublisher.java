package com.alejandro.mtoresources.infrastructure.messaging.outbox;

import com.alejandro.mtoresources.application.dto.messaging.AsynchronousMessage;
import com.alejandro.mtoresources.application.dto.messaging.DomainEvent;
import com.alejandro.mtoresources.application.service.DomainEventPublisher;
import com.alejandro.mtoresources.configuration.rabbitmq.ResourcesEventsProperties;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Escribe cada evento propio en el outbox, envuelto en el sobre comun, para que el relay lo publique
 * en {@code mto.resources.exchange} con la clave {@code mto.resources.<entidad>.<evento>}.
 *
 * <p>El agregado del outbox es la entidad del evento ({@code team}-{@code <id>}), de modo que el
 * orden estricto por agregado del relay conserva {@code created} antes que {@code status-changed}
 * de la misma orden aunque el primero falle y se reintente.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final AsynchronousMessageFactory messageFactory;
    private final OutboxService outboxService;
    private final ResourcesEventsProperties properties;

    @Override
    public void publish(DomainEvent event) {
        publish(UUID.randomUUID(), event);
    }

    @Override
    public void publish(UUID operationId, DomainEvent event) {
        String eventType = ResourcesRabbitMqNames.eventType(event.entityName(), event.eventName());

        AsynchronousMessage<DomainEvent> message = messageFactory.create(
                operationId,
                event.entityName() + "-" + event.entityId(),
                eventType,
                event
        );

        outboxService.save(
                event.entityName(),
                event.entityId(),
                eventType,
                properties.exchange(),
                ResourcesRabbitMqNames.routingKey(event.entityName(), event.eventName()),
                message
        );

        log.debug("Domain event in the outbox: {} {} {} (operationId={}, actor={})",
                event.entityName(), event.entityId(), event.eventName(), operationId, message.actor());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
