package com.alejandro.mtoresources.configuration.rabbitmq;

import com.alejandro.mtoresources.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * La topologia de lo que se escucha de {@code mto-maintenance}, configurable por entorno.
 *
 * <p>Los valores por defecto son el contrato real, y una cadena en blanco cuenta como no configurada
 * en vez de arrancar contra un exchange llamado {@code ""}, como en los consumidores de datos maestros
 * de los hermanos. El interruptor del consumidor ({@code listener-enabled}) no esta aqui: lo lee el
 * {@code @ConditionalOnProperty} de {@link RabbitMqConfiguration}, que es quien decide si el bean
 * existe.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq.maintenance")
public record MaintenanceRabbitProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String routingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {

    public MaintenanceRabbitProperties {
        exchange = defaultIfBlank(exchange, MaintenanceRabbitMqNames.MAINTENANCE_EXCHANGE);
        queue = defaultIfBlank(queue, MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_QUEUE);
        routingKey = defaultIfBlank(routingKey, MaintenanceRabbitMqNames.MAINTENANCE_SHIFT_ROUTING_PATTERN);
        deadLetterExchange = defaultIfBlank(deadLetterExchange, MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_DEAD_LETTER_EXCHANGE);
        deadLetterQueue = defaultIfBlank(deadLetterQueue, MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_DEAD_LETTER_QUEUE);
        deadLetterRoutingKey = defaultIfBlank(deadLetterRoutingKey, MaintenanceRabbitMqNames.RESOURCES_MAINTENANCE_DEAD_LETTER_ROUTING_KEY);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
