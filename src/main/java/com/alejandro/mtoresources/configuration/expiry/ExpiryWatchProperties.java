package com.alejandro.mtoresources.configuration.expiry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El vigilante diario de caducidades ({@code app.resources.expiry.*}).
 *
 * <p>El interruptor ({@code enabled}) no esta aqui: lo lee el {@code @ConditionalOnProperty} de
 * {@link ExpiryWatchConfiguration}, que decide si el planificador existe. El cron tampoco: lo lee el propio
 * {@code @Scheduled}.</p>
 *
 * @param warningDays con cuantos dias de antelacion se avisa de que algo caduca (30 por defecto); de lo
 *                    caducado se avisa siempre, desde el dia siguiente al de su caducidad
 */
@ConfigurationProperties(prefix = "app.resources.expiry")
public record ExpiryWatchProperties(Integer warningDays) {

    public static final int DEFAULT_WARNING_DAYS = 30;

    public ExpiryWatchProperties {
        warningDays = warningDays == null || warningDays < 0 ? DEFAULT_WARNING_DAYS : warningDays;
    }
}
