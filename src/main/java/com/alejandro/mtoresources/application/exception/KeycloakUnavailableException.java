package com.alejandro.mtoresources.application.exception;

/**
 * Keycloak no responde, rechaza la cuenta de servicio o su lectura esta apagada
 * ({@code app.keycloak.admin.enabled=false}): el informe que lo lee no se puede dar. Responde 503
 * {@code KCK-503}; nada se ha escrito.
 */
public class KeycloakUnavailableException extends BusinessException {

    public KeycloakUnavailableException(String message) {
        super(message);
    }

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
