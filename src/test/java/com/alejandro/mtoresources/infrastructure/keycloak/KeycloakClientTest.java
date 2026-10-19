package com.alejandro.mtoresources.infrastructure.keycloak;

import com.alejandro.mtoresources.application.exception.KeycloakUnavailableException;
import com.alejandro.mtoresources.application.service.KeycloakDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * La lectura de la Admin API de Keycloak del informe de discrepancias, contra un servidor simulado: las
 * paginas, un rol que no existe y lo que convierte en {@code KCK-503}. La cuenta de servicio y su token son
 * los de Spring Security, que ya prueba el proyecto.
 */
class KeycloakClientTest {

    private static final String ADMIN = "http://keycloak.test/admin/realms/mto";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(ADMIN);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClientKeycloakDirectory directory = new RestClientKeycloakDirectory(builder.build(), true);

    @Test
    void theMembersOfARoleArePagedUntilAShortPageAndWhatElseKeycloakSaysIsIgnored() {
        server.expect(requestTo(ADMIN + "/roles/mto-position-worker/users?first=0&max=100&briefRepresentation=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(users(0, 100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/roles/mto-position-worker/users?first=100&max=100&briefRepresentation=true"))
                .andRespond(withSuccess(users(100, 1), MediaType.APPLICATION_JSON));

        List<KeycloakDirectory.DirectoryUser> members = directory.realmRoleMembers("mto-position-worker");

        assertEquals(101, members.size());
        assertEquals("user.0", members.getFirst().username());
        assertEquals("user.100", members.getLast().username());
        assertEquals(Boolean.TRUE, members.getFirst().enabled());
        server.verify();
    }

    @Test
    void aRoleThatDoesNotExistHasNoMembers() {
        server.expect(requestTo(ADMIN + "/roles/mto-position-chief-construction/users?first=0&max=100&briefRepresentation=true"))
                .andRespond(withResourceNotFound());

        assertEquals(List.of(), directory.realmRoleMembers("mto-position-chief-construction"));
    }

    @Test
    void anyOtherAnswerIsKeycloakUnavailableAndSwitchedOffNothingIsCalled() {
        server.expect(requestTo(ADMIN + "/roles/mto-position-worker/users?first=0&max=100&briefRepresentation=true"))
                .andRespond(withServerError());
        server.expect(requestTo(ADMIN + "/roles/mto-position-worker/users?first=0&max=100&briefRepresentation=true"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        KeycloakUnavailableException down = assertThrows(KeycloakUnavailableException.class, () -> directory.realmRoleMembers("mto-position-worker"));
        assertTrue(down.getMessage().contains("500"), down.getMessage());
        KeycloakUnavailableException refused = assertThrows(KeycloakUnavailableException.class,
                () -> directory.realmRoleMembers("mto-position-worker"));
        assertTrue(refused.getMessage().contains("403"), "The service account without its roles: " + refused.getMessage());
        server.verify();

        RestClientKeycloakDirectory off = new RestClientKeycloakDirectory(RestClient.builder().baseUrl(ADMIN).build(), false);
        assertThrows(KeycloakUnavailableException.class, () -> off.realmRoleMembers("mto-position-worker"));
    }

    private static String users(int from, int count) {
        return IntStream.range(from, from + count)
                .mapToObj(i -> "{\"id\":\"id-" + i + "\",\"username\":\"user." + i + "\",\"enabled\":true,\"email\":\"user." + i
                        + "@mto.local\",\"createdTimestamp\":1}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
