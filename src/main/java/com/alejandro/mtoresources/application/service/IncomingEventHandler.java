package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.messaging.IncomingEvent;
import com.alejandro.mtoresources.application.dto.messaging.IncomingEventContext;

/**
 * Lo que hace este servicio con los eventos de una entidad de otro servicio: un {@code @Service} por
 * entidad, recogido del contexto sin registrar nada, como los manejadores de datos maestros de
 * {@code mto-maintenance}. Dos para la misma entidad y el mismo emisor impiden arrancar; una entidad
 * sin manejador se registra y se ignora.
 *
 * <p>Corre dentro de la transaccion del inbox: no abre la suya ni llama a otro servicio.</p>
 */
public interface IncomingEventHandler {

    /** El servicio emisor, el {@code origin} del sobre ({@code mto-maintenance}). */
    String sourceService();

    /** La entidad de sus eventos que atiende ({@code shift}). */
    String entityName();

    void handle(IncomingEvent event, IncomingEventContext context);
}
