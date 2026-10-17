package com.alejandro.mtoresources.configuration;

import com.alejandro.mtoresources.application.dto.error.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Metadatos, esquema de seguridad, etiquetas y respuestas de error reutilizables del OpenAPI.
 *
 * <p>A diferencia de {@code mto-stock}, las rutas no se declaran aqui a mano: springdoc las lee de
 * las anotaciones de los controladores, que son la unica fuente de verdad de la API. Lo que si se
 * fija aqui es lo transversal, que ningun controlador deberia repetir.</p>
 */
@Configuration
public class OpenApiDocumentationConfiguration {

    private static final String JSON = "application/json";
    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI mtoResourcesOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                // Requisito global: cada operacion documentada pide el bearer salvo que lo anule
                // explicitamente, de modo que anadir un endpoint nuevo no lo publica como abierto.
                .security(List.of(new SecurityRequirement().addList(BEARER_AUTH)))
                .servers(List.of(
                        new Server().url("http://localhost:8089").description("Local development server"),
                        new Server().url("http://localhost:8090/api/resources").description("Through mto-gateway")
                ))
                .tags(tags())
                .components(components());
    }

    private static Info apiInfo() {
        return new Info()
                .title("People, Organization and Fleet API")
                .description("The people of the domain and their hierarchy (worker, team leader, supervisor, "
                        + "chief supervisor, chief of maintenance or construction, project manager), the teams "
                        + "that work in a vehicle with their linemen, and the fleet of rail, road and road-rail "
                        + "vehicles with where each one is. Shared by maintenance and construction.")
                .version("v1")
                .contact(new Contact()
                        .name("MTO Platform Team")
                        .email("support@example.com"))
                .license(new License()
                        .name("Proprietary"));
    }

    private static List<Tag> tags() {
        return List.of(
                tag("People", "Workers, team leaders, supervisors and chiefs, with their optional Keycloak user."),
                tag("Organization", "The organization chart: project, departments, chief supervisions, supervisions and teams of one leader and 2 to 6 linemen."),
                tag("Parking Sites", "Known places where a vehicle can be parked or stabled: sidings, station tracks, depots, road parkings."),
                tag("Vehicle Types", "Vehicle models: rail-only, road-rail and road-only, with their characteristics and capabilities."),
                tag("Vehicles", "The fleet: each vehicle, its status and where it is now."),
                tag("Vehicle Locations", "Where a vehicle has been: manual entries and the ones deduced from the shifts."),
                tag("Operations", "Background operations answered with 202 when they take longer than the request is willing to wait.")
        );
    }

    private static Components components() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Keycloak-issued JWT access token. Send it as \"Authorization: Bearer <token>\"."))
                .addResponses("BadRequest", errorResponse("Invalid request, validation failure or malformed JSON.", HttpStatus.BAD_REQUEST))
                .addResponses("Unauthorized", errorResponse("Missing, expired or otherwise invalid bearer token.", HttpStatus.UNAUTHORIZED))
                .addResponses("Forbidden", errorResponse("The authenticated user lacks the role required by this operation.", HttpStatus.FORBIDDEN))
                .addResponses("NotFound", errorResponse("The requested resource or business aggregate was not found.", HttpStatus.NOT_FOUND))
                .addResponses("Conflict", errorResponse("The request conflicts with current state: duplicate code, stale version, an organization or fleet rule.", HttpStatus.CONFLICT))
                .addResponses("UnprocessableContent", errorResponse("The request is syntactically valid but violates a domain rule.", HttpStatus.UNPROCESSABLE_CONTENT))
                .addResponses("TooManyRequests", errorResponse("Too many background operations are running; retry after the Retry-After seconds.", HttpStatus.TOO_MANY_REQUESTS))
                .addResponses("InternalServerError", errorResponse("Unexpected server error. Internal details are not exposed to clients.", HttpStatus.INTERNAL_SERVER_ERROR));
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ApiErrorResponse.class);
        schemas.forEach(components::addSchemas);
        return components;
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static ApiResponse errorResponse(String description, HttpStatus status) {
        return new ApiResponse()
                .description(status.value() + " - " + description)
                .content(new Content().addMediaType(JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}
