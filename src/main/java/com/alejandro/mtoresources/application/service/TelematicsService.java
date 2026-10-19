package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsRequest;
import com.alejandro.mtoresources.application.dto.telematics.TelematicsPositionsResponse;

/**
 * Las posiciones de los dispositivos GPS de la flota de carretera: mueven a {@code ON_ROAD} los vehiculos
 * de carretera y los biviales, por la misma puerta que el resto ({@code VehicleLocationService.record}).
 */
public interface TelematicsService {

    TelematicsPositionsResponse ingest(TelematicsPositionsRequest request);
}
