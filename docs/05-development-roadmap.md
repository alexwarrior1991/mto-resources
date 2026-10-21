# 05 — Development roadmap

## Done

- People, the organization tree and the crew rule (1 leader + 2..6 linemen), with `GET /me`.
- The fleet: models (rail-only, road-rail, road-only, with their characteristics), vehicles, the
  optional catalogue of parking places, the location history and the current position with its
  watermark, towing and carrying, the fleet board.
- Own events (`person`, `team`, `vehicle`, `instrument`) through the outbox, and `republish` for a new
  consumer. What `republish` publishes says `republished: true`, and a vehicle event says whether that
  change moved its availability (`availabilityChanged`), so that `mto-notification` warns about changes
  and not about republications.
- The consumer of the shifts of `mto-maintenance`, which moves the vehicles.
- Virtual threads and background operations with a time budget (`Prefer: respond-async`).
- Keycloak: the client `mto-resources-api`, its permissions and the functional profiles; the position
  profiles (`mto-position-*`) live in `mto-platform`.
- **Qualifications and expiries**: the catalogue of qualification types and the qualifications of every
  person (dates only), the documents of the vehicles, the calibrated instruments, the qualification the
  driver of each model needs, all of them in the events (`person.*` with its `qualifications`, `vehicle.*`
  with its `documents` and `type.requiredDriverQualification`, `instrument.*`), and the daily expiry
  watcher (`qualification.*`, `vehicle-document.*`, `calibration.*`). `mto-maintenance` keeps what is
  expired out of a shift and asks for a licensed driver per self-propelled vehicle.
- The positions of the field devices of `mto-field` (`FIELD`), through its telemetry exchange.
- **Track access points**: the master data of `mto-configuration` (`V28` there) kept as a snapshot
  (`track_access_point`, `V9`) from its master-data channel, offered by `GET /track-access-points`, and the
  rules of the fleet when a location says its `accessPointId` (enabled, on its track, admitting road-rail
  vehicles, prepared if the model needs it, strong enough for its weight).
- What does not move by itself (a wagon, a trailer) has no driver qualification by default, in any
  mobility (`V10` emptied what the default had given them).
- **Availability** (`V11`, `V12`): the absences of every person (`person_absence`, two of one person never
  overlap, `ABS-001`) and the repairs that immobilise a vehicle, planned or open, travel in every
  `person.*` and `vehicle.*` as `unavailable [{from, to}]`, never with their reason. `mto-maintenance`
  keeps them out of the shifts of those dates, together with the minimum rest between two nights.
- **Workshop** (`V12`): the repairs of each vehicle (`PLANNED → OPEN → CLOSED`, or `CANCELLED`, `REP-001`),
  which rule its status: opening one that immobilises puts it `UNDER_REPAIR`, closing or cancelling the
  last one puts it back `OPERATIONAL`.
- **GPS** (`V12`): the `telematicsDeviceId` of a vehicle and `POST /telematics/positions`, for the gateway's
  service account `mto-telematics-svc` (`resources-telematics-write`): `ON_ROAD` with `source = TELEMATICS`,
  at most one every `app.resources.telematics.min-interval` (5 min) per device, ignoring with its reason
  an unknown device and a retired, rail-only or attached vehicle, or one on track in a shift.
- **Position profiles against categories**: `GET /reports/position-discrepancies` reads Keycloak with the
  read-only service account `mto-resources-svc` (`KCK-503` when it does not answer). It is the only call
  this service makes.
- **Screens** in `mto-backoffice` and `mto-frontend`, at parity (`recursos/*`): people with their
  qualifications and absences, the organization tree, the fleet with its documents, workshop and GPS, the
  board, the instruments and the discrepancy report.
- **Scope by execution package**: `mto-maintenance` limits who writes there with the record of the person
  and the teams they belong to, which arrive from here; nothing changed in this service.

## Next

- **Stricter crew requirements per shift** beyond «nothing expired and a licensed driver per
  self-propelled vehicle» (a minimum of qualified people per kind of work), if the operation asks for them.
- **A real GPS gateway**: the service account and the endpoint exist, but nothing sends positions yet.
- `vehicle-position` events once someone consumes them.
- **`mto-construction`**:
  - its position profile `mto-position-chief-construction` (today `CHIEF_CONSTRUCTION` has none, and the
    discrepancy report leaves it out);
  - its assignments reaching `mto-maintenance` as `unavailable` periods, so that nobody works a night of
    maintenance and a construction site at once. Shared reservations with an `Idempotency-Key`, like the
    stock, only if periods are not enough.

## Not planned

Decided against, written here so that nobody rediscovers them as missing:

- **Gradient and rail gear**: the rule that a 9B without upgrades does not go to gradients of 1/75 or
  more, and the gradient master of the track it would need in `mto-configuration`.
- **Moving a vehicle on open track** with a path or a possession of its own.
- **Central reservations** of people and vehicles instead of availability through events.
