package com.alejandro.mtoresources.infrastructure.messaging.rabbitmq;

/**
 * Bean names of the listener container factories of this service.
 *
 * <p>El nombre viaja como cadena dentro de {@code @RabbitListener}, donde ningún compilador lo
 * comprueba: si el bean se renombra y la anotación no, Spring falla al arrancar buscando una
 * factory que ya no existe. Con la constante, el renombrado arrastra los dos sitios.</p>
 */
public final class RabbitListenerContainerFactoryNames {

    /** Factory de la cola de los eventos de mto-maintenance (los turnos). */
    public static final String MAINTENANCE = "maintenanceRabbitListenerContainerFactory";

    private RabbitListenerContainerFactoryNames() {
    }
}
