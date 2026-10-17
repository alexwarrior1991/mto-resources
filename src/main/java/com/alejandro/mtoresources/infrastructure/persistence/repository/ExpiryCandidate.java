package com.alejandro.mtoresources.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Algo con fecha de caducidad de lo que quiza haya que avisar hoy: su id y la caducidad que se leyo. La
 * marca condicional se escribe con esa misma fecha, de modo que si alguien la cambio entre medias no se
 * marca nada y se mira otra vez en la siguiente pasada.
 */
public interface ExpiryCandidate {

    UUID getId();

    LocalDate getExpiresOn();
}
