package com.alejandro.mtoresources.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lo que este servicio lee de Keycloak, solo para el informe de discrepancias: quien tiene cada perfil de
 * puesto. Nunca escribe el realm. Si Keycloak no responde, rechaza la cuenta de servicio o la lectura esta
 * apagada, {@code KeycloakUnavailableException} (503 {@code KCK-503}).
 */
public interface KeycloakDirectory {

    /**
     * Los usuarios con ese rol de realm asignado directamente (no por un grupo ni dentro de otro compuesto),
     * todos. Vacio si el rol no existe en el realm.
     */
    List<DirectoryUser> realmRoleMembers(String roleName);

    /** Un usuario de Keycloak: su id, su nombre de usuario y si esta activo; lo demas que mande se ignora. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DirectoryUser(String id, String username, Boolean enabled) {
    }
}
