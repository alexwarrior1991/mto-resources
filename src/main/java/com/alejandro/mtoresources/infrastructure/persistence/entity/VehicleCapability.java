package com.alejandro.mtoresources.infrastructure.persistence.entity;

/** Lo que sabe hacer un modelo. INSULATED_PLATFORM (con sus kV) y EARTHING importan para trabajar bajo una catenaria de 25 kV; TOWING, para remolcar un vagon o un remolque. */
public enum VehicleCapability {
    CRANE, ELEVATING_PLATFORM, INSULATED_PLATFORM, ROTATING_PLATFORM, PANTOGRAPH, CONTACT_WIRE_HOLDER, WIRE_DRUM, OCS_MEASURING, TRACK_MEASURING, EARTHING, TOWING, CREW_CABIN, WORKSHOP
}
