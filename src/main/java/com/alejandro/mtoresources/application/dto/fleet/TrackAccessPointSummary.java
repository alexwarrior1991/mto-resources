package com.alejandro.mtoresources.application.dto.fleet;

/**
 * El punto de acceso a la via de una ubicacion, del catalogo de {@code mto-configuration}: donde encarrilo
 * o desencarrilo. {@code kind} es {@code PREPARED_ACCESS}, {@code LEVEL_CROSSING} o {@code DEPOT}.
 *
 * <p>Ejemplo: {@code {"id": 77, "name": "Acceso km 110+176", "kind": "PREPARED_ACCESS"}}</p>
 */
public record TrackAccessPointSummary(Long id, String name, String kind) {
}
