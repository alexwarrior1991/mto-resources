# 03 — Database

PostgreSQL 17 (16+ works), database `mto_resources`. Flyway runs `src/main/resources/db/migration` at
startup and Hibernate only validates (`ddl-auto: validate`), so every schema change is a new
migration. A change to an audited table changes its `_aud` twin in the same migration (nullable, no
constraints).

Conventions: UUID ids (`gen_random_uuid()`), `created_at`/`updated_at`/`created_by`/`updated_by` on
every edited table, `version` on every table edited through the API (optimistic lock, 409 `CON-001`),
PostgreSQL enums for every status and kind, `numeric(12,3)` for a kp, `numeric(9,6)` for latitude and
longitude, `timestamptz` for instants. Ids of `mto-configuration` (EP, station, track) are `bigint`
columns without a foreign key.

| Migration | What |
|---|---|
| `V1` | `inbox_message` (the shifts consumed from `mto-maintenance`), unique `(message_id, source_service)` |
| `V2` | `audit_revision_seq` and `audit_revision` (Envers, with `source` and `correlation_id`) |
| `V3` | `outbox_message` (the own events), the copy of `mto-configuration`'s outbox |
| `V4` | `background_operation` |
| `V5` | `person`, `organization_unit`, `organization_unit_execution_package`, `team_member` and their `_aud` |
| `V6` | `vehicle_type` (+ `vehicle_type_capability`), `parking_site`, `vehicle` (+ `_aud`), `vehicle_location_event`, `vehicle_position` |
| `V7` | The seed: project `MTO` and department `MNT`, with a `BASELINE` revision |

## People and organization (`V5`)

| Table | Key columns | Constraints and indexes |
|---|---|---|
| `person` | `code` (`PER-%06d` from `person_code_seq`), `employee_number`, `first_name`, `last_name`, `username`, `keycloak_user_id`, `category`, `employer`, `company`, `phone`, `active`, `version` | `code` unique; partial unique indexes on `employee_number`, `lower(username)` and `keycloak_user_id`; a subcontractor has a company |
| `organization_unit` | `code` (upper case, 16 chars), `name`, `kind`, `discipline`, `parent_id`, `head_person_id`, `default_vehicle_id`, `active`, `version` | `code` unique and upper; only a project has no parent and no discipline; a team has a head; only a team has a default vehicle; **one active team per head** (`uq_organization_unit_active_team_head`, partial) |
| `organization_unit_execution_package` | `unit_id`, `execution_package_id` | the EPs of a unit (an `@ElementCollection`, audited with its `_aud`) |
| `team_member` | `team_id`, `person_id`, `joined_on`, `left_on` | `left_on >= joined_on`; **one open team per person** (`uq_team_member_open_person`, partial on `left_on IS NULL`) |

Enums: `person_category` (`WORKER`, `TEAM_LEADER`, `SUPERVISOR`, `CHIEF_SUPERVISOR`,
`CHIEF_MAINTENANCE`, `CHIEF_CONSTRUCTION`, `PROJECT_MANAGER`), `person_employer`,
`organization_unit_kind`, `work_discipline`.

The crew limits (2 to 6 linemen) are counted by the service with the team row locked
(`SELECT ... FOR UPDATE`), not by a constraint: a team is allowed to be below 2 for the instant of a
`PUT /members` inside its transaction.

## Fleet (`V6`)

| Table | Key columns | Constraints and indexes |
|---|---|---|
| `vehicle_type` | `code`, `name`, `manufacturer`, `model`, `mobility`, `category`, `self_propelled`, `portable`, dimensions and weights, `seats`, `power`, track figures, `transport_mode_authorised`, `rail_gear_type`, `on_tracking_minutes`, `needs_prepared_access`, `max_speed_road_kmh`, `required_licence`, capability figures, `active`, `version` | only `ROAD_RAIL` has rail gear; `ROAD_ONLY` has no gauge and never transport mode; `RAIL_ONLY` has no road figures |
| `vehicle_type_capability` | `type_id`, `capability` | primary key on both |
| `parking_site` | `code`, `name`, `kind`, `execution_package_id`, `station_id`, `track_id`, `kp`, `rail_access`, `road_access`, `address`, `latitude`, `longitude`, `active` | rail or road access or both; rail access has a track; coordinates travel together and are in range |
| `vehicle` | `code`, `name`, `type_id`, `registration_plate`, `rail_vehicle_number`, `serial_number`, `manufacture_year`, `owner`, `owner_company`, `operational_status`, `status_reason`, `odometer_km`, `engine_hours`, `active`, `version` | `code` unique; partial unique indexes on `upper(registration_plate)` and `upper(rail_vehicle_number)`; an owner other than `OWN` has a company |
| `vehicle_location_event` | `vehicle_id`, `kind`, `parking_site_id`, `execution_package_id`, `track_id`, `kp`, `latitude`, `longitude`, `access_point`, `description`, `carrier_vehicle_id`, `attachment`, `source`, `source_reference`, `observed_at`, `recorded_at`, `recorded_by` | `ON_TRACK` has a track; `PARKED` says where; `ATTACHED` if and only if carrier and attachment; never attached to itself; index `(vehicle_id, observed_at DESC)` and on `source_reference` |
| `vehicle_position` | `vehicle_id` (primary key), `location_event_id` and the columns the board filters on | indexes on EP, track and carrier |

Enums: `vehicle_mobility`, `vehicle_category` (19 values), `vehicle_power`, `rail_gear_type`,
`driving_licence`, `vehicle_capability`, `vehicle_owner`, `vehicle_operational_status`,
`parking_site_kind`, `vehicle_location_kind`, `vehicle_attachment`, `vehicle_location_source`.

`vehicle_position` is written only by `VehiclePositionRepository.moveTo`:
`INSERT ... SELECT ... FROM vehicle_location_event ... ON CONFLICT (vehicle_id) DO UPDATE ... WHERE
vehicle_position.observed_at < excluded.observed_at`. The watermark is inside the statement, never
read-then-write.

## Background operations (`V4`)

`background_operation`: `id`, `kind`, `scope`, `status` (`QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`),
`requested_by`, `correlation_id`, `request_method`, `request_uri`, `result_status`, `result` (`json`),
`error_status`, `error_code`, `error_message`, `created_at`, `started_at`, `finished_at`,
`heartbeat_at`. Finished if and only if `SUCCEEDED`/`FAILED`; a `FAILED` one has its error code.
Written only with native SQL (`BackgroundOperationRepository`): the insert counts the live ones in its
own `WHERE` under an advisory lock, and the start takes its turn by `(created_at, id)` within its scope.
Not audited: it is its own history, and the purge deletes the finished ones after
`app.operations.retention`.
