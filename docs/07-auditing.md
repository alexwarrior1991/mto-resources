# 07 — Auditing

Two layers, answering different questions, both resolving the actor through `AuditActorResolver`:

- `created_at`/`updated_at`/`created_by`/`updated_by` (Spring Data auditing) say who touched a row last.
- **Hibernate Envers** keeps the history of previous values, in `<table>_aud` and `audit_revision`
  (who, when, from which channel — `HTTP`, `MESSAGING`, `SYSTEM`, `BASELINE` — and with which
  correlation id, method, URI, client address and user agent). Read at `GET /<resource>/{id}/revisions`,
  the most recent first; no revision is 404.

## Audited

| Entity | Notes |
|---|---|
| `Person` | |
| `OrganizationUnit` | with its EPs (`organization_unit_execution_package_aud`) |
| `TeamMember` | the linemen; a membership is closed, never deleted |
| `Vehicle` | its model is read as it is now (`NOT_AUDITED` relation): models are a catalogue |
| `PersonQualification` | its type is read as it is now (`NOT_AUDITED`); a deleted one keeps its data in the `DEL` revision (`store_data_at_delete`); the expiry marks are `@NotAudited` |
| `VehicleDocument` | the same: a deleted one keeps its data, the expiry marks are not history |
| `PersonAbsence` | a deleted one keeps its data in the `DEL` revision |
| `VehicleRepair` | cancelled, never deleted; every transition is a revision |
| `Instrument` | retired, never deleted; the expiry marks of its calibration are not history. Read at `GET /instruments/{id}/revisions` |

The seeded project and department have a `BASELINE` revision (`V7`): without it, reading the history
of a unit under them would fail looking for its parent in `organization_unit_aud`.

## Not audited, on purpose

| Entity | Why |
|---|---|
| `VehicleType`, `ParkingSite`, `QualificationType` | catalogues of rare change |
| `VehicleLocationEvent` | it is already the append-only history of the locations |
| `VehiclePosition` | written only with native SQL, which Envers cannot see |
| `TrackAccessPoint` | the snapshot of a master of `mto-configuration`, written only with native SQL from its events (`@Immutable` for Hibernate); its history is `mto-configuration`'s |
| `InboxMessage`, `OutboxMessage`, `BackgroundOperation` | their history is themselves |

`@Audited` goes on each entity and never on `AuditableEntity`. `JpaEntityModelTest` guards the split.

## Background work

A background operation runs on another thread, after the servlet request has gone. What the request
knew is copied into `BackgroundOperationContext`, which `AuditRevisionListener` reads as the HTTP
source of the revision; the `SecurityContext` travels with the task (`AsyncConfiguration`), so the
revision and `created_by` carry the person who asked, not `system`.
