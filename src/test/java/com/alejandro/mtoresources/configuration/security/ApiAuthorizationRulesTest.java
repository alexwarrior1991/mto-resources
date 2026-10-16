package com.alejandro.mtoresources.configuration.security;

import com.alejandro.mtoresources.infrastructure.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que cada recurso pida su permiso y que ninguno implique a otro: escribir personas no basta para la
 * flota, la flota no basta para mover un vehiculo... salvo que mover un vehiculo es justo lo que puede
 * hacer quien solo tiene {@code resources-location-write}. Contra controladores sonda en las rutas reales:
 * lo que se prueba son los patrones de la cadena, no los servicios.
 */
@WebMvcTest(controllers = ApiAuthorizationRulesTest.Probes.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class,
        ApiAuthorizationRulesTest.Probes.class})
@TestPropertySource(properties = {
        "app.security.client-id=mto-resources-api",
        "app.security.principal-claim=preferred_username",
        "app.security.audience-validation-enabled=false",
        "app.security.expose-api-docs=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8082/realms/mto"
})
class ApiAuthorizationRulesTest {

    private static final String API = SecurityConfiguration.API;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withoutATokenEverythingUnderTheApiIsUnauthorized() throws Exception {
        mockMvc.perform(get(API + "/people")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTH-401"));
        mockMvc.perform(post(API + "/vehicles/1/locations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/republish")).andExpect(status().isUnauthorized());
    }

    @Test
    void readingNeedsResourcesReadAndNothingElseImpliesIt() throws Exception {
        expect(get(API + "/people"), 200, SecurityRoles.RESOURCES_READ);
        expect(get(API + "/vehicles/positions"), 200, SecurityRoles.RESOURCES_READ);
        expect(get(API + "/people"), 403, SecurityRoles.RESOURCES_WRITE, SecurityRoles.RESOURCES_FLEET_WRITE,
                SecurityRoles.RESOURCES_LOCATION_WRITE, SecurityRoles.RESOURCES_DELETE);
    }

    @Test
    void peopleUnitsAndParkingSitesAreResourcesWrite() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[]{post(API + "/people"), patch(API + "/people/1"),
                post(API + "/units"), put(API + "/units/1/members"), post(API + "/units/1/members/2/leave"), post(API + "/parking-sites")}) {
            expect(request, 200, SecurityRoles.RESOURCES_WRITE);
        }
        expect(post(API + "/people"), 403, SecurityRoles.RESOURCES_READ, SecurityRoles.RESOURCES_FLEET_WRITE, SecurityRoles.RESOURCES_DELETE);
        expect(post(API + "/units"), 403, SecurityRoles.RESOURCES_FLEET_WRITE);
    }

    @Test
    void vehiclesAndTheirModelsAreFleetWrite() throws Exception {
        expect(post(API + "/vehicles"), 200, SecurityRoles.RESOURCES_FLEET_WRITE);
        expect(patch(API + "/vehicle-types/1"), 200, SecurityRoles.RESOURCES_FLEET_WRITE);
        expect(post(API + "/vehicles"), 403, SecurityRoles.RESOURCES_WRITE, SecurityRoles.RESOURCES_LOCATION_WRITE);
    }

    @Test
    void recordingWhereAVehicleIsIsLocationWriteAndOnlyThat() throws Exception {
        expect(post(API + "/vehicles/1/locations"), 200, SecurityRoles.RESOURCES_LOCATION_WRITE);
        expect(post(API + "/vehicles/1/locations"), 403, SecurityRoles.RESOURCES_FLEET_WRITE, SecurityRoles.RESOURCES_WRITE);
        expect(patch(API + "/vehicles/1"), 403, SecurityRoles.RESOURCES_LOCATION_WRITE);
    }

    @Test
    void deletingIsResourcesDeleteAndWritingDoesNotImplyIt() throws Exception {
        expect(delete(API + "/people/1"), 200, SecurityRoles.RESOURCES_DELETE);
        expect(delete(API + "/vehicles/1"), 200, SecurityRoles.RESOURCES_DELETE);
        expect(delete(API + "/people/1"), 403, SecurityRoles.RESOURCES_WRITE, SecurityRoles.RESOURCES_FLEET_WRITE);
    }

    @Test
    void aRouteNobodyDeclaredIsDenied() throws Exception {
        expect(post(API + "/undeclared"), 403, SecurityRoles.RESOURCES_WRITE, SecurityRoles.RESOURCES_FLEET_WRITE,
                SecurityRoles.RESOURCES_LOCATION_WRITE, SecurityRoles.RESOURCES_DELETE);
    }

    @Test
    void writingThroughActuatorIsOpsWriteAndReadingItOpsMetrics() throws Exception {
        expect(post("/actuator/republish"), 403, SecurityRoles.OPS_METRICS, SecurityRoles.RESOURCES_WRITE);
        expect(get("/actuator/republish"), 403, SecurityRoles.RESOURCES_READ);
    }

    private void expect(MockHttpServletRequestBuilder request, int status, String... roles) throws Exception {
        mockMvc.perform(request.with(withRoles(roles))).andExpect(status().is(status));
    }

    private static JwtRequestPostProcessor withRoles(String... roles) {
        String[] authorities = new String[roles.length];
        for (int index = 0; index < roles.length; index++) {
            authorities[index] = SecurityAuthorityPrefixes.ROLE_PREFIX + roles[index];
        }
        return jwt().authorities(AuthorityUtils.createAuthorityList(authorities));
    }

    @RestController
    @RequestMapping(SecurityConfiguration.API)
    static class Probes {

        @GetMapping({"/people", "/vehicles/positions"})
        String read() {
            return "ok";
        }

        @PostMapping({"/people", "/units", "/parking-sites", "/vehicles", "/vehicles/{id}/locations", "/units/{id}/members/{person}/leave",
                "/undeclared"})
        String create() {
            return "ok";
        }

        @PatchMapping({"/people/{id}", "/vehicles/{id}", "/vehicle-types/{id}"})
        String patchIt(@PathVariable String id) {
            return "ok";
        }

        @PutMapping("/units/{id}/members")
        String replace(@PathVariable String id) {
            return "ok";
        }

        @DeleteMapping({"/people/{id}", "/vehicles/{id}"})
        String remove(@PathVariable String id) {
            return "ok";
        }
    }
}
