package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

/**
 * Lo que este servicio escucha de {@code mto-field}: su exchange de telemetria y su clave, que son
 * contrato del emisor, y la cola con su descarte, que son de este servicio porque una cola es de quien
 * la consume.
 *
 * <p>Solo las posiciones ({@code mto.field.position.*}): el kp en el que un dispositivo de un turno
 * dice que esta su vehiculo. Van por un exchange aparte del de los eventos de {@code mto-field}
 * ({@code mto.field.exchange}), porque {@code mto-notification} registra todo lo que llega alli y una
 * posicion por minuto y vehiculo no es actividad.</p>
 */
public final class FieldRabbitMqNames {

    /** Exchange de la telemetria de {@code mto-field}. */
    public static final String FIELD_TELEMETRY_EXCHANGE = "mto.field.telemetry.exchange";

    /** Las posiciones: {@code mto.field.position.reported}. */
    public static final String FIELD_POSITION_ROUTING_PATTERN = "mto.field.position.*";

    public static final String RESOURCES_FIELD_QUEUE = "mto.resources.field.queue";
    public static final String RESOURCES_FIELD_DEAD_LETTER_EXCHANGE = "mto.resources.field.queue.dlx";
    public static final String RESOURCES_FIELD_DEAD_LETTER_QUEUE = "mto.resources.field.queue.dlq";
    public static final String RESOURCES_FIELD_DEAD_LETTER_ROUTING_KEY = "mto.resources.field.queue.dlq";

    /** El {@code origin} que tienen que traer sus sobres; uno distinto no es suyo y va a la DLQ. */
    public static final String FIELD_SOURCE_SERVICE = "mto-field";

    private FieldRabbitMqNames() {
    }
}
