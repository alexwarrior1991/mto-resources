package com.alejandro.mtoresources.configuration.rabbitmq;

import com.alejandro.mtoresources.application.service.IncomingEventProcessor;
import com.alejandro.mtoresources.configuration.messaging.MessagePayloadSignatureVerifier;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.FieldEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MasterDataEventConsumer;
import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.RabbitListenerContainerFactoryNames;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the exchange of this service and the queues of what it consumes: the shifts of
 * {@code mto-maintenance}, the positions of {@code mto-field} and the track access points of
 * {@code mto-configuration}.
 *
 * <p>Como consumidor, {@code mto-resources} declara sus propias colas, con su DLX, su DLQ y el binding
 * contra el exchange de cada emisor, porque una cola pertenece a quien la consume.
 * Como productor declara solo su exchange, {@code mto.resources.exchange}: lo que cuenta de sí
 * mismo lo publica el outbox ({@code configuration/outbox}) con la plantilla que autoconfigura Spring
 * Boot, con publisher confirms y returns ({@code spring.rabbitmq.*}), y las colas que lo escuchan son
 * de quien lo consume ({@code mto-maintenance} hoy).</p>
 *
 * <p>El exchange se redeclara con exactamente los mismos atributos que usa el emisor (topic,
 * durable, sin auto-delete). La redeclaración es idempotente solo si coinciden: cualquier
 * diferencia hace que el broker responda {@code PRECONDITION_FAILED} y cierre el canal, con lo que
 * se quedan sin declarar la cola y el binding — y sin binding no llega ni un mensaje, sin ningún
 * error visible después del arranque.</p>
 *
 * <p>Todo el bloque se apaga con {@code app.rabbitmq.enabled=false}: sin estos beans nadie abre una
 * conexión y la aplicación arranca igual sin broker, que es lo que necesitan los tests y un
 * entorno donde el canal todavía no está montado.</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({MaintenanceRabbitProperties.class, FieldRabbitProperties.class, MasterDataRabbitProperties.class,
        ResourcesEventsProperties.class})
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqConfiguration.class);

    private final MaintenanceRabbitProperties properties;
    private final FieldRabbitProperties fieldProperties;
    private final MasterDataRabbitProperties masterDataProperties;
    private final ResourcesEventsProperties eventsProperties;

    /**
     * Exchange propio: lo que este servicio cuenta de sí mismo, con la clave
     * {@code mto.resources.<entidad>.<evento>} (ver {@code ResourcesRabbitMqNames}). Aquí solo
     * el exchange, topic y durable como el de los hermanos; ninguna cola ni binding, que son de
     * quien consume. Se declara aunque nadie escuche todavía: sin exchange, el relay del outbox no
     * tendría a dónde publicar y cada mensaje fallaría hasta agotar sus intentos.
     */
    @Bean
    public TopicExchange resourcesExchange() {
        return new TopicExchange(eventsProperties.exchange(), true, false);
    }

    /** Exchange del emisor. Se replica tal cual; ver el javadoc de la clase. */
    @Bean
    public TopicExchange maintenanceExchange() {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /**
     * Cola de este servicio, con su descarte configurado en la declaración.
     *
     * <p>Los argumentos de una cola ya creada son inmutables, así que añadir aquí un TTL o un
     * límite obliga a borrarla y recrearla en cada entorno; para eso están las policies del
     * broker.</p>
     */
    @Bean
    public Queue maintenanceQueue() {
        return QueueBuilder.durable(properties.queue())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, properties.deadLetterExchange())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, properties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding maintenanceBinding(Queue maintenanceQueue, TopicExchange maintenanceExchange) {
        return BindingBuilder.bind(maintenanceQueue).to(maintenanceExchange).with(properties.routingKey());
    }

    /**
     * El exchange de descarte es {@code direct} y no {@code topic}: aquí no se enruta por patrón,
     * solo se lleva lo rechazado a una única cola.
     */
    @Bean
    public DirectExchange maintenanceDeadLetterExchange() {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue maintenanceDeadLetterQueue() {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding maintenanceDeadLetterBinding(
            Queue maintenanceDeadLetterQueue,
            DirectExchange maintenanceDeadLetterExchange) {
        return BindingBuilder.bind(maintenanceDeadLetterQueue)
                .to(maintenanceDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }

    /**
     * Convertidor JSON del canal.
     *
     * <p>Parte del {@link JsonMapper} de la aplicación para no mantener dos configuraciones de
     * Jackson, y le desactiva explícitamente el fallo por propiedades desconocidas. Eso no es un
     * atajo: el emisor puede añadir campos al mensaje sin coordinarse con cada consumidor, y con el
     * fallo activo un campo nuevo mandaría a la DLQ mensajes perfectamente válidos.</p>
     *
     * <p>El tipo destino se infiere de la firma del método anotado con {@code @RabbitListener}. Es
     * necesario porque el outbox del emisor publica bytes ya serializados, sin cabecera
     * {@code __TypeId__}: no hay tipo en el mensaje que seguir.</p>
     */
    @Bean
    public MessageConverter incomingEventMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    /**
     * Factory del contenedor de listeners.
     *
     * <p>El {@link SimpleRabbitListenerContainerFactoryConfigurer} no es un adorno: declarar esta
     * factory a mano sustituye a la de Spring Boot, y sin pasar por el configurer se descarta en
     * silencio todo {@code spring.rabbitmq.listener.simple.*} — reintentos, backoff y
     * acknowledge-mode incluidos. Un consumidor que fallara no reintentaría nunca, aunque el YAML
     * dijera que sí.</p>
     *
     * <p>{@code defaultRequeueRejected} sí se fija aquí, y no en el YAML, a propósito. Con el valor
     * por defecto de Spring AMQP ({@code true}) un mensaje que falla vuelve a la cabeza de la cola
     * y se reentrega sin fin: el consumidor gira en vacío, la DLQ nunca recibe nada y el canal se
     * queda atascado en el primer mensaje malo. Es la única garantía de este consumidor que no
     * conviene dejar a merced de una línea de configuración.</p>
     */
    @Bean(RabbitListenerContainerFactoryNames.MAINTENANCE)
    public SimpleRabbitListenerContainerFactory maintenanceRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter incomingEventMessageConverter) {
        return listenerContainerFactory(configurer, connectionFactory, incomingEventMessageConverter);
    }

    /** La de los datos maestros de mto-configuration: la misma configuracion, en su propia factory. */
    @Bean(RabbitListenerContainerFactoryNames.MASTER_DATA)
    public SimpleRabbitListenerContainerFactory masterDataRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter incomingEventMessageConverter) {
        return listenerContainerFactory(configurer, connectionFactory, incomingEventMessageConverter);
    }

    /** La de las posiciones de mto-field: la misma configuracion, en su propia factory, para que cada cola tenga la suya. */
    @Bean(RabbitListenerContainerFactoryNames.FIELD)
    public SimpleRabbitListenerContainerFactory fieldRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter incomingEventMessageConverter) {
        return listenerContainerFactory(configurer, connectionFactory, incomingEventMessageConverter);
    }

    private static SimpleRabbitListenerContainerFactory listenerContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                                 ConnectionFactory connectionFactory,
                                                                                 MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    /**
     * Exchange de la telemetria de {@code mto-field}, redeclarado con los atributos del emisor (topic,
     * durable), y la cola de este servicio con su descarte: las posiciones de los dispositivos de un turno.
     */
    @Bean
    public TopicExchange fieldTelemetryExchange() {
        return new TopicExchange(fieldProperties.exchange(), true, false);
    }

    @Bean
    public Queue fieldQueue() {
        return QueueBuilder.durable(fieldProperties.queue())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, fieldProperties.deadLetterExchange())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, fieldProperties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding fieldBinding(Queue fieldQueue, TopicExchange fieldTelemetryExchange) {
        return BindingBuilder.bind(fieldQueue).to(fieldTelemetryExchange).with(fieldProperties.routingKey());
    }

    @Bean
    public DirectExchange fieldDeadLetterExchange() {
        return new DirectExchange(fieldProperties.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue fieldDeadLetterQueue() {
        return QueueBuilder.durable(fieldProperties.deadLetterQueue()).build();
    }

    @Bean
    public Binding fieldDeadLetterBinding(Queue fieldDeadLetterQueue, DirectExchange fieldDeadLetterExchange) {
        return BindingBuilder.bind(fieldDeadLetterQueue).to(fieldDeadLetterExchange).with(fieldProperties.deadLetterRoutingKey());
    }

    /**
     * Exchange de los datos maestros de {@code mto-configuration}, redeclarado con los atributos del emisor
     * (topic, durable), y la cola de este servicio con su descarte, atada solo a los puntos de acceso a la via
     * ({@code mto.master-data.track-access-point.*}): del resto de la infraestructura este servicio guarda
     * solo ids. El exchange ya tiene otras colas (las de mto-stock, mto-maintenance y mto-notification, con
     * {@code mto.master-data.#}), asi que el {@code mandatory} del emisor no falla aunque esta cola aun no
     * exista; pero lo que se publique antes no llega aqui, y por eso tras desplegar se republican los puntos
     * de acceso.
     */
    @Bean
    public TopicExchange masterDataExchange() {
        return new TopicExchange(masterDataProperties.exchange(), true, false);
    }

    @Bean
    public Queue masterDataQueue() {
        return QueueBuilder.durable(masterDataProperties.queue())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_EXCHANGE, masterDataProperties.deadLetterExchange())
                .withArgument(MaintenanceRabbitMqNames.ARG_DEAD_LETTER_ROUTING_KEY, masterDataProperties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding masterDataBinding(Queue masterDataQueue, TopicExchange masterDataExchange) {
        return BindingBuilder.bind(masterDataQueue).to(masterDataExchange).with(masterDataProperties.routingKey());
    }

    @Bean
    public DirectExchange masterDataDeadLetterExchange() {
        return new DirectExchange(masterDataProperties.deadLetterExchange(), true, false);
    }

    @Bean
    public Queue masterDataDeadLetterQueue() {
        return QueueBuilder.durable(masterDataProperties.deadLetterQueue()).build();
    }

    @Bean
    public Binding masterDataDeadLetterBinding(Queue masterDataDeadLetterQueue, DirectExchange masterDataDeadLetterExchange) {
        return BindingBuilder.bind(masterDataDeadLetterQueue).to(masterDataDeadLetterExchange)
                .with(masterDataProperties.deadLetterRoutingKey());
    }

    /** Consumidor de los datos maestros, con su interruptor ({@code app.rabbitmq.master-data.listener-enabled}). */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.rabbitmq.master-data", name = "listener-enabled",
            havingValue = "true", matchIfMissing = true)
    public MasterDataEventConsumer masterDataEventConsumer(
            IncomingEventProcessor incomingEventProcessor,
            MessagePayloadSignatureVerifier signatureVerifier) {
        LOGGER.info("Master data consumer enabled: queue={}, boundTo={} with routingKey={}, deadLetterQueue={}",
                masterDataProperties.queue(), masterDataProperties.exchange(), masterDataProperties.routingKey(),
                masterDataProperties.deadLetterQueue());
        return new MasterDataEventConsumer(incomingEventProcessor, signatureVerifier);
    }

    /** Consumidor de las posiciones, con su interruptor ({@code app.rabbitmq.field.listener-enabled}). */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.rabbitmq.field", name = "listener-enabled",
            havingValue = "true", matchIfMissing = true)
    public FieldEventConsumer fieldEventConsumer(
            IncomingEventProcessor incomingEventProcessor,
            MessagePayloadSignatureVerifier signatureVerifier) {
        LOGGER.info("Field position consumer enabled: queue={}, boundTo={} with routingKey={}, deadLetterQueue={}",
                fieldProperties.queue(), fieldProperties.exchange(), fieldProperties.routingKey(), fieldProperties.deadLetterQueue());
        return new FieldEventConsumer(incomingEventProcessor, signatureVerifier);
    }

    /**
     * Consumidor del canal.
     *
     * <p>Es un {@code @Bean} y no un {@code @Component} para que herede la condición de esta clase:
     * con {@code app.rabbitmq.enabled=false} no hay factory que lo soporte, y un consumidor
     * escaneado por su cuenta fallaría al arrancar buscándola.</p>
     *
     * <p>Depende del procesador idempotente y no del manejador: el consumidor entrega el mensaje al
     * inbox, y es el inbox quien decide si el manejador llega a ejecutarse.</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.rabbitmq.maintenance", name = "listener-enabled",
            havingValue = "true", matchIfMissing = true)
    public MaintenanceEventConsumer maintenanceEventConsumer(
            IncomingEventProcessor incomingEventProcessor,
            MessagePayloadSignatureVerifier signatureVerifier) {
        LOGGER.info("Maintenance event consumer enabled: queue={}, boundTo={} with routingKey={}, "
                        + "deadLetterQueue={} via {} with routingKey={}",
                properties.queue(), properties.exchange(), properties.routingKey(),
                properties.deadLetterQueue(), properties.deadLetterExchange(), properties.deadLetterRoutingKey());

        return new MaintenanceEventConsumer(incomingEventProcessor, signatureVerifier);
    }
}
