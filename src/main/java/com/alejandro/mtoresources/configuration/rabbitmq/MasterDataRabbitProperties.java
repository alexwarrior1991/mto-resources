package com.alejandro.mtoresources.configuration.rabbitmq;

import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MasterDataRabbitMqNames;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * La topologia de lo que se escucha de los datos maestros de {@code mto-configuration} (los puntos de
 * acceso a la via), configurable por entorno, como {@link MaintenanceRabbitProperties}: los valores por
 * defecto son el contrato real y una cadena en blanco cuenta como no configurada. El interruptor del
 * consumidor ({@code listener-enabled}) lo lee el {@code @ConditionalOnProperty} de {@link RabbitMqConfiguration}.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq.master-data")
public record MasterDataRabbitProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String routingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {

    public MasterDataRabbitProperties {
        exchange = defaultIfBlank(exchange, MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE);
        queue = defaultIfBlank(queue, MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_QUEUE);
        routingKey = defaultIfBlank(routingKey, MasterDataRabbitMqNames.TRACK_ACCESS_POINT_ROUTING_PATTERN);
        deadLetterExchange = defaultIfBlank(deadLetterExchange, MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_DEAD_LETTER_EXCHANGE);
        deadLetterQueue = defaultIfBlank(deadLetterQueue, MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_DEAD_LETTER_QUEUE);
        deadLetterRoutingKey = defaultIfBlank(deadLetterRoutingKey, MasterDataRabbitMqNames.RESOURCES_MASTER_DATA_DEAD_LETTER_ROUTING_KEY);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
