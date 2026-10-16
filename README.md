# MTO Resources

Spring Boot 4 / Java 25 REST API for the **people, the organization and the fleet** of the MTO domain
(railway catenary): who works, who leads whom (project → department → chief supervision → supervision
→ team, a team being one team leader and 2 to 6 linemen in one vehicle), and the rail, road-rail and
road vehicles with their characteristics and where each one is.

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
`mantenimiento.jefe` (chief of maintenance) and `proyecto.jefe` (project manager). See
[`keycloak/README.md`](keycloak/README.md).

To load a new consumer (or after `mto-maintenance` declared its queue the first time):

```bash
curl -X POST -H "Authorization: Bearer $OPS_TOKEN" http://localhost:8089/actuator/republish
```

## Configuration

Everything is read from the environment; `.env.example` lists every variable with its default. The ones
without a default: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `KEYCLOAK_ISSUER_URI`,
`KEYCLOAK_CLIENT_ID`, `KEYCLOAK_AUDIENCE` and, with RabbitMQ on, `SPRING_RABBITMQ_USERNAME`/`PASSWORD`.

## Tests

```bash
./mvnw test                                       # Testcontainers postgres:17-alpine
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/mto_resources_test \
TEST_DATABASE_USERNAME=... TEST_DATABASE_PASSWORD=... ./mvnw test   # without Docker
```
