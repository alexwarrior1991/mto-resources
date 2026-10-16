package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Que es un vehiculo. Cada categoria admite una sola movilidad. */
public enum VehicleCategory {

    /** Dresina o castillete de catenaria (motor tower car): plataforma elevadora, grua, a veces pantografo de medida. */
    CATENARY_TOWER_CAR(VehicleMobility.RAIL_ONLY),
    /** Tren de tendido: bobinas e hilo de contacto con su tension mecanica. */
    WIRING_TRAIN(VehicleMobility.RAIL_ONLY),
    /** Dresina de via con grua y plataforma de carga. */
    TRACK_MAINTENANCE_CAR(VehicleMobility.RAIL_ONLY),
    /** Vehiculo de auscultacion: mide la via y la posicion de la catenaria. */
    INSPECTION_CAR(VehicleMobility.RAIL_ONLY),
    LOCOMOTIVE(VehicleMobility.RAIL_ONLY),
    /** Vagon o plataforma remolcada. */
    RAIL_WAGON(VehicleMobility.RAIL_ONLY),
    /** Vagoneta, motorizada o de empuje (EN 13977). */
    TROLLEY(VehicleMobility.RAIL_ONLY),

    /** Plataforma elevadora bivial. */
    ROAD_RAIL_MEWP(VehicleMobility.ROAD_RAIL),
    ROAD_RAIL_TRUCK(VehicleMobility.ROAD_RAIL),
    ROAD_RAIL_EXCAVATOR(VehicleMobility.ROAD_RAIL),
    ROAD_RAIL_TRACTOR(VehicleMobility.ROAD_RAIL),
    ROAD_RAIL_TRAILER(VehicleMobility.ROAD_RAIL),

    VAN(VehicleMobility.ROAD_ONLY),
    PICKUP(VehicleMobility.ROAD_ONLY),
    CREW_BUS(VehicleMobility.ROAD_ONLY),
    CRANE_TRUCK(VehicleMobility.ROAD_ONLY),
    TRUCK(VehicleMobility.ROAD_ONLY),
    /** Gondola: lleva a las biviales y a las vagonetas hasta el acceso. */
    LOW_LOADER(VehicleMobility.ROAD_ONLY),
    CAR(VehicleMobility.ROAD_ONLY);

    private final VehicleMobility mobility;

    VehicleCategory(VehicleMobility mobility) {
        this.mobility = mobility;
    }

    public VehicleMobility mobility() {
        return mobility;
    }
}
