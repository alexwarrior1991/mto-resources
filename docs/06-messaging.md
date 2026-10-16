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
| `person.created`, `person.updated`, `person.deactivated` | `code`, `firstName`, `lastName`, `fullName`, `username`, `employeeNumber`, `category`, `employer`, `company`, `active`, `version`. Never the phone. |
| `team.created`, `team.updated`, `team.deactivated` | `code`, `name`, `discipline`, `active`, `parentId`, `departmentId`, `executionPackageIds`, `defaultVehicle {id, code}`, `leader`, `supervisor` (head of the nearest supervision above), `chiefSupervisor`, `members` (each person as `{personId, code, fullName, username, category}`), `linemen`, `version` |
| `vehicle.created`, `vehicle.updated`, `vehicle.deactivated` | `code`, `name`, `type {id, code, name, mobility, category, selfPropelled, portable, seats, railGearType, transportModeAuthorised, capabilities}`, `registrationPlate`, `railVehicleNumber`, `operationalStatus`, `available`, `active`, `version` |

One example per event in `messaging/examples/`, built by `MessagingContractExamplesTest` with
`ResourcesEvents` and the real factory. **Keys are only added**; a new key or event changes its
example in the same commit (`MESSAGING_EXAMPLES_WRITE=true` regenerates them).

### What a consumer has to know

- **The watermark is the outbox `sequenceNumber` header, not `version`.** Changing the head of a
  supervision changes the event of every team under it without touching their version. Every
  transaction that publishes a `team.updated` first locks the rows of those teams in id order
  (`OrganizationLookups.lockTeams`), and the vehicles and people it republishes likewise, so the order
  of the sequence numbers is the order of the commits for each of them.
- **A random `operationId` per event**: two `team.updated` of the same request are two messages.
- **What republishes what**: a person's name, username, category or code → their teams; the head or the
  parent of a unit → every team under it; the linemen of a team → that team; a vehicle model → its
  vehicles.
- **`updated` is an upsert**: `republish` publishes `updated` (or `deactivated`) of things the consumer
  may never have seen.
- **Only what someone listens to is published**: the outbox publishes with `mandatory=true`, and an
  event without a queue fails and blocks its aggregate. Locations are not published until someone
  consumes them (then as `vehicle-position`).
- **Deployment order**: first `mto-maintenance`, which declares its queue
  (`mto.maintenance.resources.queue`, bound to `mto.resources.{person,team,vehicle}.*`), then this
  service; and `POST /actuator/republish` to load it.

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
