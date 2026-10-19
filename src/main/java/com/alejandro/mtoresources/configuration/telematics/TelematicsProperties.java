package com.alejandro.mtoresources.configuration.telematics;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Las posiciones de los dispositivos GPS ({@code app.resources.telematics.*}).
 *
 * @param minInterval lo minimo que pasa entre dos posiciones apuntadas del mismo dispositivo (5 minutos por
 *                    defecto): un GPS que manda cada pocos segundos no llena el historico. Lo que llega antes
 *                    se ignora, igual que lo que llega con una hora anterior a la ultima apuntada.
 */
@ConfigurationProperties(prefix = "app.resources.telematics")
public record TelematicsProperties(Duration minInterval) {

    public static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMinutes(5);

    public TelematicsProperties {
        minInterval = minInterval == null || minInterval.isNegative() ? DEFAULT_MIN_INTERVAL : minInterval;
    }
}
