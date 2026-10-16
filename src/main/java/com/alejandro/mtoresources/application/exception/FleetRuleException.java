package com.alejandro.mtoresources.application.exception;

/**
 * Regla de la flota incumplida: una dresina por carretera o aparcada fuera de una via, una furgoneta
 * en via, un vehiculo retirado que se mueve, un remolque enganchado a quien no remolca. Responde 409
 * {@code VEH-001}.
 */
public class FleetRuleException extends BusinessException {

    public FleetRuleException(String message) {
        super(message);
    }
}
