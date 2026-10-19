package com.alejandro.mtoresources.application.exception;

/**
 * Regla de las ausencias incumplida: una ausencia que se solapa con otra de la misma persona. Responde 409
 * {@code ABS-001}.
 */
public class AbsenceRuleException extends BusinessException {

    public AbsenceRuleException(String message) {
        super(message);
    }
}
