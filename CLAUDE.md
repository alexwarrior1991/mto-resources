# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MTO Resources: a Spring Boot 4.1 / Java 25 API for the **people, the organization and the fleet** of
the MTO domain (railway catenary), shared by `mto-maintenance` today and `mto-construction` tomorrow.
Every person has a record (with or without a Keycloak user) and a category (`WORKER`, `TEAM_LEADER`,
`SUPERVISOR`, `CHIEF_SUPERVISOR`, `CHIEF_MAINTENANCE`, `CHIEF_CONSTRUCTION`, `PROJECT_MANAGER`); the
organization is a tree (project → department → chief supervision → supervision → team) where **a team
works in one vehicle with one team leader, always, and 2 to 6 linemen**; the fleet is rail-only,
road-rail and road-only vehicles of a model, with where each one is at every moment.

Read `docs/` in order: `00-project-overview.md` … `07-auditing.md`. Those documents plus this file are
the source of truth; `03-database.md` documents the schema and should be checked before changing
persistence code.

⚠️ `mto-maintenance`, `mto-configuration` and the rest are **independent sibling repositories**. EP,
track and station ids of `mto-configuration` are plain `bigint`s here, without a foreign key. This
service **calls nobody**: what it needs from `mto-maintenance` (where the vehicles of a shift went)
arrives by RabbitMQ, and what `mto-maintenance` needs from here (teams, crews, vehicles) leaves by
RabbitMQ. No service account.

⚠️ The local infrastructure (PostgreSQL, RabbitMQ, Keycloak, the trace collector) is brought up by
`mto-platform`. `compose.yaml` here holds **only the application**.

Documentation in English; comments in the code in Spanish; commits in Spanish.

## Commands

```bash
./mvnw compile
./mvnw test                                   # Docker for Testcontainers, or TEST_DATABASE_* pointing at a PostgreSQL
./mvnw test -Dtest=BusinessLayerTest
./mvnw spring-boot:run                        # dev profile: port 8089, Swagger open
MESSAGING_EXAMPLES_WRITE=true ./mvnw test -Dtest=MessagingContractExamplesTest   # regenerate the contract examples
```

Local environment: `cd ../mto-platform && docker compose --profile all up -d && ./keycloak/apply-partials.sh`.
Profiles `dev` (default), `test`, `prod`. Port **8089** (`dev`); the container listens on 8080. Behind
the gateway the prefix is `/api/resources`. Flyway on startup, Hibernate `ddl-auto: validate`: every
schema change is a new migration, and one that changes an audited table changes its `_aud` twin.

## Architecture

Three layers under `com.alejandro.mtoresources`, the same split as `mto-maintenance`:

- `domain/model` — framework-free rules: `CrewComposition` (`MIN_LINEMEN = 2`, `MAX_LINEMEN = 6`;
  `mto-maintenance` keeps a copy for the crew of a shift), `OrganizationRules`, `FleetRules`. They return
  the reason instead of throwing: the caller decides 400 or 409.
- `application` — `dto` (one package per resource), `service` + `service/impl` (package-private impls;
  `OrganizationLookups` loads with its 404, walks the tree, locks teams in id order and republishes
  them; `FleetResponses` builds the fleet answers; **`ResourcesEvents` is the event contract**),
  `mapper` (MapStruct, entity → response only; `OrganizationUnitResponse` and the fleet answers are
  built by hand because they carry what is computed), `exception`.
- `infrastructure` — `persistence` (entities, repositories, specifications, Envers), `web` (controllers,
  merge-patch, `PreferHeader`/`OperationResponses`, the exception handler, `actuator/RepublishEndpoint`),
  `messaging/rabbitmq` (`MaintenanceEventConsumer`), `messaging/outbox` (copy of `mto-configuration`'s).
- `configuration` — security, `rabbitmq`, `messaging` (signature), `outbox`, `async` (virtual threads,
  `OperationsProperties`, the scheduler of the background operations), JPA auditing, OpenAPI, `Clock`.

### Rules that must not be broken

- **A team is born complete and never leaves the crew rule**: created with its `headPersonId` and 2 to 6
  `memberPersonIds` (400 otherwise); its leader is replaced, never emptied; `POST /members` with 6 and
  `leave` with 2 are 409 `UNI-001`; several change at once through `PUT /members`. The counts are made
  with the team row locked. A lineman is a `WORKER` or a `TEAM_LEADER`, never the leader of the same
  team; a person is an open lineman of one team and leads one active team at most (partial unique
  indexes) and is never both. A deactivated team releases its linemen and comes back with new ones.
- **Every transaction that publishes a `team.updated` locks those team rows in id order first**
  (`OrganizationLookups.lockTeams`), before building the view; the same for the vehicles of a model and
  for what `republish` publishes. The consumers' watermark is the outbox `sequenceNumber`, so its order
  must be the order of the commits. What republishes what is in `docs/06-messaging.md`.
- **`vehicle_position` moves only through `VehiclePositionRepository.moveTo`**, the native upsert with
  the `observed_at` watermark inside the statement; never read-then-write. `vehicle_location_event` is
  append-only.
- **Where a vehicle can be is decided by its mobility** (`FleetRules`): a rail-only vehicle is never on
  the road and is parked on a track; a road-only one is never on the track; an attached one is towed by
  one with `TOWING` on the same way or carried by a road vehicle (or anything, if portable), and is read
  where its carrier is.
- **A model is coherent with its mobility** (`FleetRules.typeViolation`); a vehicle carries what its
  model needs (plate if it runs on the road, rail number if it may run on open track).
- **Permission ≠ position.** Keycloak profiles say what someone may do; the tree says whom they lead.
  This service never writes the realm. The position profiles (`mto-position-*`) live in
  `mto-platform/keycloak/mto-positions-cross-service.json`.
- **Background work holds no connection while it waits** and is capped for the whole deployment in
  the database (`BudgetedOperations`); an API route that adopts it does so through
  `Prefer: respond-async` only, so the existing clients stay synchronous.
- `@Transactional(noRollbackFor = BusinessException.class)` on `VehicleLocationService.record`: its rules
  are checked before writing anything, so a refusal does not doom the inbox transaction of the shift
  consumer, which records `UNKNOWN` with the reason instead.
- An enum literal in JPQL against a `NAMED_ENUM` column is cast to a type that does not exist: pass it
  as a parameter (see `OrganizationUnitRepository.findTeamIds`).

### Security

Resource server of the `mto` realm, audience `mto-resources-api`. Client roles `resources-read`,
`resources-write` (people, units, linemen, parking sites), `resources-fleet-write` (vehicles and
models), `resources-location-write` (only `POST /vehicles/{id}/locations`), `resources-delete`
(`DELETE`, and `PUT`/`PATCH` with `active=false` through `@PreAuthorize`), `ops-metrics`, `ops-write`;
realm roles only as `ROLE_REALM_*`. No role implies another; anything undeclared under the API is
denied. A new role goes to `SecurityRoles` **and** `keycloak/mto-resources-partial-import.json`.

### Messaging

Publishes `person`, `team` and `vehicle` (`created`/`updated`/`deactivated`) on `mto.resources.exchange`
through the outbox, inside the business transaction; declares no queue (`mto-maintenance` and
`mto-notification` declare theirs). What `republish` publishes carries `republished: true`, and a vehicle
event says whether that change moved its availability (`availabilityChanged`): `mto-notification` notices
people of changes, not of republications nor of every note. Consumes
`mto.maintenance.shift.*` on its own queue `mto.resources.maintenance.queue` (DLX/DLQ) through the
idempotent inbox, and the positions of the field devices (`mto.field.position.*` on
`mto.field.telemetry.exchange`, queue `mto.resources.field.queue`, `FieldPositionHandler`): `ON_TRACK` with
`source = FIELD` and the reference of the shift, ignored once that shift left the track. **Keys are only added**; a new key or event changes its example in
`docs/messaging/examples/` in the same commit. The other way round, the shift examples of
`mto-maintenance` are copied in `src/test/resources/contracts/mto-maintenance/` and `BusinessLayerTest`
runs them through the real handler: a change of those examples is copied here in the same change.

### Testing

One class per layer; add methods, not classes. `BusinessLayerTest` runs the **services against a real
PostgreSQL** with the whole context and a `@Primary` `RecordingEventPublisher` — on purpose, unlike
`mto-maintenance`: the rules here are rules of rows (locks, partial indexes, the position watermark),
which a mocked repository would accept without testing; every case creates its own data with random
codes because the database is shared. `DomainModelTest`, `ApiAuthorizationRulesTest` (probe controllers
on the real routes, and the `@PreAuthorize` of every real `PUT`/`PATCH` whose body can say
`active=false`), `SecurityLayerTest`, `MessagingLayerTest` (the consumer), `MessagingContractExamplesTest`,
`JpaEntityModelTest`, `MtoResourcesApplicationTests` (the whole context with Tomcat, and that requests
and background work run on virtual threads) and the outbox's own tests under
`infrastructure/messaging/outbox`. `support/PostgreSQLTestContainer` starts `postgres:17-alpine`, or uses
`TEST_DATABASE_URL`/`TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD`; without either the test is skipped.
