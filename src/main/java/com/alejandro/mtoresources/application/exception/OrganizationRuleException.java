package com.alejandro.mtoresources.application.exception;

/**
 * Regla del organigrama incumplida: un equipo sin su jefe o fuera de 2 a 6 linemen, un padre que no
 * es de un nivel superior, un jefe sin la categoria del nivel, una unidad con hijos activos que se
 * quiere desactivar. Responde 409 {@code UNI-001}.
 */
public class OrganizationRuleException extends BusinessException {

    public OrganizationRuleException(String message) {
        super(message);
    }
}
