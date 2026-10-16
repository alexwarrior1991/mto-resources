# Realm de Keycloak para `mto-resources`

Estos ficheros son la definición de lo que `mto-resources` necesita en el servidor de identidad. Se
versionan para que la configuración de Keycloak se revise en pull request como cualquier otro cambio,
y para que los entornos no diverjan por lo que alguien pinchó un día en la consola. No hay ningún
secreto: `mto-resources` no llama a nadie y no tiene cuenta de servicio.

## El realm es compartido

Todos los servicios del dominio viven en el **mismo realm** (`mto`). El realm base lo crea y lo posee
[`mto-platform`](https://github.com/alexwarrior1991/mto-platform), y cada servicio aporta desde su
propio repositorio lo suyo. Este trae dos ficheros:

| Fichero | Qué aporta |
|---|---|
| `mto-resources-partial-import.json` | El cliente `mto-resources-api`, sus permisos y los perfiles funcionales `mto-resources-*`. Vale para cualquier entorno. |
| `mto-resources-dev.json` | Los usuarios de desarrollo, contraseña `local`: `recursos.lector`, `recursos.gestor` y `recursos.responsable`, con los perfiles de abajo, y uno por cada perfil de puesto (al final). Aparte a propósito, para poder aplicar lo anterior en un entorno desplegado sin arrastrarlos. |

Los aplica `mto-platform/keycloak/apply-partials.sh`, que fija el orden: primero las parciales que
crean los clientes (la de `mto-notification` la primera, porque los perfiles de los demás nombran sus
roles; esta, tras la de `mto-field`), después `mto-ops-cross-service.json` y
`mto-positions-cross-service.json`, que los nombran, y al final los ficheros de desarrollo. El de este
repositorio tiene que ir detrás del de puestos: sus usuarios de puesto llevan esos perfiles, y un usuario que lleva un
perfil que todavía no existe no da error, porque Keycloak crea un rol vacío con ese nombre y la persona
entra sin permisos (`check_realm_consistency.py` de la plataforma lo comprueba).

`mto-resources` no declara ningún cliente de navegador: los de login (`mto-frontend` del realm base y
`mto-backoffice`) llevan un *audience mapper* hacia `mto-resources-api`.

## Permisos y perfiles

Los **permisos** son roles de cliente de `mto-resources-api` y son lo que comprueba el código
(`SecurityRoles`). Ninguno implica a otro (`ApiAuthorizationRulesTest`).

| Permiso | Concede |
|---|---|
| `resources-read` | Todo `GET`/`HEAD` bajo `/api/v1/resources` |
| `resources-write` | Personas, unidades del organigrama, linemen de los equipos y sitios de aparcamiento |
| `resources-fleet-write` | Modelos de vehículo y vehículos |
| `resources-location-write` | Solo `POST /vehicles/{id}/locations`: registrar dónde está un vehículo |
| `resources-delete` | `DELETE`, y `PUT`/`PATCH` con `active=false` |
| `ops-metrics` | Lectura de los endpoints de Actuator |
| `ops-write` | Operaciones de Actuator que modifican estado (`outbox`, `republish`) |

Los **perfiles funcionales** son roles de realm compuestos y son lo que se asigna a las personas:

| Perfil | Agrupa |
|---|---|
| `mto-resources-viewer` | `resources-read`, `config-read` y `notification-inbox` |
| `mto-resources-manager` | `resources-read`, `-write`, `-fleet-write`, `-location-write`, `config-read`, `notification-inbox` y `notification-activity-read` |
| `mto-resources-admin` | lo del gestor + `resources-delete` |

`config-read` va en los tres porque el personal y la flota nombran EP, vías y estaciones de
`mto-configuration`, y los frontales los nombran pidiéndoselos a su servicio.

## Permiso no es puesto

Los **perfiles de puesto** (`mto-position-worker`, `-team-leader`, `-supervisor`,
`-chief-supervisor`, `-chief-maintenance`, `-project-manager`) no están aquí: cruzan servicios y viven
en `mto-platform/keycloak/mto-positions-cross-service.json`. Dicen qué puede hacer cada puesto en cada
servicio. A quién dirige una persona y en qué EP lo dice el organigrama de `mto-resources`, no Keycloak:
se guardan separados a propósito (un suplente puede necesitar el perfil sin cambiar de puesto, y
`mto-resources` no toca el realm).

Para probarlos, `mto-resources-dev.json` trae un usuario por puesto (contraseña `local`):

| Usuario | Perfil de puesto |
|---|---|
| `equipo.operario` | `mto-position-worker` |
| `equipo.jefe` | `mto-position-team-leader` |
| `equipo.supervisor` | `mto-position-supervisor` |
| `equipo.jefe-supervisores` | `mto-position-chief-supervisor` |
| `mantenimiento.jefe` | `mto-position-chief-maintenance` |
| `proyecto.jefe` | `mto-position-project-manager` |

No están en ningún grupo de equipo de `mto-field` (`EQ-NORTE`, `EQ-SUR`): el supervisor y el jefe de
supervisores, con `field-supervise`, se unen a cualquier turno, pero el operario y el jefe de equipo
no se unen a ninguno mientras no estén en el grupo del suyo.
