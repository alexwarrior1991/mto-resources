package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Donde esta un vehiculo: aparcado (en un sitio, en una via y un kp, o donde dice su descripcion), en la via trabajando, por carretera, en un taller, enganchado a otro (remolcado o en gondola) o no se sabe. */
public enum VehicleLocationKind {
    PARKED, ON_TRACK, ON_ROAD, IN_WORKSHOP, ATTACHED, UNKNOWN
}
