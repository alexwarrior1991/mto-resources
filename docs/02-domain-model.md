# 02 — Domain model

## People

A `Person` is a record: code (`PER-000001`, from a sequence), names, optional employee number,
optional `username` (their `preferred_username` in Keycloak, stored lowercase) and `keycloakUserId`,
`category`, `employer` (`OWN` or `SUBCONTRACTOR`, then with its `company`), phone and `active`. The
phone never leaves this service. A person is never deleted: deactivating one who leads a unit is 409
`UNI-001` (give the unit another head first); a lineman leaves their team that day, unless the team
would drop below 2.

Category ranks: `WORKER` 1, `TEAM_LEADER` 2, `SUPERVISOR` 3, `CHIEF_SUPERVISOR` 4, `CHIEF_MAINTENANCE`
and `CHIEF_CONSTRUCTION` 5, `PROJECT_MANAGER` 6.

## The organization tree

`OrganizationUnit`: `PROJECT` (level 1) → `DEPARTMENT` (2) → `CHIEF_SUPERVISION` (3) → `SUPERVISION`
(4) → `TEAM` (5).

- A unit hangs from one of a **strictly higher** level (levels may be skipped); a project is the root.
- The **discipline** (`MAINTENANCE`, `CONSTRUCTION`) is said by the department and inherited below; a
  unit never moves to another discipline. A project has none.
- The **head** has the category of the level or above (a team, a team leader or above; a supervision,
  a supervisor or above...). A department is led by the chief of its discipline or the project manager.
  Every level but a team may be vacant.
- A unit has its EPs (`mto-configuration` ids). A team may have a **default vehicle**. No team has a
  fixed base: vehicles are parked wherever they can be.
- A unit with active units under it is not deactivated. Changing the head or the parent of a unit
  republishes every team under it (their event carries their supervisor and chief supervisor).

The project `MTO` and the department `MNT` are seeded (with a `BASELINE` revision); teams are not,
because a team cannot exist without its leader and linemen. When teams `A` and `B` are created here
with their EPs, `mto-maintenance` adopts its own teams by code.

### The crew rule (`CrewComposition`)

**One team leader, always, and 2 to 6 linemen** — 3 to 7 people in the vehicle.

| Operation | Rule |
|---|---|
| Create a team | `headPersonId` and 2 to 6 `memberPersonIds` in the same request; 400 otherwise |
| Change the leader | Replaced (`PATCH headPersonId`), never emptied (400) |
| `POST /units/{id}/members` | 409 `UNI-001` with 6 |
| `POST /units/{id}/members/{personId}/leave` | 409 `UNI-001` with 2 |
| `PUT /units/{id}/members` | The whole list, 2 to 6, in one go: how several change without crossing the limits |
| Deactivate a team | Its linemen leave that day; reactivating it needs its linemen again (`active=true` + `memberPersonIds`) |

A lineman is a `WORKER` or a `TEAM_LEADER` (a leader may go as lineman with another), never the leader
of the same team. A person is an open lineman of one team at most (partial unique index), leads one
active team at most (partial unique index), and is never both. A membership is never deleted: it is
closed with its `leftOn`, and the history of a team is its rows.

## The fleet

Three families, each with its rules and its way of reaching the work site: **rail-only** (catenary tower cars / MTW, wiring trains, track maintenance cars, inspection
cars, locomotives, wagons and trolleys — EN 14033 / EN 13977), **road-rail** (MEWPs, trucks,
excavators and tractors with rail gear — EN 15746; they on-track at an access point and off-track
after the work) and **road-only** (vans, pick-ups, crew buses, crane trucks, low loaders).

`VehicleType` is the **model**: mobility (`RAIL_ONLY`, `ROAD_RAIL`, `ROAD_ONLY`) and a category of that
mobility; self-propelled or not, portable or not; dimensions and weights, seats, power; on track:
gauge, working and transport speeds, maximum gradient, minimum radius, working cant and whether it may
run on open track in transport mode; road-rail: rail gear type (`RAIL_WHEEL_DRIVE` 9A,
`FRICTION_DRIVE` 9B, `ROAD_WHEEL_DRIVE` 9C), on-tracking minutes and whether it needs a prepared
access; road: speed and driving licence (`B`, `C`, `C_E`); capabilities (`CRANE`,
`ELEVATING_PLATFORM`, `INSULATED_PLATFORM`, `ROTATING_PLATFORM`, `PANTOGRAPH`, `CONTACT_WIRE_HOLDER`,
`WIRE_DRUM`, `OCS_MEASURING`, `TRACK_MEASURING`, `EARTHING`, `TOWING`, `CREW_CABIN`, `WORKSHOP`) with
their figures. `FleetRules.typeViolation` keeps a model coherent (400 otherwise).

`Vehicle` is the **unit**: fleet code, model, registration plate (required if it runs on the road,
absent if rail-only), rail vehicle number (required if its model may run on open track, absent if
road-only), serial, year, owner, operational status (`OPERATIONAL`, `LIMITED`, `UNDER_REPAIR`,
`OUT_OF_SERVICE`, with a reason unless operational), odometer and engine hours. **Available** means
active and `OPERATIONAL` or `LIMITED`. The default vehicle of an active team is not retired.

## Where each vehicle is

`vehicle_location_event` is the append-only history; `vehicle_position` is the current position, one
row per vehicle, moved by a native upsert whose watermark (`observed_at`) is inside the statement:
what is observed earlier stays in the history and does not move the position.

| Kind | Meaning | Rules |
|---|---|---|
| `PARKED` | Left somewhere: a `parking_site`, a track and kp, a description or coordinates | Rail-only: always on a track (a track, or a site with rail access). Road-only: off it (a site with road access, a description or coordinates). |
| `ON_TRACK` | On its track (and kp) | Never a road-only vehicle |
| `ON_ROAD` | On the road | Never a rail-only vehicle |
| `IN_WORKSHOP` | In a workshop | — |
| `ATTACHED` | Towed or carried by another (`carrier` + `TOWED`/`CARRIED`) | Towed by one with `TOWING` that runs on the same way; carried by a road vehicle, or anything if portable. The carrier is not attached itself; a carrier is not attached to a third one. It is read where its carrier is. |
| `UNKNOWN` | Not known | — |

Sources: `MANUAL`, `SHIFT` (derived from `mto-maintenance`), `FIELD` and `TELEMATICS` (later). A
retired vehicle does not move; `observedAt` is never in the future (one minute of clock skew).

### Derived from the shifts

- `shift.started`: rail-only and road-rail vehicles go `ON_TRACK` on the first track of the shift at its
  `startKp` (on-tracking, for a road-rail one); road-only ones go `ON_ROAD` in its EP.
- `shift.closed`, and `shift.cancelled` of a shift that had started: `PARKED` where the shift says each
  was left (`vehicles[].parking`), else at its `parkingPlace` (and, if rail-only, on its first track),
  else `UNKNOWN`. Only vehicles whose position is still the one that shift left: one moved by hand
  since stays. Whatever is attached to them follows on its own.
- What a fleet rule refuses is recorded as `UNKNOWN` with the reason instead of failing the message;
  the fine detail (where it on-tracked, the low loader) is corrected by hand.
- If the field device of the shift reported after the `actualEnd` written at closing, the close is
  recorded one millisecond after its last position: otherwise the watermark would leave it on the track.

### Derived from the field devices

`mto.field.position.reported` (`mto-field`): the device of a shift says the kp of its vehicle. It goes
`ON_TRACK` on that track and kp with `source = FIELD` and the same reference as the shift
(`mto-maintenance:shift:<id>`), so that the close of the shift still parks it. A late one stays in the
history and moves nothing (the watermark). What cannot apply is logged and ignored, never sent to the DLQ:
a vehicle not in the fleet or retired, a position without a track or a shift, what `FleetRules` refuses
(a road vehicle is never on the track), and one of a shift that already left the track (its vehicle is
`PARKED` or `UNKNOWN` by that same shift): `mto-field` stops publishing at the clear-of-track, and what
arrives afterwards cannot put the vehicle back on the track.
