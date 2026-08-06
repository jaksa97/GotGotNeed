# Backend (`:backend`)

A Spring Boot 4 application written in Kotlin, built as the `:backend`
Gradle module (see [`settings.gradle.kts`](../settings.gradle.kts)).

## Spring Boot setup

Key versions (from [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)):

- **Spring Boot:** `4.1.0`
- **Spring Dependency Management plugin:** `1.1.7` (manages transitive
  Spring/Flyway/etc. versions so they don't need pinning individually)
- **Kotlin:** `2.4.10`
- **Java toolchain for this module:** `17` (see
  [`backend/build.gradle.kts`](../backend/build.gradle.kts) — this is
  narrower than the JDK 21 the Gradle daemon itself uses; Gradle resolves
  it automatically via toolchain support)

Plugins applied to `:backend`:

```kotlin
// backend/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)               // opens classes Spring needs to proxy
    alias(libs.plugins.kotlinJpa)                  // opens @Entity/@MappedSuperclass/@Embeddable classes
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}
```

The `kotlin-spring` and `kotlin-jpa` compiler plugins exist because Kotlin
classes are `final` by default, which breaks CGLIB proxying (Spring AOP)
and lazy-loading proxies (Hibernate) unless the relevant classes are
`open`. These plugins auto-open the classes annotated in `allOpen` in the
same file:

```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

## How to run the backend

**Option 1 — local Gradle (fastest inner loop):**

```bash
# 1. Make sure MySQL is up (see database.md)
docker compose up -d mysql
docker compose ps   # wait for "healthy"

# 2. Run the backend
./gradlew :backend:bootRun
```

It starts an embedded Tomcat server on port `8080` by default (Spring
Boot's standard default — not overridden in `application.yaml`).

**Option 2 — fully containerized (backend + MySQL both in Docker):**

```bash
docker compose up --build
```

See the root [`README.md`](../README.md#running-backend-with-docker) for the
full command reference, and [`backend/Dockerfile`](../backend/Dockerfile) for
how the image is built. Compose is configured so the backend container only
starts once MySQL's healthcheck passes (`depends_on: condition: service_healthy`
in [`docker-compose.yml`](../docker-compose.yml)).

Run the test suite:

```bash
./gradlew :backend:test
```

## Configuration files

- [`backend/src/main/resources/application.yaml`](../backend/src/main/resources/application.yaml) —
  the only configuration file today. Sets the application name, datasource
  connection (env-var driven, see [`database.md`](./database.md)), JPA/
  Hibernate behavior (`ddl-auto: update`, SQL logging), and disables
  Flyway for now. There are no environment-specific profile files
  (`application-dev.yaml`, `application-prod.yaml`, etc.) yet — if/when
  deployment environments diverge (e.g. different logging levels, a real
  production datasource, Flyway turned on), that's the natural next step
  using Spring profiles.
- [`backend/Dockerfile`](../backend/Dockerfile) — multi-stage build (Gradle
  wrapper on a JDK 17 image to build the boot jar, then a minimal JRE 17
  image to run it as a non-root user). Its build context is the **repo
  root**, not `backend/`, because this is a single Gradle multi-module
  monorepo and the wrapper needs `settings.gradle.kts` and every module's
  `build.gradle.kts` to resolve the project graph, even though only
  `:backend` actually gets compiled/packaged. See the file's header comment
  for details. The backend can still run directly via Gradle (`bootRun`) or
  as an executable jar (`./gradlew :backend:bootJar`, output under
  `backend/build/libs/`) without Docker at all.

## Dependencies

From [`backend/build.gradle.kts`](../backend/build.gradle.kts) /
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml):

| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-data-jpa` | implementation | Spring Data JPA + Hibernate for persistence. |
| `spring-boot-starter-webmvc` | implementation | REST controllers on Spring MVC / embedded Tomcat. |
| `spring-boot-starter-security` | implementation | Authentication/authorization (not yet configured beyond the dependency). |
| `spring-boot-starter-validation` | implementation | Bean Validation (`jakarta.validation`) for request DTOs. |
| `spring-boot-starter-flyway` + `flyway-mysql` | implementation | Schema migrations — currently disabled, see [`database.md`](./database.md). |
| `kotlin-reflect` | implementation | Required by Spring/Jackson for Kotlin reflection (e.g. data class introspection). |
| `jackson-module-kotlin` | implementation | Correct JSON (de)serialization of Kotlin data classes/defaults. |
| `mysql-connector-j` | runtimeOnly | JDBC driver for MySQL. |
| `spring-boot-devtools` | developmentOnly | Auto-restart/live reload during local development; excluded from production packaging. |
| `spring-boot-starter-data-jpa-test`, `-flyway-test`, `-security-test`, `-validation-test`, `-webmvc-test` | testImplementation | Test-scoped counterparts of the above starters. |
| `kotlin-test-junit5` | testImplementation | Kotlin-idiomatic assertions on top of JUnit 5. |
| `junit-platform-launcher` | testRuntimeOnly | Required to actually execute JUnit 5 tests under Gradle. |

All versions except `kotlin-reflect`/`kotlin-test-junit5` (pinned to the
Kotlin version) are deliberately **not** pinned in
`libs.versions.toml` — they're managed by the Spring Boot BOM via the
`io.spring.dependency-management` plugin, which is the standard/recommended
way to keep a Spring Boot project's dependency versions mutually
compatible.

## Package structure

Current, minimal structure:

```
backend/src/main/kotlin/com/gotgotneed/backend/
└── BackendApplication.kt      # @SpringBootApplication + main()

backend/src/test/kotlin/com/gotgotneed/backend/
└── BackendApplicationTests.kt # @SpringBootTest smoke test (context loads)
```

**Recommended package layout as real functionality gets added** (standard
layered Spring Boot structure, package-by-feature is also reasonable once
the API surface grows — pick one and stay consistent):

```
com.gotgotneed.backend/
├── BackendApplication.kt
├── config/            # @Configuration classes (security, CORS, beans)
├── web/                # @RestController classes + request/response DTOs
├── service/            # business logic, transaction boundaries (@Service)
├── repository/         # Spring Data JPA repositories (@Repository)
├── domain/             # @Entity classes
└── exception/          # @ControllerAdvice / custom exceptions
```

None of this exists yet — it's a recommendation for when controllers,
services, and entities are actually added, not a description of current
code.

## See also

- [`database.md`](./database.md) — datasource/JPA/Flyway configuration in
  depth.
- [`architecture.md`](./architecture.md) — how `:backend` fits with the
  rest of the monorepo.
- [`commands.md`](./commands.md) — every Gradle command for this module.
