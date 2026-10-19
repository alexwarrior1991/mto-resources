package com.alejandro.mtoresources.configuration.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Cadena de filtros de la API. La aplicación es un <em>resource server</em>: no emite tokens ni
 * guarda usuarios, solo valida los JWT que emite Keycloak y traduce sus roles a autoridades.
 *
 * <p>No hay CORS: todo navegador llega por {@code mto-gateway}, que lo resuelve y quita
 * {@code Origin} antes de llamar. Sin {@code .cors()} ni {@code OPTIONS} abierto, un preflight que
 * llegara aquí directamente pide token como cualquier otra petición.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    /** Raíz de los recursos de negocio. Coincide con el {@code @RequestMapping} de cada controlador. */
    static final String API = "/api/v1/resources";

    private static final String[] API_DOCS = {
            "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
            "/swagger-ui/**", "/swagger-ui.html"
    };

    private static final HttpMethod[] WRITE_METHODS = {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH};

    private final SecurityProperties securityProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfiguration(
            SecurityProperties securityProperties,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.securityProperties = securityProperties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public KeycloakJwtAuthenticationConverter jwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter(securityProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            KeycloakJwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> {
                    // Las sondas de arranque y de vida las consulta el orquestador, que no tiene
                    // token. El resto del detalle de health lo gobierna
                    // 'management.endpoint.health.show-details'.
                    authorize.requestMatchers(
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info"
                    ).permitAll();

                    // La documentación describe la superficie completa de la API. En un entorno
                    // desplegado es el mejor mapa posible para quien busque un hueco, así que se
                    // abre solo donde la configuración lo pide.
                    if (securityProperties.exposeApiDocs()) {
                        authorize.requestMatchers(API_DOCS).permitAll();
                    }

                    // Todo Actuator cerrado salvo health e info. Lo que modifica va antes que la
                    // regla general y con su propio permiso: en Actuator las @WriteOperation viajan
                    // por POST y las @DeleteOperation por DELETE; el resto es lectura.
                    authorize.requestMatchers(HttpMethod.POST, "/actuator/**").hasRole(SecurityRoles.OPS_WRITE);
                    authorize.requestMatchers(HttpMethod.DELETE, "/actuator/**").hasRole(SecurityRoles.OPS_WRITE);
                    authorize.requestMatchers("/actuator/**").hasRole(SecurityRoles.OPS_METRICS);

                    // El orden importa: cada petición se resuelve con la primera regla que encaja.
                    authorize.requestMatchers(HttpMethod.GET, API + "/**").hasRole(SecurityRoles.RESOURCES_READ);
                    // HEAD lo sirve el mismo handler que GET y revela si un recurso existe.
                    authorize.requestMatchers(HttpMethod.HEAD, API + "/**").hasRole(SecurityRoles.RESOURCES_READ);

                    // Registrar donde esta un vehiculo va antes que la regla de la flota: es lo que
                    // hace en el tajo quien no puede tocar la ficha del vehiculo.
                    authorize.requestMatchers(HttpMethod.POST, API + "/vehicles/*/locations")
                            .hasRole(SecurityRoles.RESOURCES_LOCATION_WRITE);

                    // Las posiciones de los GPS: una cuenta de servicio que no toca nada mas.
                    authorize.requestMatchers(HttpMethod.POST, API + "/telematics/positions")
                            .hasRole(SecurityRoles.RESOURCES_TELEMATICS_WRITE);

                    // La flota: vehiculos (y sus documentos y reparaciones), sus tipos y los instrumentos.
                    for (HttpMethod method : WRITE_METHODS) {
                        authorize.requestMatchers(method, API + "/vehicles", API + "/vehicles/**",
                                API + "/vehicle-types", API + "/vehicle-types/**",
                                API + "/instruments", API + "/instruments/**").hasRole(SecurityRoles.RESOURCES_FLEET_WRITE);
                    }

                    // Personal y organizacion: personas (y sus cualificaciones y ausencias), unidades, linemen, sitios de
                    // aparcamiento y el catalogo de tipos de cualificacion.
                    for (HttpMethod method : WRITE_METHODS) {
                        authorize.requestMatchers(method, API + "/people", API + "/people/**",
                                API + "/units", API + "/units/**",
                                API + "/parking-sites", API + "/parking-sites/**",
                                API + "/qualification-types", API + "/qualification-types/**").hasRole(SecurityRoles.RESOURCES_WRITE);
                    }

                    authorize.requestMatchers(HttpMethod.DELETE, API + "/**").hasRole(SecurityRoles.RESOURCES_DELETE);

                    // Cualquier otra escritura bajo la API se deniega: un recurso nuevo declara su
                    // permiso o no existe.
                    authorize.requestMatchers(API + "/**").denyAll();

                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    /**
     * Se construye con el JWK Set en lugar de con el descubrimiento por issuer: {@code
     * JwtDecoders.fromIssuerLocation()} hace una llamada HTTP bloqueante al crear el bean, de modo
     * que la aplicación no arranca si Keycloak todavía no sirve el {@code
     * .well-known/openid-configuration}. Con el JWK Set la descarga es perezosa —la primera vez que
     * llega un token— y un reinicio simultáneo de los dos servicios deja de ser un fallo de
     * arranque.
     */
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        OAuth2ResourceServerProperties.Jwt jwtProperties = properties.getJwt();
        String issuerUri = jwtProperties.getIssuerUri();

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(resolveJwkSetUri(jwtProperties))
                .build();

        // Se parte del validador por defecto en vez de reemplazarlo: incluye la comprobación de
        // emisor y de vigencia, y hereda las que Spring Security añada en versiones futuras.
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator()
        ));

        return jwtDecoder;
    }

    /**
     * Keycloak publica el JWK Set en una ruta fija bajo el realm. Se respeta {@code jwk-set-uri} si
     * está configurado, para no atar la aplicación a esa convención.
     */
    static String resolveJwkSetUri(OAuth2ResourceServerProperties.Jwt jwtProperties) {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            return jwtProperties.getJwkSetUri();
        }

        return jwtProperties.getIssuerUri() + "/protocol/openid-connect/certs";
    }

    /**
     * Que {@code required-audience} esté relleno lo garantiza {@link SecurityProperties} en el
     * arranque, así que aquí no hay ninguna rama que deje pasar el token por falta de
     * configuración: o se valida la audiencia, o se ha desactivado de forma explícita.
     */
    private OAuth2TokenValidator<Jwt> audienceValidator() {
        if (!securityProperties.audienceValidationEnabled()) {
            return jwt -> OAuth2TokenValidatorResult.success();
        }

        return new JwtAudienceValidator(securityProperties.requiredAudience());
    }
}
