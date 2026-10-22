# 06 — Messaging

One broker, the one of `mto-platform`.

## Published events

Exchange `mto.resources.exchange` (topic, durable, declared here; **no queue here**: a queue belongs to
whoever consumes it). Routing key `mto.resources.<entity>.<event>`, event type
`RESOURCES_<ENTITY>_<EVENT>`, the `AsynchronousMessage` envelope of `mto-configuration` with `actor`
(`PERSON`/`SERVICE`/`SYSTEM`, from the token) and `correlationId` (`X-Correlation-Id` of the request),
signed with the shared secret.

**`DomainEventPublisher.publish` is the only door, and it is called inside the business transaction**:
the event goes to `outbox_message` with the change and the relay publishes it afterwards with
publisher confirms. With `app.rabbitmq.enabled=false` the publisher is the `NoOpDomainEventPublisher`.

| Event | `values` |
|---|---|
| `person.created`, `person.updated`, `person.deactivated` | `code`, `firstName`, `lastName`, `fullName`, `username`, `employeeNumber`, `category`, `employer`, `company`, `active`, `version`, `republished`, `qualifications` (all of them, the expired ones too, by `code`: `{code, kind, drivingLicence, expiresOn}`, `expiresOn` null when it does not expire), `unavailable` (`[{from, to}]`, the periods of their absences that end today or later, both dates included; never their kind). Never the phone. |
| `team.created`, `team.updated`, `team.deactivated` | `code`, `name`, `discipline`, `active`, `parentId`, `departmentId`, `executionPackageIds`, `defaultVehicle {id, code}`, `leader`, `supervisor` (head of the nearest supervision above), `chiefSupervisor`, `members` (each person as `{personId, code, fullName, username, category}`), `linemen`, `version`, `republished` |
| `vehicle.created`, `vehicle.updated`, `vehicle.deactivated` | `code`, `name`, `type {id, code, name, mobility, category, selfPropelled, portable, seats, railGearType, transportModeAuthorised, capabilities, requiredDriverQualification}` (the code of the qualification its driver needs, or null), `registrationPlate`, `railVehicleNumber`, `operationalStatus`, `available`, `active`, `version`, `availabilityChanged`, `republished`, `documents` (all of them, the expired ones too, by `kind`: `{kind, reference, expiresOn}`), `unavailable` (`[{from, to}]`, the repairs that immobilise it, planned or open, that end today or later; `to` null without a known end) |

| `instrument.created`, `instrument.updated`, `instrument.deactivated` | `code`, `name`, `kind`, `serialNumber`, `calibratedOn`, `calibrationExpiresOn`, `active`, `version`, `republished`. `deactivated` once, when it is retired; a change to a retired one is `updated` |
| `qualification.expiring`, `qualification.expired` | The id is the one of the `person_qualification` row. `personId`, `personCode`, `fullName`, `username`, `qualificationCode`, `qualificationName`, `kind`, `expiresOn`, `daysLeft` (negative once expired), `teamCode` (the open team of the person, as a lineman or as its leader, or null), `leader` (`{personId, code, fullName, username, category}` of that team's leader, or null; never the person themselves) |
| `vehicle-document.expiring`, `vehicle-document.expired` | The id is the one of the document. `vehicleId`, `vehicleCode`, `vehicleName`, `kind`, `reference`, `expiresOn`, `daysLeft` |
| `calibration.expiring`, `calibration.expired` | The id is the one of the instrument. `instrumentId`, `code`, `name`, `kind`, `serialNumber`, `calibrationExpiresOn`, `daysLeft` |

The expiry warnings come from the daily `ExpiryWatchService` (`docs/02-domain-model.md`, *Expiries*), with
actor `SYSTEM` and no correlation, once per expiry date. `mto-notification` reads them (`mto.resources.#`)
to warn the person, their leader and whoever manages the staff or the fleet.

One example per event in `messaging/examples/`, built by `MessagingContractExamplesTest` with
`ResourcesEvents` and the real factory. **Keys are only added**; a new key or event changes its
example in the same commit (`MESSAGING_EXAMPLES_WRITE=true` regenerates them).

### What a consumer has to know

- **The watermark is the outbox `sequenceNumber` header, not `version`.** Changing the head of a
  supervision changes the event of every team under it without touching their version. Every
  transaction that publishes a `team.updated` first locks the rows of those teams in id order
  (`OrganizationLookups.lockTeams`), and the vehicles and people it republishes likewise, so the order
  of the sequence numbers is the order of the commits for each of them.
- **A random `operationId` per event**: two `team.updated` of the same request are two messages. The
  exception are the expiry warnings, whose `operationId` is derived from what they tell
  (`UUID.nameUUIDFromBytes("<entity>:<event>:<id>:<expiresOn>")`, `ResourcesEvents.expiryOperationId`):
  two passes that both published leave one in the inbox.
- **What republishes what**: a person's name, username, category or code → their teams; the head or the
  parent of a unit → every team under it; the linemen of a team → that team; a vehicle model → its
  vehicles; a qualification of a person (given, changed or removed) → that person, `person.updated` with
  the whole list (the person row is locked first, so the last event of a person carries every
  qualification); the kind or the licence of a qualification type → the people who hold it, locked in id
  order; a document of a vehicle → that vehicle (`availabilityChanged=false`, the vehicle row locked first);
  an absence of a person → that person; a repair of a vehicle → that vehicle (with `availabilityChanged` when
  opening or closing it moved its status), each row locked first.
- **A list travels whole**: `qualifications` and `unavailable` (people), `documents` and `unavailable`
  (vehicles) are all of them, and an event replaces the list the consumer had. `unavailable` only carries
  what ends today or later: what is past drops out with the next event, and a consumer compares it with the
  date of its shift anyway. An event without the key (an older `mto-resources`) says
  nothing about it.
- **`updated` is an upsert**: `republish` publishes `updated` (or `deactivated`) of things the consumer
  may never have seen. What it publishes carries `republished: true` (everything else, `false`): a
  snapshot applies it like any other, and whoever tells people about changes (`mto-notification`)
  ignores it, because nothing changed.
- **`availabilityChanged`** (vehicles): `true` only when that change took the vehicle from available to
  not available or back (out of service, back in service, retired while available). A model change, an
  arrival or a republication say `false`, so that a notice of «out of service» goes out once.
- **Only what someone listens to is published**: the outbox publishes with `mandatory=true`, and an
  event without a queue fails and blocks its aggregate. Locations are not published until someone
  consumes them (then as `vehicle-position`).
- **Deployment order**: first the consumers, which declare their queues — `mto-maintenance`
  (`mto.maintenance.resources.queue`, bound to `mto.resources.{person,team,vehicle,instrument}.*`) and
  `mto-notification` (`mto.notification.resources.queue`, bound to `mto.resources.#`, which records
  every event in the activity log) — then this service; and `POST /actuator/republish` to load them
  (also after `V8`: the people, vehicles and instruments a consumer has do not carry qualifications,
  documents, the driver qualification of their model nor the instruments until they are republished).

## The queues it consumes: classic or quorum

The three queues below (shifts, positions, track access points) and their DLQs are of the type
`app.rabbitmq.queues.type` says (`ConsumerQueueProperties`): `classic` by default and locally (one broker
node), `quorum` where RabbitMQ runs as a cluster, as on OpenShift (`APP_RABBITMQ_QUEUES_TYPE=quorum`), so
that a queue survives the loss of a node. With `classic` they are declared exactly as before, without
`x-queue-type`: adding the argument to a queue that already exists would be another declaration and the
broker would refuse it. A quorum queue carries `x-delivery-limit` (`app.rabbitmq.queues.delivery-limit`,
5): nothing rejected is requeued here (the retries happen inside one delivery and then the message goes to
the DLQ), so the broker's count only grows when a message comes back unacknowledged because the consumer
died or lost its connection; five lets a couple of ordinary restarts through and stops the message that
kills the consumer every time. The DLQs are quorum too, without a limit. Changing the type of an existing
queue means deleting it first (stop the consumer, let it drain, delete, redeploy).

## Consumed: the shifts of `mto-maintenance`

Queue `mto.resources.maintenance.queue` (declared here, with its DLX `...queue.dlx` and DLQ
`...queue.dlq`), bound to `mto.maintenance.exchange` with `mto.maintenance.shift.*`. The consumer
(`MaintenanceEventConsumer`) checks the signature and that the envelope comes from `mto-maintenance`
and has `data`, `entityName` and `eventName`; what has no remedy goes to the DLQ without retries. The
idempotent **inbox** (`inbox_message`, unique `(message_id, source_service)`, the envelope's
`operationId` as key) decides whether it runs, and `DispatchingIncomingEventHandler` routes by source
and entity to the `IncomingEventHandler`s; an unknown entity is logged and ignored.

`MaintenanceShiftEventHandler` reads from the shift's `values`: `code`, `trackIds`,
`executionPackageId`, `startKp`, `actualStart`, `actualEnd`, `parkingPlace`, `vehicleIds` and
`vehicles[{id, parking{siteId, trackId, kp, description}}]`. What it does with them is in
`02-domain-model.md` (*Derived from the shifts*). Every location it records carries
`source = SHIFT` and `sourceReference = mto-maintenance:shift:<id>`, and the same reference with the
same `observedAt` is never recorded twice, so a republication of a shift does not repeat anything. An
event without `vehicleIds` (an older `mto-maintenance`) moves nothing.

When the field device of the shift reported a position after the `actualEnd` written at closing, the
close is recorded one millisecond after the last `FIELD` position of that shift (`findLastObservedAt`):
the same `observedAt` every time, so a republication does not repeat it either.

The examples `mto-maintenance` versions for these three events (`docs/messaging/examples/shift-started.json`,
`shift-closed.json` and `shift-cancelled.json` there) are copied in
`src/test/resources/contracts/mto-maintenance/`, and `BusinessLayerTest` passes them through the real
handler with a vehicle of its own: a key renamed there fails here, not in a vehicle's position.

## Consumed: the positions of `mto-field`

Queue `mto.resources.field.queue` (declared here, with its DLX `...queue.dlx` and DLQ `...queue.dlq`),
bound to `mto.field.telemetry.exchange` with `mto.field.position.*` (`app.rabbitmq.field.*`). It is a
different exchange from the events of `mto-field` (`mto.field.exchange`), because `mto-notification`
records everything that reaches that one and a position per minute and vehicle is not activity. The
consumer (`FieldEventConsumer`, a copy of the one of the shifts) checks the signature and that the
envelope comes from `mto-field`; the same idempotent inbox (`source_service = mto-field`) decides whether
it runs, and `FieldPositionHandler` applies it (`02-domain-model.md`, *Derived from the field devices*).

`position.reported` carries in `values`: `code` (the possession), `shiftId`, `shiftCode`, `teamCode`,
`deviceId`, `vehicleId`, `trackId`, `kp`, `executionPackageId`, `observedAt` and `reportedBy`. Each position is an
aggregate of its own in the outbox of `mto-field` (nothing waits behind a failed one: the watermark,
not the order, decides). This queue must exist before `mto-field` publishes positions (`mandatory=true`
there): deploy this service first. The example `mto-field` versions
(`docs/messaging/examples/position-reported.json` there) is copied in
`src/test/resources/contracts/mto-field/` and `BusinessLayerTest` passes it through the real handler.

## Consumed: the track access points of `mto-configuration`

Queue `mto.resources.master-data.queue` (declared here, with its DLX `...queue.dlx` and DLQ
`...queue.dlq`), bound to `mto.master-data.exchange` **only** with `mto.master-data.track-access-point.*`
(`app.rabbitmq.master-data.*`): of the rest of the infrastructure this service keeps only ids, so it does
not listen to `mto.master-data.#`. The consumer (`MasterDataEventConsumer`) checks the signature, that the
envelope comes from `mto-configuration` and has `data`, `entityName` and `operation` (`CREATED`, `UPDATED`,
`DELETED`; an unknown one cannot be read and goes to the DLQ); what has no remedy goes to the DLQ without
retries. The envelope is the one of the master data (`data.operation` where the own events say
`data.eventName`): it is turned into the common one with the operation in lower case as the event name, and
goes through the same idempotent inbox (`source_service = mto-configuration`) and the same dispatcher to
`TrackAccessPointMasterDataHandler`.

`track-access-point` carries in `values`: `id`, `name`, `enabled`, `kp` (metres), `kind`, `side`,
`roadRailAccess`, `maxAxleLoadT`, `maxGrossWeightT`, `latitude`, `longitude` (the decimals as JSON
numbers), `station` (`{id, name}` or null) and `track` (`{id, name, executionPackageId}`, never null).
`created` and `updated` upsert the snapshot `track_access_point` as it comes; `deleted` (a logical deletion
there, with the same `values`) disables it. The watermark is the `sequenceNumber` header (global in the
outbox of `mto-configuration`), compared inside the writing statement: an older event changes nothing, one
without a sequence applies and keeps the stored one, and a deletion advances it too, so a late creation
does not bring it back (`docs/03-database.md`). A `created`/`updated` without its id, name, track, kind or
side cannot be applied: it fails, is retried and ends in the DLQ with the reason in the inbox.

The example `mto-configuration` versions (`docs/messaging/examples/master-data-track-access-point-updated.json`
there) is copied in `src/test/resources/contracts/mto-configuration/`: `MessagingLayerTest` reads it with the
converter of the channel and `BusinessLayerTest` passes it through the real handler. A change of that
example is copied here in the same change.

**Deployment order**: unlike the positions of `mto-field`, this service does not have to be deployed before
`mto-configuration` publishes access points: `mto.master-data.exchange` already has other queues bound to
`mto.master-data.#` (those of `mto-stock`, `mto-maintenance` and `mto-notification`), so the `mandatory` of
the publisher never fails because this queue is missing. But what is published before this queue exists
does not reach it: once this service has declared its queue, load the snapshot by republishing the access
points from `mto-configuration` (`POST /api/v1/configuration/master-data/republish?entity=track-access-point`,
optionally `&trackId=`; `entity=all` includes them). The republication arrives as `UPDATED` and is applied
like any other.

