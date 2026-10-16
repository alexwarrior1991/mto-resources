package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** El equipo ferroviario de una bivial: 9A (ruedas de carril con traccion propia, el preferido), 9B (high ride: traccion y freno por friccion del neumatico; un 9B sin mejorar no deberia ir a pendientes de 1/75 o mas) y 9C (low ride: el neumatico sobre el carril con guias abatibles). */
public enum RailGearType {
    RAIL_WHEEL_DRIVE, FRICTION_DRIVE, ROAD_WHEEL_DRIVE
}
