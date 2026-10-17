package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Que es una cualificacion: lo que decide si lleva un permiso de conducir y que exige un modelo. */
public enum QualificationKind {

    /** Un permiso de conducir por carretera: lleva cual ({@link DrivingLicence}). */
    DRIVING_LICENCE,

    /** La habilitacion para conducir u operar un vehiculo: de via auxiliar, una bivial. */
    VEHICLE_OPERATION,

    /** Seguridad: trabajos en proximidad de tension, primeros auxilios, aptitud medica (solo la fecha). */
    SAFETY,

    OTHER
}
