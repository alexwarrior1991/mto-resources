package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Que es un instrumento: lo que se lleva al tajo y se calibra. */
public enum InstrumentKind {

    /** Pertiga de puesta a tierra (y en cortocircuito) de la catenaria. */
    EARTHING_POLE,

    /** Detector de ausencia de tension. */
    VOLTAGE_DETECTOR,

    /** Equipo de medida: de la geometria del hilo, de la altura, de la tension mecanica. */
    MEASURING,

    OTHER
}
