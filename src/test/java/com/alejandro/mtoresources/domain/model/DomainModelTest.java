package com.alejandro.mtoresources.domain.model;

import com.alejandro.mtoresources.infrastructure.persistence.entity.DrivingLicence;
import com.alejandro.mtoresources.infrastructure.persistence.entity.OrganizationUnitKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.PersonCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleAttachment;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCapability;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleCategory;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleLocationKind;
import com.alejandro.mtoresources.infrastructure.persistence.entity.VehicleMobility;
import com.alejandro.mtoresources.infrastructure.persistence.entity.WorkDiscipline;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las reglas sin framework: la dotacion de un equipo, el organigrama y donde puede estar un vehiculo. */
class DomainModelTest {

    // ------------------------------------------------------------------- dotacion

    @Test
    void aTeamWorksWithOneLeaderAndTwoToSixLinemen() {
        assertTrue(CrewComposition.completeViolation("Team A", false, 4).isPresent(), "Without a leader");
        assertTrue(CrewComposition.completeViolation("Team A", true, 1).isPresent(), "One lineman");
        assertTrue(CrewComposition.completeViolation("Team A", true, 7).isPresent(), "Seven linemen");
        assertTrue(CrewComposition.completeViolation("Team A", true, 2).isEmpty());
        assertTrue(CrewComposition.completeViolation("Team A", true, 6).isEmpty());
        assertEquals(2, CrewComposition.MIN_LINEMEN);
        assertEquals(6, CrewComposition.MAX_LINEMEN);
    }

    @Test
    void oneMoreOnSixAndOneLessOnTwoAreRefused() {
        assertTrue(CrewComposition.addViolation("Team A", 6).isPresent());
        assertTrue(CrewComposition.addViolation("Team A", 5).isEmpty());
        assertTrue(CrewComposition.removeViolation("Team A", 2).isPresent());
        assertTrue(CrewComposition.removeViolation("Team A", 3).isEmpty());
        assertTrue(CrewComposition.limitViolation("Team A", 7).isPresent());
        assertTrue(CrewComposition.limitViolation("Team A", 0).isEmpty(), "An incomplete crew is only bounded above");
    }

    @Test
    void theCrewInTheVehicleIsTheLeaderAndTheLinemen() {
        assertEquals(3, CrewComposition.crewSize(true, 2));
        assertEquals(7, CrewComposition.crewSize(true, 6));
        assertEquals(4, CrewComposition.crewSize(false, 4));
    }

    // -------------------------------------------------------------- organigrama

    @Test
    void aUnitHangsFromAStrictlyHigherLevelAndTheProjectFromNothing() {
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.PROJECT, null).isEmpty());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.PROJECT, OrganizationUnitKind.PROJECT).isPresent());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.TEAM, null).isPresent());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.TEAM, OrganizationUnitKind.TEAM).isPresent());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.SUPERVISION, OrganizationUnitKind.TEAM).isPresent());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.TEAM, OrganizationUnitKind.SUPERVISION).isEmpty());
        assertTrue(OrganizationRules.parentViolation(OrganizationUnitKind.TEAM, OrganizationUnitKind.DEPARTMENT).isEmpty(),
                "Levels may be skipped");
    }

    @Test
    void aUnitIsLedByTheCategoryOfItsLevelOrAbove() {
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.TEAM, WorkDiscipline.MAINTENANCE, PersonCategory.WORKER).isPresent());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.TEAM, WorkDiscipline.MAINTENANCE, PersonCategory.TEAM_LEADER).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.TEAM, WorkDiscipline.MAINTENANCE, PersonCategory.SUPERVISOR).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.SUPERVISION, WorkDiscipline.MAINTENANCE, PersonCategory.TEAM_LEADER).isPresent());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.CHIEF_SUPERVISION, WorkDiscipline.MAINTENANCE, PersonCategory.SUPERVISOR).isPresent());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.CHIEF_SUPERVISION, WorkDiscipline.MAINTENANCE, PersonCategory.CHIEF_SUPERVISOR).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.PROJECT, null, PersonCategory.CHIEF_MAINTENANCE).isPresent());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.PROJECT, null, PersonCategory.PROJECT_MANAGER).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.TEAM, WorkDiscipline.MAINTENANCE, null).isEmpty(), "A vacancy");
    }

    @Test
    void aDepartmentIsLedByTheChiefOfItsDisciplineOrTheProjectManager() {
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.DEPARTMENT, WorkDiscipline.MAINTENANCE, PersonCategory.CHIEF_MAINTENANCE).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.DEPARTMENT, WorkDiscipline.MAINTENANCE, PersonCategory.PROJECT_MANAGER).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.DEPARTMENT, WorkDiscipline.MAINTENANCE, PersonCategory.CHIEF_CONSTRUCTION).isPresent());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.DEPARTMENT, WorkDiscipline.CONSTRUCTION, PersonCategory.CHIEF_CONSTRUCTION).isEmpty());
        assertTrue(OrganizationRules.headViolation(OrganizationUnitKind.DEPARTMENT, WorkDiscipline.CONSTRUCTION, PersonCategory.CHIEF_MAINTENANCE).isPresent());
    }

    @Test
    void aLinemanIsAWorkerOrATeamLeaderGoingWithAnother() {
        assertTrue(OrganizationRules.canBeLineman(PersonCategory.WORKER));
        assertTrue(OrganizationRules.canBeLineman(PersonCategory.TEAM_LEADER));
        assertFalse(OrganizationRules.canBeLineman(PersonCategory.SUPERVISOR));
        assertFalse(OrganizationRules.canBeLineman(PersonCategory.PROJECT_MANAGER));
    }

    // -------------------------------------------------------------------- flota

    @Test
    void everyCategoryAdmitsOneMobility() {
        assertEquals(VehicleMobility.RAIL_ONLY, VehicleCategory.CATENARY_TOWER_CAR.mobility());
        assertEquals(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_TRUCK.mobility());
        assertEquals(VehicleMobility.ROAD_ONLY, VehicleCategory.LOW_LOADER.mobility());
        Arrays.stream(VehicleCategory.values()).forEach(category ->
                assertTrue(FleetRules.typeViolation(shape(category.mobility(), category, category.mobility() == VehicleMobility.ROAD_RAIL,
                        Set.of())).isEmpty(), category.name()));
        assertTrue(FleetRules.typeViolation(shape(VehicleMobility.ROAD_ONLY, VehicleCategory.CATENARY_TOWER_CAR, false, Set.of())).isPresent());
    }

    @Test
    void onlyARoadRailModelHasRailGearAndItAlwaysHasIt() {
        assertTrue(FleetRules.typeViolation(shape(VehicleMobility.ROAD_RAIL, VehicleCategory.ROAD_RAIL_TRUCK, false, Set.of())).isPresent());
        assertTrue(FleetRules.typeViolation(shape(VehicleMobility.RAIL_ONLY, VehicleCategory.TROLLEY, true, Set.of())).isPresent());
    }

    @Test
    void theFiguresOfACapabilityTravelWithIt() {
        FleetRules.TypeShape craneWithoutCapability = new FleetRules.TypeShape(VehicleMobility.ROAD_ONLY, VehicleCategory.CRANE_TRUCK,
                false, false, false, false, false, Set.of(), true, false, false, false);
        assertTrue(FleetRules.typeViolation(craneWithoutCapability).isPresent());
        FleetRules.TypeShape insulatedWithoutPlatform = shape(VehicleMobility.RAIL_ONLY, VehicleCategory.CATENARY_TOWER_CAR, false,
                EnumSet.of(VehicleCapability.INSULATED_PLATFORM));
        assertTrue(FleetRules.typeViolation(insulatedWithoutPlatform).isPresent());
        FleetRules.TypeShape roadOnlyOnOpenTrack = new FleetRules.TypeShape(VehicleMobility.ROAD_ONLY, VehicleCategory.VAN,
                false, false, false, true, false, Set.of(), false, false, false, false);
        assertTrue(FleetRules.typeViolation(roadOnlyOnOpenTrack).isPresent());
    }

    @Test
    void aRailOnlyVehicleIsNeverOnTheRoadAndIsParkedOnATrack() {
        assertTrue(FleetRules.locationViolation("DR-02", VehicleMobility.RAIL_ONLY, VehicleLocationKind.ON_ROAD, false, false).isPresent());
        assertTrue(FleetRules.locationViolation("DR-02", VehicleMobility.RAIL_ONLY, VehicleLocationKind.ON_TRACK, false, false).isEmpty());
        assertTrue(FleetRules.locationViolation("DR-02", VehicleMobility.RAIL_ONLY, VehicleLocationKind.PARKED, false, true).isPresent(),
                "Not in a road car park");
        assertTrue(FleetRules.locationViolation("DR-02", VehicleMobility.RAIL_ONLY, VehicleLocationKind.PARKED, true, false).isEmpty());
    }

    @Test
    void aRoadOnlyVehicleIsNeverOnTheTrackAndARoadRailOneIsOnBoth() {
        assertTrue(FleetRules.locationViolation("VAN-1", VehicleMobility.ROAD_ONLY, VehicleLocationKind.ON_TRACK, false, false).isPresent());
        assertTrue(FleetRules.locationViolation("VAN-1", VehicleMobility.ROAD_ONLY, VehicleLocationKind.PARKED, true, false).isPresent());
        assertTrue(FleetRules.locationViolation("VAN-1", VehicleMobility.ROAD_ONLY, VehicleLocationKind.PARKED, false, true).isEmpty());
        assertTrue(FleetRules.locationViolation("RR-07", VehicleMobility.ROAD_RAIL, VehicleLocationKind.ON_TRACK, false, false).isEmpty());
        assertTrue(FleetRules.locationViolation("RR-07", VehicleMobility.ROAD_RAIL, VehicleLocationKind.ON_ROAD, false, false).isEmpty());
        assertTrue(FleetRules.locationViolation("RR-07", VehicleMobility.ROAD_RAIL, VehicleLocationKind.PARKED, false, true).isEmpty());
    }

    @Test
    void towingNeedsTheCapabilityAndTheSameWayAndCarryingARoadCarrier() {
        Set<VehicleCapability> tows = EnumSet.of(VehicleCapability.TOWING);
        assertTrue(FleetRules.attachmentViolation("TR-01", VehicleMobility.RAIL_ONLY, false, VehicleAttachment.TOWED,
                "DR-02", VehicleMobility.RAIL_ONLY, tows).isEmpty());
        assertTrue(FleetRules.attachmentViolation("TR-01", VehicleMobility.RAIL_ONLY, false, VehicleAttachment.TOWED,
                "DR-02", VehicleMobility.RAIL_ONLY, Set.of()).isPresent(), "No TOWING");
        assertTrue(FleetRules.attachmentViolation("TR-01", VehicleMobility.RAIL_ONLY, false, VehicleAttachment.TOWED,
                "VAN-1", VehicleMobility.ROAD_ONLY, tows).isPresent(), "A van does not tow a wagon");
        assertTrue(FleetRules.attachmentViolation("RR-07", VehicleMobility.ROAD_RAIL, false, VehicleAttachment.CARRIED,
                "LL-01", VehicleMobility.ROAD_ONLY, Set.of()).isEmpty(), "A road-rail on a low loader");
        assertTrue(FleetRules.attachmentViolation("DR-02", VehicleMobility.RAIL_ONLY, false, VehicleAttachment.CARRIED,
                "DR-03", VehicleMobility.RAIL_ONLY, Set.of()).isPresent());
        assertTrue(FleetRules.attachmentViolation("VG-01", VehicleMobility.RAIL_ONLY, true, VehicleAttachment.CARRIED,
                "DR-03", VehicleMobility.RAIL_ONLY, Set.of()).isEmpty(), "A portable trolley goes in anything");
    }

    // ------------------------------------------------------------- punto de acceso a la via

    /**
     * Encarrilar en un punto de acceso: habilitado, en la via de la ubicacion, que admita biviales si lo es el
     * vehiculo, preparado si el modelo lo exige y que aguante su peso (solo si se conocen las dos cifras).
     */
    @Test
    void anAccessPointIsEnabledOnTheTrackOfTheLocationAdmitsRoadRailAndTakesTheWeightOfTheModel() {
        FleetRules.AccessPointShape prepared = access(true, 3L, "PREPARED_ACCESS", true, "12.500", "40.000");
        BigDecimal gross = new BigDecimal("32.00");
        BigDecimal axle = new BigDecimal("8.00");
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, gross, axle, prepared, 3L).isEmpty());
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, gross, axle, prepared, null).isEmpty(),
                "A location without its track (ON_ROAD after off-tracking) is not compared");

        assertEquals(Optional.of("Access point Acceso km 110+176 is disabled: no vehicle on-tracks or off-tracks there"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, false, gross, axle,
                        access(false, 3L, "PREPARED_ACCESS", true, null, null), 3L));
        assertEquals(Optional.of("Access point Acceso km 110+176 is on track 3, not on track 4 of the location"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, false, gross, axle, prepared, 4L));
        FleetRules.AccessPointShape noRoadRail = access(true, 3L, "DEPOT", false, null, null);
        assertEquals(Optional.of("Access point Acceso km 110+176 does not admit road-rail vehicles: RR-07 cannot on-track or off-track there"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, false, gross, axle, noRoadRail, 3L));
        assertTrue(FleetRules.accessPointViolation("DR-02", VehicleMobility.RAIL_ONLY, false, gross, axle, noRoadRail, 3L).isEmpty(),
                "It says nothing of a rail-only vehicle");
        FleetRules.AccessPointShape crossing = access(true, 3L, "LEVEL_CROSSING", true, null, null);
        assertEquals(Optional.of("RR-07 needs a prepared access, and access point Acceso km 110+176 is a LEVEL_CROSSING"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, gross, axle, crossing, 3L));
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, false, gross, axle, crossing, 3L).isEmpty());
        assertEquals(Optional.of("RR-07 weighs up to 44 t, more than the 40 t that access point Acceso km 110+176 takes"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, new BigDecimal("44.00"), axle, prepared, 3L));
        assertEquals(Optional.of("RR-07 has an axle load of up to 12.75 t, more than the 12.5 t that access point Acceso km 110+176 takes"),
                FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, gross, new BigDecimal("12.75"), prepared, 3L));
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, new BigDecimal("40.000"), new BigDecimal("12.5"),
                prepared, 3L).isEmpty(), "Exactly the maximum fits");
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, null, null, prepared, 3L).isEmpty(),
                "A model without its weights is not compared");
        assertTrue(FleetRules.accessPointViolation("RR-07", VehicleMobility.ROAD_RAIL, true, new BigDecimal("99"), new BigDecimal("99"),
                access(true, 3L, "PREPARED_ACCESS", true, null, null), 3L).isEmpty(), "Nor an access point without its limits");
    }

    private static FleetRules.AccessPointShape access(boolean enabled, Long trackId, String kind, boolean roadRail, String maxAxleLoad,
                                                      String maxGrossWeight) {
        return new FleetRules.AccessPointShape("Acceso km 110+176", enabled, trackId, kind, roadRail,
                maxAxleLoad == null ? null : new BigDecimal(maxAxleLoad), maxGrossWeight == null ? null : new BigDecimal(maxGrossWeight));
    }

    private static FleetRules.TypeShape shape(VehicleMobility mobility, VehicleCategory category, boolean railGear,
                                              Set<VehicleCapability> capabilities) {
        return new FleetRules.TypeShape(mobility, category, railGear, railGear, false, false, false, capabilities,
                false, false, false, false);
    }

    // --------------------------------------------------------------------- caducidades

    @Test
    void somethingExpiresTheDayAfterItsExpiryDateAndIsWarnedAboutWithinTheWarningDays() {
        LocalDate today = LocalDate.parse("2026-10-17");
        assertEquals(0, ExpiryRules.daysLeft(today, today));
        assertEquals(-1, ExpiryRules.daysLeft(today.minusDays(1), today));
        assertFalse(ExpiryRules.isExpired(today, today), "It is still valid on its expiry date");
        assertTrue(ExpiryRules.isExpired(today.minusDays(1), today));
        assertFalse(ExpiryRules.isExpired(null, today), "Without an expiry it never expires");

        assertEquals(Optional.of(ExpiryRules.Stage.EXPIRING), ExpiryRules.stage(today, today, 30));
        assertEquals(Optional.of(ExpiryRules.Stage.EXPIRING), ExpiryRules.stage(today.plusDays(30), today, 30));
        assertEquals(Optional.empty(), ExpiryRules.stage(today.plusDays(31), today, 30));
        assertEquals(Optional.of(ExpiryRules.Stage.EXPIRED), ExpiryRules.stage(today.minusDays(1), today, 30));
        assertEquals(Optional.empty(), ExpiryRules.stage(null, today, 30));
    }

    @Test
    void anExpiryIsNeverBeforeTheIssueAndTheValidityOfTheTypeGivesTheMissingOne() {
        LocalDate issued = LocalDate.parse("2026-01-15");
        assertTrue(ExpiryRules.datesViolation("A qualification", issued, issued.minusDays(1)).isPresent());
        assertTrue(ExpiryRules.datesViolation("A qualification", issued, issued).isEmpty());
        assertTrue(ExpiryRules.datesViolation("A qualification", null, issued).isEmpty());
        assertEquals(Optional.of(LocalDate.parse("2029-01-15")), ExpiryRules.defaultExpiry(issued, 36));
        assertEquals(Optional.empty(), ExpiryRules.defaultExpiry(null, 36));
        assertEquals(Optional.empty(), ExpiryRules.defaultExpiry(issued, null));
    }

    @Test
    void aModelThatDoesNotSayItsDriverQualificationTakesTheOneOfItsMobility() {
        assertEquals(Optional.of("RAIL-DRIVER"), FleetRules.defaultDriverQualification(VehicleMobility.RAIL_ONLY, true, null));
        assertEquals(Optional.empty(), FleetRules.defaultDriverQualification(VehicleMobility.RAIL_ONLY, false, null), "A wagon is not driven");
        assertEquals(Optional.of("RR-OPERATOR"), FleetRules.defaultDriverQualification(VehicleMobility.ROAD_RAIL, true, DrivingLicence.C));
        assertEquals(Optional.of("LIC-B"), FleetRules.defaultDriverQualification(VehicleMobility.ROAD_ONLY, true, DrivingLicence.B));
        assertEquals(Optional.of("LIC-C"), FleetRules.defaultDriverQualification(VehicleMobility.ROAD_ONLY, true, DrivingLicence.C));
        assertEquals(Optional.of("LIC-CE"), FleetRules.defaultDriverQualification(VehicleMobility.ROAD_ONLY, true, DrivingLicence.C_E));
        assertEquals(Optional.empty(), FleetRules.defaultDriverQualification(VehicleMobility.ROAD_ONLY, true, null));
    }
}
