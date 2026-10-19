package com.alejandro.mtoresources.configuration.security;

/**
 * Roles de cliente de Keycloak que comprueba esta API, ya normalizados a autoridad de Spring.
 *
 * <p>Son los nombres que declara {@code keycloak/mto-resources-partial-import.json} en
 * mayusculas y con guion bajo: {@code resources-read} llega como {@code ROLE_RESOURCES_READ}.
 * Un rol que se anade aqui sin anadirlo alli no lo tiene nadie y todo responde 403. Ninguno implica
 * a otro: los compone el perfil (lo fija {@code ApiAuthorizationRulesTest}).</p>
 */
public final class SecurityRoles {

    private SecurityRoles() {
    }

    /** Consulta de personas, organigrama, equipos, sitios de aparcamiento, flota y ubicaciones. */
    public static final String RESOURCES_READ = "RESOURCES_READ";

    /**
     * Alta y modificacion de personas y de sus cualificaciones, unidades del organigrama, linemen de un
     * equipo, sitios de aparcamiento y el catalogo de tipos de cualificacion.
     */
    public static final String RESOURCES_WRITE = "RESOURCES_WRITE";

    /** Alta y modificacion de vehiculos y de sus documentos, de sus tipos (los modelos de la flota) y de los instrumentos. */
    public static final String RESOURCES_FLEET_WRITE = "RESOURCES_FLEET_WRITE";

    /**
     * Registrar donde esta un vehiculo: lo que hacen el jefe de equipo y el supervisor en el tajo sin
     * poder tocar la ficha del vehiculo. Solo abre {@code POST /vehicles/{id}/locations}.
     */
    public static final String RESOURCES_LOCATION_WRITE = "RESOURCES_LOCATION_WRITE";

    /**
     * Mandar las posiciones de los dispositivos GPS: solo {@code POST /telematics/positions}. Lo tiene la
     * cuenta de servicio {@code mto-telematics-svc}, no una persona.
     */
    public static final String RESOURCES_TELEMATICS_WRITE = "RESOURCES_TELEMATICS_WRITE";

    /** Todo DELETE (desactivar una persona o una unidad, retirar un vehiculo) y desactivar con PATCH. */
    public static final String RESOURCES_DELETE = "RESOURCES_DELETE";

    /** Lectura de los endpoints de Actuator. */
    public static final String OPS_METRICS = "OPS_METRICS";

    /** Operaciones de Actuator que modifican estado: el redrive del outbox y la republicacion. */
    public static final String OPS_WRITE = "OPS_WRITE";
}
