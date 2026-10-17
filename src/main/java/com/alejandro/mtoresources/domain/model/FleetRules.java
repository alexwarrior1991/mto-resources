package com.alejandro.mtoresources.domain.model;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;

import java.util.Optional;
import java.util.Set;

/**
 * Donde puede estar un vehiculo segun por donde se mueve. Sin framework.
 *
 * <ul>
 *   <li>De solo via: nunca por carretera, y aparcado siempre sobre una via (la suya con kp, o un sitio con
 *       via).</li>
 *   <li>De solo carretera: nunca en la via, y aparcado fuera de ella.</li>
 *   <li>Bivial: en las dos.</li>
 * </ul>
 */
public final class FleetRules {

    private FleetRules() {
    }

    /**
     * @param onTrackParking si el aparcamiento es sobre una via (la del evento, o un sitio con via)
     * @param offTrackParking si el aparcamiento es fuera de la via (un sitio con acceso por carretera, o una
     *                        descripcion o unas coordenadas sin via)
     */
    public static Optional<String> locationViolation(String vehicle, VehicleMobility mobility, VehicleLocationKind kind,
                                                     boolean onTrackParking, boolean offTrackParking) {
        return switch (kind) {
            case ON_TRACK -> mobility.runsOnTrack() ? Optional.empty()
                    : Optional.of(vehicle + " is a road-only vehicle: it cannot be on the track");
            case ON_ROAD -> mobility.runsOnRoad() ? Optional.empty()
                    : Optional.of(vehicle + " is a rail-only vehicle: it cannot be on the road");
            case PARKED -> parkedViolation(vehicle, mobility, onTrackParking, offTrackParking);
            case IN_WORKSHOP, ATTACHED, UNKNOWN -> Optional.empty();
        };
    }

    private static Optional<String> parkedViolation(String vehicle, VehicleMobility mobility, boolean onTrack, boolean offTrack) {
        if (mobility == VehicleMobility.RAIL_ONLY && !onTrack) {
            return Optional.of(vehicle + " is a rail-only vehicle: it is parked on a track (a track and a kp, or a site with rail access)");
        }
        if (mobility == VehicleMobility.ROAD_ONLY && !offTrack) {
            return Optional.of(vehicle + " is a road-only vehicle: it is parked off the track (a site with road access, a description or coordinates)");
        }
        return Optional.empty();
    }

    /**
     * Lo que un modelo dice de si mismo, reducido a lo que deciden las reglas: que cifras trae y que
     * capacidades tiene.
     *
     * @param railGear        si trae su equipo ferroviario ({@code railGearType})
     * @param roadRailFigures si trae cifras de bivial ({@code railGearType}, {@code onTrackingMinutes},
     *                        {@code needsPreparedAccess})
     * @param trackFigures    si trae cifras de via (ancho, velocidades en via, pendiente, radio, peralte)
     * @param roadFigures     si trae cifras de carretera (velocidad, permiso de conducir)
     */
    public record TypeShape(VehicleMobility mobility, VehicleCategory category, boolean railGear, boolean roadRailFigures,
                            boolean trackFigures, boolean transportModeAuthorised, boolean roadFigures,
                            Set<VehicleCapability> capabilities, boolean craneFigures, boolean platformFigures,
                            boolean insulationFigure, boolean towingFigure) {
    }

    /**
     * Un modelo coherente: su categoria es de su movilidad, solo una bivial trae equipo ferroviario (y
     * lo trae siempre), uno de solo carretera no trae cifras de via ni va por via abierta, uno de solo via
     * no trae cifras de carretera, y las cifras de grua, plataforma, aislamiento y remolque van con su
     * capacidad.
     */
    public static Optional<String> typeViolation(TypeShape type) {
        if (type.category().mobility() != type.mobility()) {
            return Optional.of("A " + type.category() + " is a " + type.category().mobility() + " vehicle, not " + type.mobility());
        }
        if (type.mobility() == VehicleMobility.ROAD_RAIL && !type.railGear()) {
            return Optional.of("A road-rail vehicle says its rail gear (railGearType: RAIL_WHEEL_DRIVE 9A, FRICTION_DRIVE 9B "
                    + "or ROAD_WHEEL_DRIVE 9C)");
        }
        if (type.mobility() != VehicleMobility.ROAD_RAIL && type.roadRailFigures()) {
            return Optional.of("Only a road-rail vehicle has rail gear, on-tracking time or a prepared access");
        }
        if (type.mobility() == VehicleMobility.ROAD_ONLY && (type.trackFigures() || type.transportModeAuthorised())) {
            return Optional.of("A road-only vehicle has no track figures and never runs on open track");
        }
        if (type.mobility() == VehicleMobility.RAIL_ONLY && type.roadFigures()) {
            return Optional.of("A rail-only vehicle has no road speed nor driving licence");
        }
        Set<VehicleCapability> capabilities = type.capabilities();
        if (type.craneFigures() && !capabilities.contains(VehicleCapability.CRANE)) {
            return Optional.of("Crane figures (craneCapacityT, craneReachM) need the CRANE capability");
        }
        boolean platform = capabilities.contains(VehicleCapability.ELEVATING_PLATFORM);
        if (type.platformFigures() && !platform) {
            return Optional.of("Platform figures (platformHeightM, platformLoadKg) need the ELEVATING_PLATFORM capability");
        }
        if ((capabilities.contains(VehicleCapability.INSULATED_PLATFORM) || capabilities.contains(VehicleCapability.ROTATING_PLATFORM))
                && !platform) {
            return Optional.of("An insulated or rotating platform is an ELEVATING_PLATFORM too");
        }
        if (type.insulationFigure() && !capabilities.contains(VehicleCapability.INSULATED_PLATFORM)) {
            return Optional.of("platformInsulationKv needs the INSULATED_PLATFORM capability");
        }
        if (type.towingFigure() && !capabilities.contains(VehicleCapability.TOWING)) {
            return Optional.of("towingCapacityT needs the TOWING capability");
        }
        return Optional.empty();
    }

    /**
     * Un vehiculo que va con otro: remolcado ({@code TOWED}) por uno con {@code TOWING} y por donde los
     * dos pueden ir (una plataforma tras una dresina, un remolque tras un camion, una dresina averiada
     * tras otra), o transportado ({@code CARRIED}) por uno de carretera (una bivial en gondola, una
     * dresina en un camion) si no es portatil; uno portatil, como una vagoneta, va en cualquiera.
     */
    public static Optional<String> attachmentViolation(String vehicle, VehicleMobility mobility, boolean portable,
                                                       VehicleAttachment attachment, String carrier, VehicleMobility carrierMobility,
                                                       Set<VehicleCapability> carrierCapabilities) {
        return switch (attachment) {
            case TOWED -> {
                if (!carrierCapabilities.contains(VehicleCapability.TOWING)) {
                    yield Optional.of(carrier + " cannot tow: its model has no TOWING capability");
                }
                boolean sameWay = (mobility.runsOnTrack() && carrierMobility.runsOnTrack())
                        || (mobility.runsOnRoad() && carrierMobility.runsOnRoad());
                yield sameWay ? Optional.empty()
                        : Optional.of(carrier + " (" + carrierMobility + ") cannot tow " + vehicle + " (" + mobility + "): they do not run on the same way");
            }
            case CARRIED -> portable || carrierMobility.runsOnRoad() ? Optional.empty()
                    : Optional.of(vehicle + " is not portable: only a road vehicle (a low loader, a truck) carries it, and "
                    + carrier + " is " + carrierMobility);
        };
    }

    // ------------------------------------------------------------- el conductor de un modelo

    /** Conductor de vehiculo auxiliar de via. */
    public static final String RAIL_DRIVER = "RAIL-DRIVER";
    /** Operador de maquina bivial. */
    public static final String ROAD_RAIL_OPERATOR = "RR-OPERATOR";

    /**
     * La habilitacion que exige conducir un modelo que no la dice, por su movilidad: una de solo via
     * autopropulsada, conductor de vehiculo auxiliar; una bivial, operador de bivial; una de carretera, el
     * permiso que pide. La misma regla rellena los modelos que ya existian (V8). Es el codigo del tipo de
     * cualificacion; vacio, ninguna (un vagon, una vagoneta, una de carretera que no dice permiso).
     */
    public static Optional<String> defaultDriverQualification(VehicleMobility mobility, boolean selfPropelled, DrivingLicence licence) {
        return switch (mobility) {
            case RAIL_ONLY -> selfPropelled ? Optional.of(RAIL_DRIVER) : Optional.empty();
            case ROAD_RAIL -> Optional.of(ROAD_RAIL_OPERATOR);
            case ROAD_ONLY -> Optional.ofNullable(licence).map(FleetRules::licenceQualification);
        };
    }

    /** El tipo de cualificacion de un permiso de conducir: {@code LIC-B}, {@code LIC-C}, {@code LIC-CE}. */
    public static String licenceQualification(DrivingLicence licence) {
        return switch (licence) {
            case B -> "LIC-B";
            case C -> "LIC-C";
            case C_E -> "LIC-CE";
        };
    }
}
