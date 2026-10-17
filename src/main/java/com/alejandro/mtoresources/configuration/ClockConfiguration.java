package com.alejandro.mtoresources.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * El reloj de los servicios: «hoy» para una entrada o una salida de un equipo, «ahora» para una
 * ubicacion sin {@code observedAt}. Un bean para que los tests lo fijen.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
