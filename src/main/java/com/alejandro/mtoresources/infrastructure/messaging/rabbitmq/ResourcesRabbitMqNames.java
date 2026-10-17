package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

import java.util.Locale;

/**
 * Nombres del exchange propio de este servicio y de lo que se enruta por el.
 *
 * <p>Es un exchange distinto de {@code mto.master-data.exchange} a proposito: aquel es el contrato
 * de los datos maestros de {@code mto-configuration}, y lo que este servicio cuenta de si mismo
 * (personas, equipos, vehiculos) sale por aqui. Solo se declara el exchange: la cola es de quien la
 * consume ({@code mto-maintenance} hoy, {@code mto-notification} y {@code mto-construction} despues),
 * que la bindea con lo que le interese ({@code mto.resources.team.*}...).</p>
 *
 * <p>La clave de enrutado y el {@code eventType} son contrato: el consumidor deriva de la clave el
 * tipo de actividad ({@code resources.<entidad>.<evento>}), asi que el formato no es cosmetico.</p>
 */
public final class ResourcesRabbitMqNames {

    public static final String RESOURCES_EXCHANGE = "mto.resources.exchange";

    public static final String RESOURCES_ROUTING_PREFIX = "mto.resources";
    public static final String RESOURCES_ROUTING_PATTERN = "mto.resources.#";

    private static final String EVENT_TYPE_PREFIX = "RESOURCES";

    private ResourcesRabbitMqNames() {
    }

    /** {@code mto.resources.<entidad>.<evento>}: lo que decide a que colas llega el mensaje. */
    public static String routingKey(String entityName, String eventName) {
        return RESOURCES_ROUTING_PREFIX + "." + normalize(entityName) + "." + normalize(eventName);
    }

    /** {@code RESOURCES_<ENTIDAD>_<EVENTO>}: el {@code eventType} del sobre y de la cabecera. */
    public static String eventType(String entityName, String eventName) {
        return EVENT_TYPE_PREFIX + "_" + constant(entityName) + "_" + constant(eventName);
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "-")
                .replace(" ", "-");
    }

    private static String constant(String value) {
        return normalize(value)
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
    }
}
