# Database

Local development uses **MySQL 8.4** running in Docker, wired up via
[`docker-compose.yml`](../docker-compose.yml) at the repo root.

## MySQL Docker setup

```yaml
# docker-compose.yml (summary)
services:
  mysql:
    image: mysql:8.4
    container_name: gotgotneed-mysql
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - gotgotneed-mysql-data:/var/lib/mysql        # persistent data
      - ./docker/mysql/init:/docker-entrypoint-initdb.d:ro   # one-time init scripts
    healthcheck:
      test: mysqladmin ping -h 127.0.0.1 -u$MYSQL_USER -p$MYSQL_PASSWORD --silent
```

- **Container name:** `gotgotneed-mysql` — fixed, so tooling/scripts can
  refer to it reliably (e.g. `docker exec -it gotgotneed-mysql ...`).
- **Persistence:** the named volume `gotgotneed-mysql-data` survives
  `docker compose down`; only `docker compose down -v` deletes it.
- **Health check:** runs `mysqladmin ping` using the app credentials every
  10s (after a 30s startup grace period) so `docker compose ps` accurately
  reports `healthy` only once MySQL can actually accept connections — this
  is what you should wait for before starting the backend.
- **Init scripts:** anything placed in
  [`docker/mysql/init/`](../docker/mysql/init/) runs once, only against a
  brand-new (empty) volume — see that directory's `README.md` for details.
  This is for optional seed/bootstrap data, not schema migrations.

## Database name and credentials

Everything is parameterized via environment variables, read from a
git-ignored `.env` file (see [`.env.example`](../.env.example) for the
template that's actually committed):

| Variable | Default | Used by |
|---|---|---|
| `MYSQL_DATABASE` | `gotgotneed` | Compose — the database created on first boot. |
| `MYSQL_USER` | `gotgotneed` | Compose — the non-root app user created on first boot. |
| `MYSQL_PASSWORD` | `gotgotneed` | Compose — that user's password. |
| `MYSQL_ROOT_PASSWORD` | `gotgotneed` | Compose — root password, for administrative access only. |
| `MYSQL_PORT` | `3306` | Compose — host port mapped to the container's `3306`. |
| `DB_HOST` | `localhost` | Backend (`application.yaml`) — MySQL host as seen from wherever the backend runs. |
| `DB_PORT` | `3306` | Backend — MySQL port. |
| `DB_NAME` | `gotgotneed` | Backend — database to connect to. |
| `DB_USERNAME` | `gotgotneed` | Backend — connection user. |
| `DB_PASSWORD` | `gotgotneed` | Backend — connection password. |

These defaults are intentionally identical to each other so that running
`docker compose up -d` followed by `./gradlew :backend:bootRun` works with
**zero configuration** — no need to export anything unless you want to
point the backend at something other than the local container (a different
host, port, or a shared dev database).

> These are local development defaults, not secrets — but `.env` is
> git-ignored regardless, and only `.env.example` (no real credentials, just
> the same illustrative defaults) is committed. Never reuse these
> credentials outside a local/dev context.

## Connection configuration (Spring Boot)

From [`backend/src/main/resources/application.yaml`](../backend/src/main/resources/application.yaml):

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:gotgotneed}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:gotgotneed}
    password: ${DB_PASSWORD:gotgotneed}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

The `${VAR:default}` syntax means: use the environment variable if it's set
(e.g. exported in your shell, or injected by CI/a container orchestrator),
otherwise fall back to the literal default — which matches the local Docker
Compose setup. The JDBC driver comes from the `mysql-connector-j` runtime
dependency in [`backend/build.gradle.kts`](../backend/build.gradle.kts).

## JPA configuration

Also in `application.yaml`:

```yaml
spring:
  jpa:
    open-in-view: false
    show-sql: true
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect
        jdbc:
          time_zone: UTC
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

- **`ddl-auto: update`** — Hibernate inspects your `@Entity` classes and
  updates the schema to match automatically. Convenient for local
  development since there are no entities/migrations yet, but it must
  **never** be used against a shared, staging, or production database (it
  can silently make destructive-adjacent changes and doesn't produce a
  reviewable history). See "Future migration strategy" below.
- **`show-sql` / `hibernate.SQL: debug` / `jdbc.bind: trace`** — logs every
  SQL statement Hibernate executes, and the parameter values bound to it.
  Useful during development, noisy in production — this logging
  configuration is dev-oriented and should be tightened before any
  non-local deployment.
- **`open-in-view: false`** — disables Spring's Open Session In View
  pattern, which otherwise keeps a DB connection open for the entire
  request/response cycle (including view rendering). Turning it off is the
  generally-recommended default for REST APIs and forces data access to
  happen deliberately in the service layer rather than lazily during
  serialization.

## Future migration strategy

`spring-boot-starter-flyway` and `flyway-mysql` are already dependencies of
`:backend` (see [`backend/build.gradle.kts`](../backend/build.gradle.kts)),
but Flyway is currently **disabled**:

```yaml
spring:
  flyway:
    enabled: false
```

This is deliberate: Flyway and `ddl-auto: update` both try to own the
schema, and running both at once is a common source of confusing failures.
Since there are no migration scripts yet, disabling Flyway lets Hibernate
manage the schema during this early/scaffolding phase without conflict.

**When the schema stabilizes enough to matter (i.e. before any shared,
staging, or production database exists), migrate to Flyway-managed
schema:**

1. Add versioned SQL scripts under
   `backend/src/main/resources/db/migration/`, e.g.
   `V1__init_schema.sql`, `V2__add_users_table.sql`, following Flyway's
   `V<version>__<description>.sql` naming convention.
2. Set `spring.jpa.hibernate.ddl-auto: validate` (Hibernate only checks the
   schema matches the entities, never mutates it) or `none`.
3. Set `spring.flyway.enabled: true`.
4. From then on, every schema change is a new migration file, reviewed like
   any other code change, and applied identically in every environment.

The [`docker/mysql/init/`](../docker/mysql/init/) directory (mounted to
`/docker-entrypoint-initdb.d`) is a separate, complementary mechanism —
useful for optional seed/demo data on a fresh container, but it is not a
substitute for Flyway migrations, since it only runs once against a brand
new volume rather than being tracked/versioned per-environment.

## See also

- [`commands.md`](./commands.md) — exact `docker compose` / `mysql` shell
  commands.
- [`backend.md`](./backend.md) — how the backend module is structured
  around this configuration.
- [`troubleshooting.md`](./troubleshooting.md) — what to do if the
  container never reports healthy, or the backend can't connect.
