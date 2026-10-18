package com.alejandro.mtoresources.configuration.rabbitmq;

import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.FieldRabbitMqNames;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * La topologia de lo que se escucha de {@code mto-field} (las posiciones de sus dispositivos),
 * configurable por entorno, como {@link MaintenanceRabbitProperties}: los valores por defecto son el
 * contrato real y una cadena en blanco cuenta como no configurada. El interruptor del consumidor
 * ({@code listener-enabled}) lo lee el {@code @ConditionalOnProperty} de {@link RabbitMqConfiguration}.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq.field")
public record FieldRabbitProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String routingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {

    public FieldRabbitProperties {
        exchange = defaultIfBlank(exchange, FieldRabbitMqNames.FIELD_TELEMETRY_EXCHANGE);
        queue = defaultIfBlank(queue, FieldRabbitMqNames.RESOURCES_FIELD_QUEUE);
        routingKey = defaultIfBlank(routingKey, FieldRabbitMqNames.FIELD_POSITION_ROUTING_PATTERN);
        deadLetterExchange = defaultIfBlank(deadLetterExchange, FieldRabbitMqNames.RESOURCES_FIELD_DEAD_LETTER_EXCHANGE);
        deadLetterQueue = defaultIfBlank(deadLetterQueue, FieldRabbitMqNames.RESOURCES_FIELD_DEAD_LETTER_QUEUE);
        deadLetterRoutingKey = defaultIfBlank(deadLetterRoutingKey, FieldRabbitMqNames.RESOURCES_FIELD_DEAD_LETTER_ROUTING_KEY);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
