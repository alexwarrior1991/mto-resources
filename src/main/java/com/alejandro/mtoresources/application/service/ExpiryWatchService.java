package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.expiry.ExpiryWatchResult;

/**
 * La mirada diaria a las caducidades: las cualificaciones de las personas activas, los documentos de los
 * vehiculos activos y la calibracion de los instrumentos activos. De cada una avisa una vez por fecha de
 * caducidad de que caduca ({@code expiring}, cuando faltan {@code app.resources.expiry.warning-days} o
 * menos) y una vez de que ha caducado ({@code expired}, desde el dia siguiente), y otra vez si se renueva.
 *
 * <p>Corre una vez al dia desde {@code ExpiryWatchConfiguration}; los tests lo llaman. Es, con
 * {@code republish}, lo unico que este servicio publica sin que nadie haya escrito nada, y por eso su
 * {@code operationId} sale de lo que cuenta ({@code ResourcesEvents.expiryOperationId}).</p>
 */
public interface ExpiryWatchService {

    ExpiryWatchResult check();
}
