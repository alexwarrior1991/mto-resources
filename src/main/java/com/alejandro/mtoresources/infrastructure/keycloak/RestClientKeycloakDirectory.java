package com.alejandro.mtoresources.infrastructure.keycloak;

import com.alejandro.mtoresources.application.exception.KeycloakUnavailableException;
import com.alejandro.mtoresources.application.service.KeycloakDirectory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * La Admin API de Keycloak con la cuenta de servicio, por paginas de {@value #PAGE}. Un rol que no existe
 * (404) no tiene miembros; cualquier otro fallo (sin red, un 5xx, la cuenta de servicio rechazada o sin
 * token) es {@link KeycloakUnavailableException}. Sin circuito: la llama una persona que pide un informe, no
 * un bucle.
 */
public class RestClientKeycloakDirectory implements KeycloakDirectory {

    static final int PAGE = 100;

    private static final ParameterizedTypeReference<List<DirectoryUser>> USERS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final boolean enabled;

    public RestClientKeycloakDirectory(RestClient restClient, boolean enabled) {
        this.restClient = restClient;
        this.enabled = enabled;
    }

    @Override
    public List<DirectoryUser> realmRoleMembers(String roleName) {
        if (!enabled) {
            throw new KeycloakUnavailableException("Reading Keycloak is switched off (app.keycloak.admin.enabled=false)");
        }
        List<DirectoryUser> members = new ArrayList<>();
        for (int first = 0; ; first += PAGE) {
            List<DirectoryUser> page = page(roleName, first);
            members.addAll(page);
            if (page.size() < PAGE) {
                return members;
            }
        }
    }

    private List<DirectoryUser> page(String roleName, int first) {
        try {
            List<DirectoryUser> users = restClient.get()
                    .uri("/roles/{role}/users?first={first}&max={max}&briefRepresentation=true", roleName, first, PAGE)
                    .retrieve()
                    .body(USERS);
            return users == null ? List.of() : users;
        } catch (RestClientResponseException response) {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return List.of();
            }
            throw new KeycloakUnavailableException("Keycloak answered " + response.getStatusCode().value() + " reading who holds "
                    + roleName, response);
        } catch (RestClientException | OAuth2AuthorizationException failure) {
            throw new KeycloakUnavailableException("Keycloak cannot be read now (" + failure.getMessage() + ")", failure);
        }
    }
}
