package com.alejandro.mtoresources.application.dto.report;

import java.time.Instant;
import java.util.List;

/**
 * Donde no casan el perfil de puesto de Keycloak ({@code mto-position-*}, lo que alguien puede hacer) y la
 * categoria de su ficha (el puesto que ocupa). Permiso no es puesto: un suplente puede tener otro perfil a
 * proposito; el informe lo dice, no lo arregla. Se calcula al pedirlo, leyendo Keycloak.
 *
 * <p>Ejemplo, {@code GET /reports/position-discrepancies}:</p>
 * <pre>{@code
 * {"generatedAt": "2026-10-19T07:00:00Z", "peopleChecked": 42, "usersWithPosition": 39,
 *  "wrongProfile": [{"personCode": "PER-000007", "username": "equipo.jefe", "category": "TEAM_LEADER",
 *                    "expectedProfile": "mto-position-team-leader", "profiles": ["mto-position-worker"], …}],
 *  "severalProfiles": [], "withoutProfile": [], "withoutRecord": [{"username": "old.user", "profiles": [...], …}]}
 * }</pre>
 *
 * @param peopleChecked     las personas activas con usuario
 * @param usersWithPosition los usuarios de Keycloak con algun perfil de puesto
 * @param wrongProfile      con un perfil de puesto que no es el de su categoria
 * @param severalProfiles   con mas de uno
 * @param withoutProfile    activas, con usuario y sin el perfil de su categoria (ni otro)
 * @param withoutRecord     usuarios con perfil de puesto sin ficha activa aqui
 */
public record PositionDiscrepancyReport(
        Instant generatedAt,
        int peopleChecked,
        int usersWithPosition,
        List<PositionDiscrepancy> wrongProfile,
        List<PositionDiscrepancy> severalProfiles,
        List<PositionDiscrepancy> withoutProfile,
        List<PositionDiscrepancy> withoutRecord
) {
}
