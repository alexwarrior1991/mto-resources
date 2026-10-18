package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

/**
 * Lo que este servicio escucha de los datos maestros de {@code mto-configuration}: su exchange y su clave,
 * que son contrato del emisor, y la cola con su descarte, que son de este servicio porque una cola es de
 * quien la consume.
 *
 * <p>Solo los puntos de acceso a la via ({@code mto.master-data.track-access-point.*}), no
 * {@code mto.master-data.#}: de la infraestructura este servicio solo necesita por donde encarrila una
 * bivial; las vias, estaciones y paquetes los guarda como ids sin clave ajena.</p>
 */
public final class MasterDataRabbitMqNames {

    /** Exchange de los datos maestros de {@code mto-configuration}. */
    public static final String MASTER_DATA_EXCHANGE = "mto.master-data.exchange";

    /** Solo los puntos de acceso a la via: {@code created}, {@code updated} y {@code deleted}. */
    public static final String TRACK_ACCESS_POINT_ROUTING_PATTERN = "mto.master-data.track-access-point.*";

    public static final String RESOURCES_MASTER_DATA_QUEUE = "mto.resources.master-data.queue";
    public static final String RESOURCES_MASTER_DATA_DEAD_LETTER_EXCHANGE = "mto.resources.master-data.queue.dlx";
    public static final String RESOURCES_MASTER_DATA_DEAD_LETTER_QUEUE = "mto.resources.master-data.queue.dlq";
    public static final String RESOURCES_MASTER_DATA_DEAD_LETTER_ROUTING_KEY = "mto.resources.master-data.queue.dlq";

    /** El {@code origin} que tienen que traer sus sobres; uno distinto no es suyo y va a la DLQ. */
    public static final String CONFIGURATION_SOURCE_SERVICE = "mto-configuration";

    private MasterDataRabbitMqNames() {
    }
}
