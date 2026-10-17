package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Por donde se mueve un vehiculo: lo que deciden las reglas de donde puede estar. */
public enum VehicleMobility {

    /** Solo por via (EN 14033): llega al tajo por la via, desde una cochera o un apartadero. */
    RAIL_ONLY,

    /** Bivial (EN 15746): va por carretera, encarrila en un punto de acceso, trabaja y desencarrila. */
    ROAD_RAIL,

    /** Solo por carretera: lleva al personal y el material hasta el acceso. */
    ROAD_ONLY;

    public boolean runsOnTrack() {
        return this != ROAD_ONLY;
    }

    public boolean runsOnRoad() {
        return this != RAIL_ONLY;
    }
}
