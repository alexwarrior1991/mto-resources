package com.alejandro.mtoresources.infrastructure.messaging;

import com.alejandro.mtoresources.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataChangedEvent;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataChangedMessage;
import com.alejandro.mtoresources.application.dto.messaging.MasterDataOperation;
import com.alejandro.mtoresources.application.dto.messaging.MessageActor;
import com.alejandro.mtoresources.application.service.ExpiryWatchService;
import com.alejandro.mtoresources.application.service.IncomingEventProcessor;
import com.alejandro.mtoresources.configuration.expiry.ExpiryWatchConfiguration;
import com.alejandro.mtoresources.configuration.expiry.ExpiryWatchProperties;
import com.alejandro.mtoresources.configuration.messaging.MessagePayloadSignatureVerifier;
import com.alejandro.mtoresources.configuration.messaging.MessageSignatureMode;
import com.alejandro.mtoresources.configuration.messaging.MessageSignatureProperties;
import com.alejandro.mtoresources.configuration.rabbitmq.ConsumerQueueProperties;
import com.alejandro.mtoresources.configuration.rabbitmq.FieldRabbitProperties;
import com.alejandro.mtoresources.configuration.rabbitmq.MaintenanceRabbitProperties;
import com.alejandro.mtoresources.configuration.rabbitmq.MasterDataRabbitProperties;
import com.alejandro.mtoresources.configuration.rabbitmq.RabbitMqConfiguration;
import com.alejandro.mtoresources.configuration.rabbitmq.ResourcesEventsProperties;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.FieldEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.FieldRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MasterDataEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MasterDataRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.SourceMessageHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Los consumidores de los turnos de {@code mto-maintenance}, de las posiciones de {@code mto-field} y de los
 * puntos de acceso a la via de {@code mto-configuration}, su topologia, los nombres del canal propio y cuando se
 * planifica el vigilante de caducidades.
 */
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
    void aPositionOfFieldReachesTheInboxAndOneFromAnotherServiceGoesToTheDeadLetterQueue() {
        FieldEventConsumer field = new FieldEventConsumer(processor,
                new MessagePayloadSignatureVerifier(new MessageSignatureProperties(null, MessageSignatureMode.OPTIONAL)));
        IncomingEvent position = new IncomingEvent("position", UUID.randomUUID().toString(), "reported", Map.of());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> field.onFieldEvent(message("mto-maintenance", position), raw(1L)));
        verify(processor, never()).process(any(), any());
        field.onFieldEvent(message("mto-field", position), raw(7L));
        ArgumentCaptor<InboxMessageCommand> command = ArgumentCaptor.forClass(InboxMessageCommand.class);
        verify(processor).process(command.capture(), any());
        assertEquals("mto-field", command.getValue().sourceService());
    }

    /**
     * Sin decir nada, las tres colas (turnos, posiciones y puntos de acceso) y sus DLQ se declaran
     * exactamente como antes: clasicas, sin {@code x-queue-type} ni {@code x-delivery-limit}. Un argumento de mas
     * haria que el broker rechazase la redeclaracion de una cola ya creada y se cayese toda la topologia.
     */
    @Test
    void byDefaultTheThreeQueuesAndTheirDeadLettersAreClassicAndDeclaredAsBefore() {
        RabbitMqConfiguration topology = topology();

        assertEquals(Map.of(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, "mto.resources.maintenance.queue.dlx",
                        MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, "mto.resources.maintenance.queue.dlq"),
                topology.maintenanceQueue().getArguments());
        assertEquals(Map.of(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, "mto.resources.field.queue.dlx",
                        MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, "mto.resources.field.queue.dlq"),
                topology.fieldQueue().getArguments());
        assertEquals(Map.of(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, "mto.resources.master-data.queue.dlx",
                        MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, "mto.resources.master-data.queue.dlq"),
                topology.masterDataQueue().getArguments());
        for (org.springframework.amqp.core.Queue deadLetters : List.of(topology.maintenanceDeadLetterQueue(),
                topology.fieldDeadLetterQueue(), topology.masterDataDeadLetterQueue())) {
            assertEquals(Map.of(), deadLetters.getArguments(), deadLetters.getName());
        }
    }

    /**
     * Con {@code app.rabbitmq.queues.type=quorum}, que es como corre en OpenShift, las tres colas son quorum con su
     * limite de entregas (5, o el que se diga) y sus DLQ tambien, sin limite; un limite por debajo de 1 no vale.
     */
    @Test
    void withQuorumTheThreeQueuesCarryTheirDeliveryLimitAndTheirDeadLettersAreQuorumToo() {
        RabbitMqConfiguration topology = topology(new ConsumerQueueProperties(ConsumerQueueProperties.QueueType.QUORUM, null));

        for (org.springframework.amqp.core.Queue queue : List.of(topology.maintenanceQueue(), topology.fieldQueue(),
                topology.masterDataQueue())) {
            assertEquals("quorum", queue.getArguments().get("x-queue-type"), queue.getName());
            assertEquals(ConsumerQueueProperties.DEFAULT_DELIVERY_LIMIT, queue.getArguments().get("x-delivery-limit"), queue.getName());
            assertEquals(queue.getName() + ".dlx", queue.getArguments().get(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE));
        }
        for (org.springframework.amqp.core.Queue deadLetters : List.of(topology.maintenanceDeadLetterQueue(),
                topology.fieldDeadLetterQueue(), topology.masterDataDeadLetterQueue())) {
            assertEquals(Map.of("x-queue-type", "quorum"), deadLetters.getArguments(), deadLetters.getName());
        }
        assertEquals(3, topology(new ConsumerQueueProperties(ConsumerQueueProperties.QueueType.QUORUM, 3))
                .fieldQueue().getArguments().get("x-delivery-limit"));
        assertThrows(IllegalArgumentException.class, () -> new ConsumerQueueProperties(ConsumerQueueProperties.QueueType.QUORUM, 0));
    }

    /** La cola de las posiciones es de este servicio, atada al exchange de telemetria de mto-field (no al de sus eventos) y con su descarte. */
    @Test
    void theQueueOfThePositionsIsBoundToTheTelemetryExchangeWithItsDeadLetter() {
        RabbitMqConfiguration topology = topology();

        org.springframework.amqp.core.Queue queue = topology.fieldQueue();
        assertEquals(FieldRabbitMqNames.RESOURCES_FIELD_QUEUE, queue.getName());
        assertEquals("mto.resources.field.queue.dlx", queue.getArguments().get(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE));
        assertEquals("mto.field.telemetry.exchange", topology.fieldTelemetryExchange().getName());
        org.springframework.amqp.core.Binding binding = topology.fieldBinding(queue, topology.fieldTelemetryExchange());
        assertEquals("mto.field.position.*", binding.getRoutingKey());
        assertEquals("mto.field.telemetry.exchange", binding.getExchange());
    }

    /**
     * Un punto de acceso de mto-configuration entra por el mismo inbox, con su emisor y su secuencia, y llega al
     * despachador como un evento cuya operacion, en minusculas, hace de {@code eventName}.
     */
    @Test
    void aTrackAccessPointOfConfigurationReachesTheInboxWithItsOperationAsTheEventName() {
        MasterDataEventConsumer masterData = new MasterDataEventConsumer(processor,
                new MessagePayloadSignatureVerifier(new MessageSignatureProperties(null, MessageSignatureMode.OPTIONAL)));
        MasterDataChangedMessage message = masterDataMessage("mto-configuration",
                new MasterDataChangedEvent("track-access-point", "77", MasterDataOperation.DELETED, Map.of("id", 77)));

        masterData.onMasterDataChanged(message, raw(1842L));

        ArgumentCaptor<InboxMessageCommand> command = ArgumentCaptor.forClass(InboxMessageCommand.class);
        ArgumentCaptor<IncomingEventMessage> delivered = ArgumentCaptor.forClass(IncomingEventMessage.class);
        verify(processor).process(command.capture(), delivered.capture());
        assertEquals("mto-configuration", command.getValue().sourceService());
        assertEquals(message.operationId().toString(), command.getValue().messageId());
        assertEquals(1842L, command.getValue().sequenceNumber());
        assertEquals("track-access-point", command.getValue().aggregateType());
        assertEquals("77", command.getValue().aggregateId());
        assertEquals("deleted", delivered.getValue().data().eventName());
        assertEquals(77, delivered.getValue().data().values().get("id"));
    }

    @Test
    void masterDataFromAnotherServiceOrWithoutEntityOrOperationGoesToTheDeadLetterQueue() {
        MasterDataEventConsumer masterData = new MasterDataEventConsumer(processor,
                new MessagePayloadSignatureVerifier(new MessageSignatureProperties(null, MessageSignatureMode.OPTIONAL)));
        MasterDataChangedEvent event = new MasterDataChangedEvent("track-access-point", "77", MasterDataOperation.UPDATED, Map.of());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> masterData.onMasterDataChanged(masterDataMessage("mto-stock", event),
                raw(1L)), "Another origin");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> masterData.onMasterDataChanged(masterDataMessage("mto-configuration",
                null), raw(1L)), "Without data");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> masterData.onMasterDataChanged(masterDataMessage("mto-configuration",
                new MasterDataChangedEvent(" ", "77", MasterDataOperation.UPDATED, Map.of())), raw(1L)), "Without entity");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> masterData.onMasterDataChanged(masterDataMessage("mto-configuration",
                new MasterDataChangedEvent("track-access-point", "77", null, Map.of())), raw(1L)), "Without operation");
        verify(processor, never()).process(any(), any());
    }

    /**
     * La cola de los datos maestros es de este servicio, atada al exchange de mto-configuration solo con los
     * puntos de acceso (no con {@code mto.master-data.#}: lo demas de la infraestructura no le dice nada) y con
     * su descarte.
     */
    @Test
    void theQueueOfTheMasterDataIsBoundOnlyToTheTrackAccessPointsWithItsDeadLetter() {
        RabbitMqConfiguration topology = topology();

        org.springframework.amqp.core.Queue queue = topology.masterDataQueue();
        assertEquals(MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_QUEUE, queue.getName());
        assertEquals("mto.resources.master-data.queue", queue.getName());
        assertEquals("mto.resources.master-data.queue.dlx", queue.getArguments().get(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE));
        assertEquals("mto.resources.master-data.queue.dlq", queue.getArguments().get(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY));
        assertEquals("mto.master-data.exchange", topology.masterDataExchange().getName());
        assertTrue(topology.masterDataExchange().isDurable());
        org.springframework.amqp.core.Binding binding = topology.masterDataBinding(queue, topology.masterDataExchange());
        assertEquals("mto.master-data.track-access-point.*", binding.getRoutingKey());
        assertEquals("mto.master-data.exchange", binding.getExchange());
        org.springframework.amqp.core.Binding deadLetter = topology.masterDataDeadLetterBinding(topology.masterDataDeadLetterQueue(),
                topology.masterDataDeadLetterExchange());
        assertEquals("mto.resources.master-data.queue.dlq", deadLetter.getDestination());
        assertEquals("mto.resources.master-data.queue.dlx", deadLetter.getExchange());
    }

    /**
     * El ejemplo que versiona mto-configuration ({@code docs/messaging/examples/master-data-track-access-point-updated.json}
     * de alli, copiado en {@code contracts/mto-configuration/}) se lee con el convertidor del canal: el sobre, la
     * operacion, el actor y los decimales como numeros.
     */
    @Test
    void theExampleOfConfigurationIsReadByTheConverterOfTheChannel() throws IOException {
        MessageConverter converter = topology().incomingEventMessageConverter(JsonMapper.builder().build());
        MessageProperties properties = new MessageProperties();
        properties.setInferredArgumentType(MasterDataChangedMessage.class);
        byte[] body;
        try (InputStream json = MessagingLayerTest.class.getResourceAsStream("/contracts/mto-configuration/master-data-track-access-point-updated.json")) {
            body = Objects.requireNonNull(json).readAllBytes();
        }

        MasterDataChangedMessage message = assertInstanceOf(MasterDataChangedMessage.class, converter.fromMessage(new Message(body, properties)));

        assertEquals("mto-configuration", message.origin());
        assertEquals(MasterDataOperation.UPDATED, message.data().operation());
        assertEquals("track-access-point", message.data().entityName());
        assertEquals("config.editor", message.actor().username());
        IncomingEvent event = message.toIncomingEvent().data();
        assertEquals("updated", event.eventName());
        assertEquals(0, new BigDecimal("110176.000").compareTo(new BigDecimal(event.values().get("kp").toString())));
        assertInstanceOf(Map.class, event.values().get("track"));
    }

    @Test
    void theOwnEventsGoToTheirExchangeWithTheirRoutingKeyAndType() {
        assertEquals("mto.resources.exchange", ResourcesRabbitMqNames.RESOURCES_EXCHANGE);
        assertEquals("mto.resources.team.updated", ResourcesRabbitMqNames.routingKey("team", "updated"));
        assertEquals("RESOURCES_VEHICLE_DEACTIVATED", ResourcesRabbitMqNames.eventType("vehicle", "deactivated"));
        assertEquals("mto.resources.maintenance.queue", MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_QUEUE);
    }

    @Test
    void theExpiryWarningsAndTheInstrumentsGoToTheOwnExchangeWithTheirRoutingKeyAndType() {
        assertEquals("mto.resources.vehicle-document.expiring", ResourcesRabbitMqNames.routingKey("vehicle-document", "expiring"));
        assertEquals("RESOURCES_VEHICLE_DOCUMENT_EXPIRED", ResourcesRabbitMqNames.eventType("vehicle-document", "expired"));
        assertEquals("mto.resources.qualification.expired", ResourcesRabbitMqNames.routingKey("qualification", "expired"));
        assertEquals("RESOURCES_CALIBRATION_EXPIRING", ResourcesRabbitMqNames.eventType("calibration", "expiring"));
        assertEquals("mto.resources.instrument.deactivated", ResourcesRabbitMqNames.routingKey("instrument", "deactivated"));
    }

    /**
     * El vigilante de caducidades solo se planifica con el broker y con su interruptor encendidos: sin broker
     * gastaria las marcas sin avisar a nadie. Sus properties existen siempre, porque los tests lo llaman.
     */
    @Test
    void theExpiryWatcherIsScheduledOnlyWithTheBrokerAndItsSwitchOn() throws ClassNotFoundException {
        Class<?> scheduler = Class.forName(ExpiryWatchConfiguration.class.getName() + "$Scheduler");
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ExpiryWatchConfiguration.class)
                .withBean(ExpiryWatchService.class, () -> mock(ExpiryWatchService.class));

        runner.withPropertyValues("app.rabbitmq.enabled=true", "app.resources.expiry.warning-days=10").run(context -> {
            assertEquals(1, context.getBeansOfType(scheduler).size());
            assertEquals(10, context.getBean(ExpiryWatchProperties.class).warningDays());
        });
        runner.withPropertyValues("app.rabbitmq.enabled=true", "app.resources.expiry.enabled=false").run(context -> {
            assertEquals(0, context.getBeansOfType(scheduler).size());
            assertEquals(ExpiryWatchProperties.DEFAULT_WARNING_DAYS, context.getBean(ExpiryWatchProperties.class).warningDays());
        });
        runner.withPropertyValues("app.rabbitmq.enabled=false").run(context -> assertEquals(0, context.getBeansOfType(scheduler).size()));
    }

    private static RabbitMqConfiguration topology() {
        return topology(new ConsumerQueueProperties(null, null));
    }

    private static RabbitMqConfiguration topology(ConsumerQueueProperties queues) {
        return new RabbitMqConfiguration(new MaintenanceRabbitProperties(null, null, null, null, null, null),
                new FieldRabbitProperties(null, null, null, null, null, null), new MasterDataRabbitProperties(null, null, null, null, null, null),
                new ResourcesEventsProperties(null), queues);
    }

    private static MasterDataChangedMessage masterDataMessage(String origin, MasterDataChangedEvent event) {
        return new MasterDataChangedMessage(UUID.randomUUID(), "track-access-point-77", origin, Instant.now(),
                "MASTER_DATA_TRACK_ACCESS_POINT_UPDATED", event, "hash", MessageActor.of("u-1", "config.editor"), "corr-1");
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
