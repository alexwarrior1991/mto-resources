# 05 — Development roadmap

## Done (this round)

- People, the organization tree and the crew rule (1 leader + 2..6 linemen), with `GET /me`.
- The fleet: models (rail-only, road-rail, road-only, with their characteristics), vehicles, the
  optional catalogue of parking places, the location history and the current position with its
  watermark, towing and carrying, the fleet board.
- Own events (`person`, `team`, `vehicle`) through the outbox, and `republish` for a new consumer.
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

## Next

- **Screens** in `mto-backoffice` and `mto-frontend`, at parity (`recursos/*`).
- **Stricter crew requirements per shift** beyond «nothing expired and a licensed driver per
  self-propelled vehicle» (a minimum of qualified people per kind of work), if the operation asks for them.
- **Track access points** for road-rail vehicles, as master data of `mto-configuration`; until then
  `accessPoint` is text.
- **Gradient and rail gear**: a 9B without upgrades should not go to gradients of 1/75 or more; it needs
  the gradient of the track, which `mto-configuration` does not keep yet.
- **Positions from the field devices** (`FIELD`, through an exchange of its own) and **GPS**
  (`TELEMATICS`, with its service account); `vehicle-position` events once someone consumes them.
- **Availability** of people (holidays, leave, minimum rest between nights) and **reservations** of
  people and vehicles shared with `mto-construction`, with an `Idempotency-Key` like the stock.
- **Scope-by-EP permissions**, from `GET /me`.
