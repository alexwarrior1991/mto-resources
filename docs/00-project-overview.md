# 00 — Project overview

`mto-resources` is the service of the **people, the organization and the fleet** of the MTO domain
(railway catenary). It answers three questions every other service needed and none could answer:

1. **Who** works: every person has a record (`PER-000001`), with or without a Keycloak user, and a
   category: `WORKER` (lineman), `TEAM_LEADER` (foreman), `SUPERVISOR`, `CHIEF_SUPERVISOR`,
   `CHIEF_MAINTENANCE`, `CHIEF_CONSTRUCTION` and `PROJECT_MANAGER`.
2. **Who leads whom**: an organization tree — project → department (maintenance, construction) →
   chief supervision → supervision → team. A team works in one vehicle during a shift: **one team
   leader, always, and 2 to 6 linemen** (3 to 7 people). A supervisor runs several teams, normally in
   the same execution package (EP); a chief supervisor runs the supervisors.
3. **With what and where**: the rail, road-rail and road vehicles, their models and characteristics,
   and where each one is at every moment.

## Why a service of its own

These are resources **shared** by maintenance today and construction tomorrow (`mto-construction`),
like the material is shared through `mto-stock`.

| Candidate | Why not |
|---|---|
| `mto-maintenance` | `mto-construction` would have to ask a consumer for its teams and vehicles. |
| `mto-configuration` | It is infrastructure; its channel goes to stock, maintenance and notification, and a location changes every night. |
| `mto-users` | It has no database (Keycloak is its truth) and rejects groups on purpose. |
| `mto-field` | It is the gRPC console of one night and keeps no master data. |

## What it is not

- **Not a permission system.** What someone may do is their Keycloak profile; whom they lead is the
  organization tree. They are kept apart on purpose: a deputy may need the profile without changing
  position, and this service does not write the realm. `GET /me` (the units and EPs of the token's
  person) is the hook for the scope-by-EP permissions of a later phase.
- **Not GPS.** A location is recorded by hand, derived from the shifts of `mto-maintenance`, and later
  sent by the field devices (`FIELD`) and telematics (`TELEMATICS`); the source is already in the model.
- **Not a caller.** It calls nobody: state travels by events in both directions. No service account.

## Neighbours

| Service | Relation |
|---|---|
| `mto-maintenance` | Consumes `mto.resources.{person,team,vehicle}.*` (its teams, crews and vehicles); publishes `mto.maintenance.shift.*`, which moves the vehicles here. |
| `mto-configuration` | EP, track and station ids are stored as plain `bigint`s, without a foreign key. |
| `mto-gateway` | `/api/resources/** → /api/v1/resources/**`. |
| `mto-platform` | Brings up PostgreSQL (`mto_resources`), RabbitMQ and Keycloak; applies `keycloak/`. |
