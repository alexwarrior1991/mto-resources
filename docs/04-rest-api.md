# 04 — REST API

Base path `/api/v1/resources` (behind the gateway, `/api/resources`). JSON, `PageResponse<T>`
(`{content, page{number, size, totalElements, totalPages, first, last}}`), `ApiErrorResponse` with a
stable `errorCode` and the request's `correlationId` — the same shapes as `mto-maintenance`.

## Permissions

| Route | Role |
|---|---|
| `GET`/`HEAD` of everything (`/track-access-points` too) | `resources-read` |
| `POST /vehicles/{id}/locations` | `resources-location-write` (its matcher goes first) |
| `POST /telematics/positions` | `resources-telematics-write`, of the service account `mto-telematics-svc`: no profile carries it, and it opens nothing else |
| Writes of `/vehicles/**` (their documents and repairs too), `/vehicle-types/**`, `/instruments/**` | `resources-fleet-write` |
| Writes of `/people/**` (their qualifications and absences too), `/units/**`, `/parking-sites/**`, `/qualification-types/**` | `resources-write` |
| `DELETE` of anything, and `PUT`/`PATCH` with `active=false` (`@PreAuthorize`) | `resources-delete` |
| `/actuator/**` | `ops-metrics` to read, `ops-write` for `POST`/`DELETE` |
| Anything else under the API | denied: a new resource declares its permission or does not exist. A write to `/track-access-points` is one of them: the master data is `mto-configuration`'s |

No role implies another (`ApiAuthorizationRulesTest`).

## Resources

| Route | What |
|---|---|
| `GET/POST /people`, `GET/PUT/PATCH/DELETE /people/{id}`, `GET /people/{id}/revisions` | People. Search by `search` (code, name, username, employee number), `category` (repeatable: any of them, as the crew pickers ask for `WORKER` and `TEAM_LEADER` at once), `unitId` (its head and open linemen), `active` |
| `GET/POST /qualification-types`, `GET/PUT/PATCH/DELETE /qualification-types/{id}` | The catalogue of qualification types. Search by `search` (code, name), `kind`, `active`. `drivingLicence` if and only if `kind` is `DRIVING_LICENCE` (another kind drops it); the code never changes; `DELETE` retires it. Changing its kind or licence republishes the people who hold it |
| `GET/POST /people/{personId}/qualifications`, `PATCH/DELETE /people/{personId}/qualifications/{qualificationId}` | The qualifications of a person, the whole list by type code (`expired`: its expiry is before today). One per type of an active type (409 `QUA-001`); without `expiresOn`, `issuedOn` plus the `validityMonths` of the type; `PATCH` (merge-patch only) renews it or empties anything; `DELETE` is a physical delete. Every change publishes `person.updated` |
| `GET/POST /people/{personId}/absences`, `PATCH/DELETE /people/{personId}/absences/{absenceId}` | The absences of a person, the most recent first: `kind`, `startsOn`, `endsOn` (both included), `notes`. Two that overlap are 409 `ABS-001`; `PATCH` (merge-patch only) empties only the notes; `DELETE` is a physical delete. Every change publishes `person.updated` with `unavailable` (the periods, never the kind) |
| `GET /me` | The person of the token, their units (`HEAD` or `LINEMAN`) and the EPs of those units and of everything under them. 404 `PER-404` when no record has the username |
| `GET/POST /units`, `GET /units/tree`, `GET/PUT/PATCH/DELETE /units/{id}`, `GET /units/{id}/revisions` | The organization tree. A team is created complete (`headPersonId` + 2 to 6 `memberPersonIds`) |
| `GET/PUT/POST /units/{id}/members`, `POST /units/{id}/members/{personId}/leave` | The linemen of a team: read (`includeFormer`), replace all at once, add one, one leaves |
| `GET/POST /parking-sites`, `GET/PUT/PATCH/DELETE /parking-sites/{id}` | The optional catalogue of known parking places |
| `GET/POST /vehicle-types`, `GET/PUT/PATCH/DELETE /vehicle-types/{id}` | Vehicle models. Changing one republishes its vehicles. `requiredDriverQualificationTypeId` (emptied with merge-patch; on create, without it, the one of its mobility) comes back as `requiredDriverQualification {id, code, name, kind}` |
| `GET/POST /vehicles`, `GET/PUT/PATCH/DELETE /vehicles/{id}`, `GET /vehicles/{id}/revisions` | The fleet; the answer carries the current `position`. Search by `search`, `typeId`, `mobility`, `category`, `status`, `available`, `active` |
| `GET/POST /vehicles/{vehicleId}/documents`, `PATCH/DELETE /vehicles/{vehicleId}/documents/{documentId}` | The documents of a vehicle, the whole list by kind (`expired`: its expiry is before today). One per kind (409 `VDO-409`), always with `expiresOn` (never emptied); `PATCH` (merge-patch only) renews it; `DELETE` is a physical delete. Every change publishes `vehicle.updated` with `availabilityChanged=false` |
| `GET/POST /vehicles/{vehicleId}/repairs`, `PATCH /vehicles/{vehicleId}/repairs/{repairId}`, `POST .../{repairId}/open`, `/close`, `/cancel` | The workshop of a vehicle, the most recent first. Born `PLANNED` (with `plannedFrom`) or `OPEN`; one that immobilises (the default) puts the vehicle `UNDER_REPAIR` when opened and back `OPERATIONAL` when the last one closes or is cancelled. A transition it does not admit, or changing what its status no longer lets change, is 409 `REP-001`. Every change publishes `vehicle.updated` with `unavailable`. `telematicsDeviceId` of a vehicle (`POST`/`PATCH /vehicles`) is its GPS device, unique (409 `VEH-409`) |
| `POST /telematics/positions` | The positions of the GPS devices (`{positions: [{deviceId, observedAt, latitude, longitude}]}`, up to 500): each one `ON_ROAD` with `source = TELEMATICS`, at most one every `app.resources.telematics.min-interval` per device, or ignored with its reason (`docs/02-domain-model.md`). Always 200 with `{recorded, ignored, results[{deviceId, observedAt, vehicleCode, outcome, reason}]}` |
| `GET /reports/position-discrepancies` | The position profile of Keycloak against the category of each person: `wrongProfile`, `severalProfiles`, `withoutProfile`, `withoutRecord`, each `{personId, personCode, fullName, username, category, personActive, expectedProfile, profiles}`. 503 `KCK-503` if Keycloak cannot be read |
| `GET/POST /vehicles/{id}/locations` | The location history (`from`, `to`) and recording one by hand. A location may say where it on-tracked or off-tracked with `accessPointId` (one of `GET /track-access-points`; 404 `TAP-404` if unknown), checked by the rules of the access point (409 `VEH-001`, `docs/02-domain-model.md`), and with the free text `accessPoint` as before. Every location answer (the history, the `position` of a vehicle, the board) keeps `accessPoint` as it was (the free text, or null) and adds `trackAccessPoint` (`{id, name, kind}` of the catalogue, or null): a new key, never a change of type of a published one |
| `GET /track-access-points` | The track access points of `mto-configuration` as this service keeps them, read-only: what the location dialog offers. Filtered by `trackId`, `executionPackageId` and `enabled` (a filter left out does not filter); a list, not a page, ordered by track, kp (one without kp last) and id. Each item: `id` (the one of `mto-configuration`), `name`, `trackId`, `executionPackageId`, `stationId`, `kp` (metres), `kind` (`PREPARED_ACCESS`, `LEVEL_CROSSING`, `DEPOT`), `side` (`LEFT`, `RIGHT`, `BOTH`), `roadRailAccess`, `maxAxleLoadT`, `maxGrossWeightT`, `latitude`, `longitude`, `enabled` |
| `GET/POST /instruments`, `GET/PUT/PATCH/DELETE /instruments/{id}`, `GET /instruments/{id}/revisions` | The calibrated instruments. Search by `search` (code, name, serial number), `kind`, `active`, `calibrationExpiresBefore` (ISO date: a calibration that expires before that day; one without calibration never matches). `calibrationExpired`: its calibration expired before today. `DELETE` (and `active=false`) retires it |
| `GET /vehicles/positions` | The fleet board: every active vehicle with a known position, filtered by `executionPackageId`, `trackId` and `kind` on where it really is (an attached vehicle where its carrier is, which comes as `effective`) |
| `GET /operations/{id}` | A background operation: its status and, when finished, its result or error. Only to whom asked for it or to `ops-write` |
| `POST /actuator/republish[?executionPackageId=]`, `GET /actuator/republish/{id}` | Republish people, teams, vehicles and instruments (everything, or one EP without instruments: they are of no EP), always as a background operation (202) |

## Modifying

`PUT` never empties a field (a key left out is not touched); `PATCH` with
`application/merge-patch+json` (RFC 7396) also empties what is `null`, only what the resource allows
(400 `VAL-001` otherwise). Both accept the `version` that was read: a stale one is 409 `CON-001`
without writing anything. A team's leader is replaced, never emptied.

## Background operations

What may take long runs as a background operation (`docs/01-architecture.md`). Today that is
`POST /actuator/republish`, always asynchronous: it answers `202 Accepted` with the operation at once.
A route of the API that adopts it follows RFC 7240: only a request with `Prefer: respond-async`
(optionally `, wait=N` seconds, bounded by `app.operations.max-wait`) gets a `202 Accepted` when the
work does not finish within that budget, with `Location: /api/v1/resources/operations/{id}`,
`Preference-Applied: respond-async` and the operation; without the header the call stays synchronous.
`GET /operations/{id}` returns, once finished, `resultStatus` and `result` (the body the synchronous
call would have returned) or `error` (`status`, `errorCode`, `message`). Above
`app.operations.max-active` live operations: 429 `OPS-429` with `Retry-After`.

Every `Location` (a `201`, a `202`) carries the path **inside the service** (`/api/v1/resources/...`),
not the gateway's (`/api/resources/...`): the clients behind the gateway do not follow it, they build the
route from the id in the body, like with the other services.

## Error codes

| Code | When |
|---|---|
| `VAL-001`, `REQ-400`, `REQ-VALIDATION` | A request that is malformed or breaks a rule of its own content (a team without leader, 1 or 7 linemen, an incoherent model, a future `observedAt`, an expiry before the issue or the calibration, a licence on a type that is not a `DRIVING_LICENCE`) |
| `PER-404`, `UNI-404`, `VEH-404`, `VTY-404`, `PRK-404`, `QTY-404`, `QUA-404`, `VDO-404`, `INT-404`, `TAP-404`, `ABS-404`, `REP-404`, `OPS-404` | Not found (`TAP-404`: an `accessPointId` that is not in the catalogue), also the history (`/revisions`) of something without revisions: the code is the aggregate's, never `APP-404`. A qualification of another person, or a document of another vehicle, is `QUA-404` or `VDO-404` |
| `PER-409`, `UNI-409`, `VEH-409`, `VTY-409`, `PRK-409`, `QTY-409`, `VDO-409`, `INT-409` | A repeated code, username, employee number, plate, rail number or GPS device; a second document of the same kind |
| `QUA-001` | A qualification rule: the person already has one of that type (renew it instead), or the type is retired (also when a model asks for it) |
| `UNI-001` | An organization rule: the crew limits, a parent of the wrong level, a head without the category, a person already in another team, a unit with active units under it |
| `ABS-001` | Two absences of the same person that overlap |
| `REP-001` | A workshop rule: a transition the repair does not admit (closing a planned one, opening a closed one), its dates once closed or cancelled, whether it immobilises once opened |
| `VEH-001` | A fleet rule: putting a vehicle back to work by hand while a repair that immobilises it is open, where a vehicle can be, towing and carrying, a retired vehicle or model, the default vehicle of an active team, an access point that is disabled, on another track, closed to road-rail vehicles, not prepared for a model that needs it or too weak for its weight |
| `CON-001` | A stale version |
| `OPS-429` | Too many background operations |
| `KCK-503` | Keycloak cannot be read for the discrepancy report (down, the service account refused, or `app.keycloak.admin.enabled=false`) |
| `AUTH-401`, `AUTH-403` | Authentication, permission |
