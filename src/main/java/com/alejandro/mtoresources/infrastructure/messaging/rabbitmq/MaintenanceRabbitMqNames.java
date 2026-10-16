package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

/**
 * Lo que este servicio escucha de {@code mto-maintenance}: su exchange y su clave, que son contrato
 * del emisor, y la cola con su descarte, que son de este servicio porque una cola es de quien la
 * consume.
 *
 * <p>Solo los turnos ({@code mto.maintenance.shift.*}): al iniciar uno, sus vehiculos quedan en la via;
 * al cerrarlo o cancelarlo, aparcados donde se dejaron. Lo demas de mantenimiento (ordenes, defectos,
 * material) no le dice nada a la flota.</p>
 */
public final class MaintenanceRabbitMqNames {

    /** Exchange de los eventos propios de {@code mto-maintenance}. */
    public static final String MAINTENANCE_EXCHANGE = "mto.maintenance.exchange";

    /** Solo los turnos: {@code started}, {@code closed} y {@code cancelled}. */
    public static final String MAINTENANCE_SHIFT_ROUTING_PATTERN = "mto.maintenance.shift.*";

    public static final String RESOURCES_MAINTENANCE_QUEUE = "mto.resources.maintenance.queue";
    public static final String RESOURCES_MAINTENANCE_DEAD_LETTER_EXCHANGE = "mto.resources.maintenance.queue.dlx";
    public static final String RESOURCES_MAINTENANCE_DEAD_LETTER_QUEUE = "mto.resources.maintenance.queue.dlq";
    public static final String RESOURCES_MAINTENANCE_DEAD_LETTER_ROUTING_KEY = "mto.resources.maintenance.queue.dlq";

    /** El {@code origin} que tienen que traer sus sobres; uno distinto no es suyo y va a la DLQ. */
    public static final String MAINTENANCE_SOURCE_SERVICE = "mto-maintenance";

    public static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String ARG_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    private MaintenanceRabbitMqNames() {
    }
}
