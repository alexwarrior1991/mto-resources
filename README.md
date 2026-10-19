# MTO Resources

Spring Boot 4 / Java 25 REST API for the **people, the organization and the fleet** of the MTO domain
(railway catenary): who works, who leads whom (project → department → chief supervision → supervision
→ team, a team being one team leader and 2 to 6 linemen in one vehicle), and the rail, road-rail and
road vehicles with their characteristics and where each one is; and until when: the qualifications of
every person, the documents of every vehicle and the calibration of every instrument, with a daily warning
of what expires.

It is shared by [`mto-maintenance`](https://github.com/alexwarrior1991/mto-maintenance) (and
tomorrow `mto-construction`), published through
[`mto-gateway`](https://github.com/alexwarrior1991/mto-gateway) at `/api/resources` and deployed with
[`mto-platform`](https://github.com/alexwarrior1991/mto-platform). Documentation lives in
[`docs/`](docs/README.md).

## Requirements

- JDK 25
- PostgreSQL 17 (16+ works)
- Docker for the Testcontainers-backed tests, or `TEST_DATABASE_URL` pointing at a PostgreSQL
- RabbitMQ and Keycloak, provided by `mto-platform`

## Running locally

```bash
cd ../mto-platform && docker compose --profile all up -d && ./keycloak/apply-partials.sh
cd ../mto-resources && ./mvnw spring-boot:run      # dev profile, http://localhost:8089, Swagger at /swagger-ui.html
```

Development users (password `local`): `recursos.lector` (read), `recursos.gestor` (people, teams, fleet
and locations), `recursos.responsable` (and deactivating), and one per position profile of
`mto-platform/keycloak/mto-positions-cross-service.json`: `equipo.operario` (worker), `equipo.jefe`
(team leader), `equipo.supervisor` (supervisor), `equipo.jefe-supervisores` (chief supervisor),
`mantenimiento.jefe` (chief of maintenance) and `proyecto.jefe` (project manager). The worker and the
team leader are in the group `A` of `mto-field` (named after the team `A` of `mto-maintenance`), so they
can join that team's shifts there. See [`keycloak/README.md`](keycloak/README.md).

To load a new consumer (or after `mto-maintenance` declared its queue the first time, or after a migration
that adds keys to the events, like `V8`), republish people, teams, vehicles and instruments:

```bash
curl -X POST -H "Authorization: Bearer $OPS_TOKEN" http://localhost:8089/actuator/republish
```

The other way round, the track access points of `mto-configuration` (what a road-rail vehicle on-tracks at,
`GET /api/v1/resources/track-access-points`) arrive by its master-data channel on this service's own queue
`mto.resources.master-data.queue`. What was published before that queue existed (the first deployment of
`V9`) is loaded by republishing them there:

```bash
curl -X POST -H "Authorization: Bearer $CONFIG_TOKEN" \
  "http://localhost:8081/api/v1/configuration/master-data/republish?entity=track-access-point"
```

## Configuration

Everything is read from the environment; `.env.example` lists every variable with its default. The ones
without a default: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `KEYCLOAK_ISSUER_URI`,
`KEYCLOAK_CLIENT_ID`, `KEYCLOAK_AUDIENCE` and, with RabbitMQ on, `SPRING_RABBITMQ_USERNAME`/`PASSWORD`.
The discrepancy report reads Keycloak with `KEYCLOAK_SERVICE_CLIENT_SECRET` (the service account
`mto-resources-svc`); without it, that report answers 503 `KCK-503` and everything else works.

## Tests

```bash
./mvnw test                                       # Testcontainers postgres:17-alpine
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/mto_resources_test \
TEST_DATABASE_USERNAME=... TEST_DATABASE_PASSWORD=... ./mvnw test   # without Docker
```
