package com.alejandro.mtoresources.application.dto.messaging;

import java.util.Map;

/**
 * El {@code data} de un evento propio de otro servicio del dominio, tal como llega: la misma forma que
 * {@link DomainEvent}, pero tolerante. No rechaza nada al construirse, porque lo que se lee no se
 * elige: lo comprueba el consumidor, que manda a la DLQ lo que no trae entidad ni evento.
 *
 * <p>Ejemplo, el {@code data} de {@code shift.started} de {@code mto-maintenance}:</p>
 * <pre>{@code
 * {"entityName": "shift", "entityId": "…", "eventName": "started",
 *  "values": {"code": "SH-000012", "trackIds": [2], "startKp": 34.271, "vehicleIds": ["…"], …}}
 * }</pre>
 */
public record IncomingEvent(
        String entityName,
        String entityId,
        String eventName,
        Map<String, Object> values
) {

    public IncomingEvent {
        values = values == null ? Map.of() : values;
    }
}
