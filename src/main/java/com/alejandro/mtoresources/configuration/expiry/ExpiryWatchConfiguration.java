package com.alejandro.mtoresources.configuration.expiry;

import com.alejandro.mtoresources.application.service.ExpiryWatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Registra las properties del vigilante de caducidades y, cuando esta encendido, su planificador.
 *
 * <p>Las properties se registran siempre porque el servicio que hace el trabajo existe siempre (los tests
 * lo llaman a mano); el planificador solo con {@code app.rabbitmq.enabled} (sin broker no hay a quien
 * contarselo, y las marcas se gastarian sin avisar) y con {@code app.resources.expiry.enabled}. El cron va
 * en UTC, la zona del reloj del servicio. Con varias instancias todas despiertan a la misma hora: la marca
 * condicional de cada fila decide cual avisa.</p>
 */
@Configuration
@EnableConfigurationProperties(ExpiryWatchProperties.class)
public class ExpiryWatchConfiguration {

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "app.resources.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @RequiredArgsConstructor
    static class Scheduler {

        private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);

        private final ExpiryWatchService expiryWatchService;

        @Scheduled(cron = "${app.resources.expiry.cron:0 10 4 * * *}", zone = "UTC")
        public void check() {
            try {
                expiryWatchService.check();
            } catch (RuntimeException exception) {
                // Manana vuelve a mirarse; un fallo aqui no puede tumbar el planificador que comparte con el
                // outbox y las operaciones de fondo.
                LOGGER.error("The expiry check failed", exception);
            }
        }
    }
}
