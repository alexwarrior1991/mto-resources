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

## Next

- **Screens** in `mto-backoffice` and `mto-frontend`, at parity (`recursos/*`).
- **Qualifications and expiries** of the staff (rail vehicle driver, heavy licence, work near live
  equipment, crane, MEWP, first aid, medical fitness — dates only, no diagnosis), the **documents of
  the vehicles** (road and rail inspections, insurance, crane and platform certificates) and the
  **calibrated instruments** (earthing poles, voltage detectors); an expired one does not enter a shift.
- **The licensed driver** of each model (rail vehicles driver, road-rail operator, `C`/`C+E`).
- **Track access points** for road-rail vehicles, as master data of `mto-configuration`; until then
  `accessPoint` is text.
- **Gradient and rail gear**: a 9B without upgrades should not go to gradients of 1/75 or more; it needs
  the gradient of the track, which `mto-configuration` does not keep yet.
- **Positions from the field devices** (`FIELD`, through an exchange of its own) and **GPS**
  (`TELEMATICS`, with its service account); `vehicle-position` events once someone consumes them.
- **Availability** of people (holidays, leave, minimum rest between nights) and **reservations** of
  people and vehicles shared with `mto-construction`, with an `Idempotency-Key` like the stock.
- **Scope-by-EP permissions**, from `GET /me`.
