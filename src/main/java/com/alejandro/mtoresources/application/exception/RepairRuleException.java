package com.alejandro.mtoresources.application.exception;

/**
 * Regla del taller incumplida: una transicion que la reparacion no admite (cerrar una planificada, abrir
 * una cerrada...) o un cambio que su estado ya no deja hacer. Responde 409 {@code REP-001}.
 */
public class RepairRuleException extends BusinessException {

    public RepairRuleException(String message) {
        super(message);
    }
}
