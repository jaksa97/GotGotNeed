# Database

Local development uses **MySQL 8.4** running in Docker, wired up via
[`docker-compose.yml`](../docker-compose.yml) at the repo root. That same
file also defines a `backend` service (see [`backend.md`](./backend.md) and
[`backend/Dockerfile`](../backend/Dockerfile)) that only starts once MySQL
reports healthy — see "Startup order" below.

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

## Startup order (Docker Compose)

When running `docker compose up --build`, the `backend` service declares:

```yaml
depends_on:
  mysql:
    condition: service_healthy
```

This means Compose brings the containers up in this order:

1. `mysql` container starts.
2. Compose polls the `mysqladmin ping` healthcheck above until it reports
   `healthy` (i.e. MySQL is actually accepting connections, not just that
   the process has started).
3. Only then does the `backend` container start and connect, using
   `jdbc:mysql://mysql:3306/...` (the `mysql` hostname resolves via the
   `gotgotneed-network` Docker network shared by both services).

This avoids the classic "backend crash-loops because MySQL was still
initializing" race condition that a plain `depends_on: [mysql]` (order-only,
no health condition) would not protect against.

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
| `BACKEND_PORT` | `8080` | Compose — host port mapped to the backend container's `8080`. |
| `DB_HOST` | `localhost` | Backend (`application.yaml`) — MySQL host when running `bootRun` on the host. **Not used** when the backend itself runs in Compose — there it's hardcoded to `mysql` directly in `docker-compose.yml`, since `localhost` inside the backend container would mean the backend container itself. |
| `DB_PORT` | `3306` | Backend — MySQL port for `bootRun`. Compose hardcodes this to the container-internal `3306` for the same reason as `DB_HOST` above (unaffected by a customized `MYSQL_PORT` host mapping). |
| `DB_NAME` | `gotgotneed` | Backend — database to connect to. Must match `MYSQL_DATABASE`. |
| `DB_USERNAME` | `gotgotneed` | Backend — connection user. Must match `MYSQL_USER`. |
| `DB_PASSWORD` | `gotgotneed` | Backend — connection password. Must match `MYSQL_PASSWORD`. |

These defaults are intentionally identical to each other so that running
`docker compose up -d` followed by `./gradlew :backend:bootRun` works with
**zero configuration** — no need to export anything unless you want to
point the backend at something other than the local container (a different
host, port, or a shared dev database). The same defaults also mean
`docker compose up --build` (backend + MySQL both containerized) works out
of the box, since `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` already match
`MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD`.

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

## Verifying the backend connected to MySQL

After `docker compose up --build`:

```bash
docker compose ps
# both gotgotneed-mysql and gotgotneed-backend should show "healthy"

docker compose logs backend
# look for HikariCP connecting successfully, e.g.:
#   HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@...
#   HikariPool-1 - Start completed.
#   Database JDBC URL [jdbc:mysql://mysql:3306/gotgotneed?...]
#   Started BackendApplicationKt in ... seconds
```

Since `spring.jpa.hibernate.ddl-auto: update` requires a live database
connection to run at startup, a failed DB connection means the Spring
context fails to start and the container exits — so a **running, healthy**
`gotgotneed-backend` container is itself strong evidence the connection
worked. For an explicit HTTP-level check (no extra dependencies needed —
`spring-boot-starter-actuator` isn't currently on the classpath):

```bash
curl -i http://localhost:8080/api-docs      # or ${BACKEND_PORT} if customized
curl -i http://localhost:8080/swagger-ui/index.html
```

A `200 OK` from either confirms the Spring context (and therefore the
datasource) initialized successfully.

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
