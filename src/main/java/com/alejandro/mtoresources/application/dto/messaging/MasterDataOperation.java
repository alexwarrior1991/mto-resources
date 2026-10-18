package com.alejandro.mtoresources.application.dto.messaging;

/**
 * Lo que le paso a un dato maestro de {@code mto-configuration}: alta, modificacion o baja (un borrado
 * logico alli). Los nombres son los del emisor y viajan tal cual dentro del payload, asi que renombrar una
 * constante rompe la lectura de los mensajes que ya estan en la cola; uno desconocido no se puede leer y va
 * a la DLQ.
 */
public enum MasterDataOperation {

    CREATED("created"),
    UPDATED("updated"),
    DELETED("deleted");

    private final String routingValue;

    MasterDataOperation(String routingValue) {
        this.routingValue = routingValue;
    }

    /**
     * El trozo que ocupa en la clave {@code mto.master-data.<entidad>.<operacion>}, que es tambien el
     * {@code eventName} con el que el evento llega a su {@code IncomingEventHandler}.
     */
    public String routingValue() {
        return routingValue;
    }
}
