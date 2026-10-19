package com.alejandro.mtoresources.configuration.keycloak;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * La lectura de Keycloak del informe de discrepancias ({@code app.keycloak.admin.*}), la unica llamada que
 * sale de este servicio: solo lee, con la cuenta de servicio {@code mto-resources-svc} ({@code view-users},
 * {@code query-users} y {@code view-realm} de {@code realm-management}, que concede {@code apply-partials.sh}).
 *
 * @param enabled              con {@code false} no se llama a nadie y el informe responde 503 {@code KCK-503}
 * @param baseUrl              la raiz de Keycloak ({@code http://auth.mto.local:8082})
 * @param realm                el realm ({@code mto})
 * @param clientRegistrationId el registro de {@code spring.security.oauth2.client} de la cuenta de servicio
 */
@Validated
@ConfigurationProperties(prefix = "app.keycloak.admin")
public record KeycloakAdminProperties(boolean enabled, @NotBlank String baseUrl, @NotBlank String realm,
                                      @NotBlank String clientRegistrationId) {

    public KeycloakAdminProperties {
        baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** La raiz de la Admin API del realm: {@code <base>/admin/realms/<realm>}. */
    public String adminRealmUrl() {
        return baseUrl + "/admin/realms/" + realm;
    }
}
