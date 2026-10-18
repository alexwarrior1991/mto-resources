package com.alejandro.mtoresources.application.service;

import com.alejandro.mtoresources.application.dto.fleet.TrackAccessPointResponse;

import java.util.List;

/**
 * Los puntos de acceso a la via de {@code mto-configuration}, como los tiene este servicio: solo lectura,
 * porque solo los escribe el evento {@code track-access-point} de los datos maestros.
 */
public interface TrackAccessPointService {

    /**
     * Lo que ofrece el dialogo de ubicacion: una lista entera, no una pagina (los de una via son pocos),
     * por via, kp e id. Un filtro sin valor no filtra.
     */
    List<TrackAccessPointResponse> list(Long trackId, Long executionPackageId, Boolean enabled);
}
