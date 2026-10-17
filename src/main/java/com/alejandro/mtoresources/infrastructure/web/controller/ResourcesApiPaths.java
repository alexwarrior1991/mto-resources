package com.alejandro.mtoresources.infrastructure.web.controller;

/** Las rutas de la API, en un solo sitio: las comparten los controladores, la seguridad y los tests. */
public final class ResourcesApiPaths {

    public static final String BASE = "/api/v1/resources";

    public static final String PEOPLE = BASE + "/people";
    public static final String ME = BASE + "/me";
    public static final String UNITS = BASE + "/units";
    public static final String PARKING_SITES = BASE + "/parking-sites";
    public static final String QUALIFICATION_TYPES = BASE + "/qualification-types";
    public static final String PERSON_QUALIFICATIONS = PEOPLE + "/{personId}/qualifications";
    public static final String VEHICLE_TYPES = BASE + "/vehicle-types";
    public static final String VEHICLES = BASE + "/vehicles";
    public static final String VEHICLE_DOCUMENTS = VEHICLES + "/{vehicleId}/documents";
    public static final String OPERATIONS = BASE + "/operations";

    private ResourcesApiPaths() {
    }
}
