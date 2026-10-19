package com.alejandro.mtoresources.configuration.keycloak;

import com.alejandro.mtoresources.infrastructure.keycloak.RestClientKeycloakDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * El cliente HTTP hacia la Admin API de Keycloak con la cuenta de servicio {@code mto-resources-svc}
 * (client_credentials), para el informe de discrepancias. El gestor de tokens se construye aqui y no se
 * pide al contexto, como en mto-maintenance hacia mto-stock y en mto-notification hacia Keycloak: el que
 * registra Spring Security por defecto esta ligado a la peticion HTTP.
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakClientConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakClientConfiguration.class);

    /** Principal nominal de la cuenta de servicio: solo identifica la autorizacion en el servicio de clientes. */
    static final String SERVICE_PRINCIPAL = "mto-resources";

    @Bean
    public RestClientKeycloakDirectory keycloakDirectory(RestClient.Builder builder, KeycloakAdminProperties properties,
                                                         ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                                         ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) {
        RestClient.Builder keycloakBuilder = builder.clone().baseUrl(properties.adminRealmUrl());
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        OAuth2AuthorizedClientService clients = authorizedClients.getIfAvailable();
        OAuth2AuthorizedClientManager manager = registrations == null || clients == null ? null
                : new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        if (manager == null) {
            LOGGER.warn("No OAuth2 client registration is configured: reading the Keycloak Admin API will go out without a bearer token");
        } else {
            keycloakBuilder.requestInterceptor((request, body, execution) -> {
                OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                        .withClientRegistrationId(properties.clientRegistrationId())
                        .principal(SERVICE_PRINCIPAL)
                        .build());
                if (client != null) {
                    request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                }
                return execution.execute(request, body);
            });
        }
        return new RestClientKeycloakDirectory(keycloakBuilder.build(), properties.enabled());
    }
}
