package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Si un vehiculo puede trabajar: OPERATIONAL y LIMITED (con su motivo) entran en un turno; UNDER_REPAIR y OUT_OF_SERVICE no. */
public enum VehicleOperationalStatus {
    OPERATIONAL, LIMITED, UNDER_REPAIR, OUT_OF_SERVICE;

    /** Si puede entrar en un turno. */
    public boolean canWork() {
        return this == OPERATIONAL || this == LIMITED;
    }
}
