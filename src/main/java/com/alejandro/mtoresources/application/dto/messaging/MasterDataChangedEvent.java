package com.alejandro.mtoresources.application.dto.messaging;

import java.util.Map;

/**
 * El {@code data} de un cambio de datos maestros de {@code mto-configuration}, tal como llega: copia del
 * {@code MasterDataChangedEvent} del emisor (y del de {@code mto-maintenance}), con {@code operation} donde
 * los eventos propios de los servicios llevan {@code eventName}. {@code values} es un mapa abierto: el
 * emisor publica entidades muy distintas con un solo tipo de mensaje, y leerlo es cosa del manejador.
 *
 * <p>Ejemplo, el {@code data} de un punto de acceso modificado:</p>
 * <pre>{@code
 * {"entityName": "track-access-point", "entityId": "77", "operation": "UPDATED",
 *  "values": {"id": 77, "name": "Acceso km 110+176", "enabled": true, "kp": 110176.000, "kind": "PREPARED_ACCESS", …,
 *             "track": {"id": 3, "name": "TRACK 1", "executionPackageId": 14}}}
 * }</pre>
 */
public record MasterDataChangedEvent(
        String entityName,
        String entityId,
        MasterDataOperation operation,
        Map<String, Object> values
) {

    public MasterDataChangedEvent {
        values = values == null ? Map.of() : values;
    }
}
