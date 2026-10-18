package com.alejandro.mtoresources.application.exception;

/**
 * Regla de las cualificaciones incumplida: una persona que ya tiene ese tipo (se renueva cambiando su
 * caducidad, no dando otra), o un tipo retirado, del que no se dan nuevas. Responde 409 {@code QUA-001}.
 */
public class QualificationRuleException extends BusinessException {

    public QualificationRuleException(String message) {
        super(message);
    }
}
