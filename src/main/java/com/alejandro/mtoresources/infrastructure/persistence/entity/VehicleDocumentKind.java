package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Los documentos con caducidad de un vehiculo: uno de cada tipo. */
public enum VehicleDocumentKind {

    /** La inspeccion tecnica de carretera (ITV, en Israel el «test» anual). */
    ROAD_INSPECTION,

    /** La inspeccion ferroviaria que le autoriza a circular por la via. */
    RAIL_INSPECTION,

    INSURANCE,

    /** El certificado de la grua. */
    CRANE_CERTIFICATE,

    /** El certificado de la plataforma elevadora. */
    PLATFORM_CERTIFICATE,

    OTHER
}
