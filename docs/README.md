# mto-resources documentation

Read in order:

| Document | What |
|---|---|
| [00-project-overview.md](00-project-overview.md) | What the service is for, what it is not, and why it is a service of its own |
| [01-architecture.md](01-architecture.md) | Layers, packages, virtual threads and the background operations |
| [02-domain-model.md](02-domain-model.md) | People, the organization tree, the crew rule, the fleet and where each vehicle is |
| [03-database.md](03-database.md) | Every table, enum, constraint and index |
| [04-rest-api.md](04-rest-api.md) | Routes, permissions, errors, merge-patch and `Prefer: respond-async` |
| [05-development-roadmap.md](05-development-roadmap.md) | What is done and what comes next |
| [06-messaging.md](06-messaging.md) | The own events (people, teams, vehicles) and the shifts it consumes |
| [07-auditing.md](07-auditing.md) | What is audited with Envers, what is not, and why |

`messaging/examples/` holds one JSON per published event, checked by `MessagingContractExamplesTest`.
