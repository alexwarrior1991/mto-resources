# 01 — Architecture

Spring Boot 4 / Java 25, the three layers of `mto-maintenance` under `com.alejandro.mtoresources`:

- `domain/model` — framework-free rules: `CrewComposition` (1 leader + 2..6 linemen; the same rule is
  copied in `mto-maintenance` for the crew of a shift), `OrganizationRules` (parent of a strictly
  higher level, head of the category of the level, who can be a lineman), `FleetRules` (a model
  coherent with its mobility; where a vehicle can be; towing and carrying; the driver qualification of a
  model; where a road-rail vehicle may on-track: the rules of a track access point), `ExpiryRules` (when something expires and what to warn about), `DomainValidations`.
- `application` — `dto` (one package per resource), `service` + `service/impl` (package-private impls
  behind public interfaces; `OrganizationLookups` and `FleetResponses` are shared helpers;
  `ResourcesEvents` is the event contract), `mapper` (MapStruct, entity → response only), `exception`.
- `infrastructure` — `persistence` (entities, repositories with the native upserts of the position and of the
  track access points,
  specifications, Envers audit), `web` (controllers, `MergePatchArgumentResolver`, `PreferHeader`,
  the exception handler, the `republish` Actuator endpoint), `messaging/rabbitmq` (the consumers of the
  shifts of `mto-maintenance`, the positions of `mto-field` and the track access points of `mto-configuration`) and `messaging/outbox` (the copy of `mto-configuration`'s outbox).
- `configuration` — security (Keycloak resource server), `rabbitmq`, `messaging` (signature), `outbox`,
  `async` (virtual threads and the background operations), `expiry` (`ExpiryWatchProperties` and the
  daily scheduler of `ExpiryWatchService`, `app.resources.expiry.*`), JPA auditing, OpenAPI, the `Clock`.

## Virtual threads

`spring.threads.virtual.enabled` puts Tomcat, `@Scheduled` (the outbox relay, the heartbeat of the
background operations) and the RabbitMQ listeners on virtual threads. `AsyncConfiguration` gives the
background work its own `newVirtualThreadPerTaskExecutor`, decorated so that the new thread carries
the MDC (`correlationId`) and the `SecurityContext` of whoever asked: without them `created_by`, the
Envers revision and the `actor` of the events would be lost.

The scarce resource is not the thread but the **connection**: a virtual thread is cheap, but while it
works it holds a Hikari connection. So the pool size is explicit (`DATABASE_POOL_MAX_SIZE`, 10, as in
`mto-maintenance` and `mto-stock`: the pools of every service share the 100 `max_connections` of the
PostgreSQL of `mto-platform`) and everything that runs in the background has a cap for the whole
deployment (`app.operations.max-active`, 4), counted in the database, not in a semaphore that three
replicas would triple.

## Background operations (`BudgetedOperations`)

What may take long runs as an operation (`background_operation`): the work starts on a virtual thread
in its own transactions and the caller waits up to a **budget**. If it finishes in time, the answer is
the usual one; if not, `202 Accepted` with the operation, and `GET /api/v1/resources/operations/{id}`
gives its result (the same body the synchronous call would have given, or its error with its code).

- **Opt-in** (RFC 7240): only a request with `Prefer: respond-async` (and `wait=N`, bounded by
  `app.operations.max-wait`) gets a 202; without it a call is synchronous. Nothing that exists breaks.
- **Cap**: `app.operations.max-active` live operations (queued or running with a fresh heartbeat) in
  the whole deployment, checked under an advisory lock when the row is inserted; above it, 429
  `OPS-429` with `Retry-After`.
- **One at a time per scope** (`EP-14`, `republish`): the next one waits `QUEUED` and **holds no
  connection** while it waits — a short transaction tries to start it and, if it is not its turn, it
  sleeps with a growing backoff (200 ms → 2 s). A sleeping virtual thread costs nothing.
- **Heartbeat and abandon**: every replica beats for its live operations; one whose heartbeat stops for
  `app.operations.stale-after` is failed (`OPS-410`) by any replica. Finished ones are purged after
  `app.operations.retention`.
- **Context**: what the servlet request knew (correlation id, method, URI, client address, user agent)
  is copied into `BackgroundOperationContext`, which the Envers listener and the event factory read on
  the worker thread.

Today it serves `POST /actuator/republish`, always asynchronous. The consumer of shifts is
asynchronous by nature (it arrives by the broker).
