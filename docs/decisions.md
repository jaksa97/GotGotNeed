# Architecture Decision Records (ADRs)

Each record below documents a decision already reflected in the repository
today, the reasoning behind it, and what alternatives exist. These are
living documents in spirit — if a decision is later reversed, add a new ADR
that supersedes it rather than editing history away.

---

## ADR-001: Kotlin Multiplatform instead of separate native apps

**Decision:** Build Android, iOS, Desktop, and Web clients from one Kotlin
Multiplatform codebase (`:shared`), rather than four independent
codebases (Kotlin/Android, Swift/iOS, and separate desktop/web stacks).

**Reason:** A single team maintaining one repo benefits from sharing UI and
business logic across platforms instead of reimplementing (and
re-testing, and re-fixing bugs in) the same features four times. Kotlin
Multiplatform also lets platform-specific code coexist with shared code in
the same module via `expect`/`actual` (see [`frontend.md`](./frontend.md)),
so platform APIs are still fully accessible where needed.

**Alternatives considered:**
- *Separate native apps per platform* (Kotlin/Jetpack Compose for Android,
  Swift/SwiftUI for iOS, Electron or a JS framework for web, a separate
  desktop stack) — maximum platform idiomaticity, but multiplies
  implementation and maintenance effort by the number of platforms, and
  makes it easy for features/behavior to drift between them.
- *A cross-platform framework outside the Kotlin ecosystem* (Flutter,
  React Native) — also gets one shared codebase, but forfeits full-fidelity
  interop with existing/future JVM code (e.g. sharing code with the Kotlin
  backend) and Kotlin-specific tooling.

---

## ADR-002: Compose Multiplatform for UI

**Decision:** Use Compose Multiplatform (JetBrains' cross-platform
evolution of Jetpack Compose) as the UI toolkit across Android, iOS,
Desktop, and Web, rather than building separate native UIs per platform.

**Reason:** Once Kotlin Multiplatform (ADR-001) is chosen for logic
sharing, Compose Multiplatform lets UI be shared too — a single
`@Composable fun App()` in `:shared` (see
[`shared/src/commonMain/kotlin/com/gotgotneed/App.kt`](../shared/src/commonMain/kotlin/com/gotgotneed/App.kt))
renders on every target. It's maintained by JetBrains alongside Kotlin
itself, which keeps it well-aligned with new Kotlin/KMP releases.

**Alternatives considered:**
- *Native UI per platform on top of shared KMP business logic* (Jetpack
  Compose on Android, SwiftUI on iOS, a separate web UI) — maximizes
  platform look-and-feel fidelity, at the cost of writing and maintaining
  UI four times, which defeats a major part of the reason for choosing KMP
  in the first place.
- *A different cross-platform UI toolkit* (e.g. Flutter's own renderer) —
  would mean abandoning Kotlin/JVM interop for UI, and isn't an option
  once staying within the Kotlin ecosystem (ADR-001) is decided.

---

## ADR-003: Spring Boot for the backend

**Decision:** Build the server (`:backend`) with Spring Boot (Kotlin),
rather than a different JVM framework or a non-JVM stack.

**Reason:** Spring Boot is the dominant, most mature JVM framework for REST
APIs with a huge ecosystem (Spring Data JPA, Spring Security, Spring
Validation — all already dependencies, see
[`backend.md`](./backend.md)). Building the backend in Kotlin on the JVM
also means it can, in principle, share tooling and even code (DTOs, pure
logic) with `:shared`'s `jvmMain` if that ever becomes useful, since
they're both Kotlin/JVM.

**Alternatives considered:**
- *Ktor* — a lighter-weight, more Kotlin-idiomatic HTTP framework, also
  from JetBrains. Would reduce boilerplate/magic compared to Spring, but
  loses Spring's batteries-included ecosystem (Spring Data JPA, Spring
  Security) that this project already depends on.
- *A non-JVM backend* (Node.js, Go, etc.) — would abandon the ability to
  share any Kotlin code/tooling with the client and split the team's
  language/toolchain across ecosystems for no clear benefit here.

---

## ADR-004: MySQL for the database

**Decision:** Use MySQL (8.x) as the relational database, both locally
(via Docker, see [`database.md`](./database.md)) and presumably in
deployed environments.

**Reason:** MySQL is a widely-used, well-understood relational database
with first-class Spring Data JPA support and a mature JDBC driver
(`mysql-connector-j`, already a dependency). It's a safe, boring choice for
a monorepo whose priority is shipping features across four client
platforms, not database experimentation.

**Alternatives considered:**
- *PostgreSQL* — arguably has richer feature support (better JSON support,
  more advanced indexing) and is also extremely well supported by Spring
  Data JPA; a reasonable alternative that wasn't chosen here, likely for
  team familiarity or hosting-availability reasons rather than a technical
  MySQL-specific requirement.
- *An embedded/file database* (H2, SQLite) — simpler for local dev, but
  would mean developing against different database semantics than
  production and losing confidence in dialect-specific behavior; MySQL is
  used consistently instead via Docker (see ADR-006).

---

## ADR-005: JPA instead of Exposed

**Decision:** Use Spring Data JPA (Hibernate) for persistence, not
JetBrains' Exposed (a Kotlin-native SQL framework).

**Reason:** Given Spring Boot is already the backend framework (ADR-003),
Spring Data JPA is the default, most integrated persistence option —
repositories, transactions, and entity lifecycle all plug directly into
Spring's existing infrastructure with minimal extra wiring, and it's what
the vast majority of Spring Boot documentation/tooling assumes.

**Alternatives considered:**
- *Exposed* — a more Kotlin-idiomatic, type-safe SQL DSL from JetBrains.
  Appealing for a Kotlin-first codebase, and avoids some JPA/Hibernate
  pain points (lazy-loading surprises, N+1 queries, proxy/`open class`
  requirements — note `:backend` already needs the `kotlin-jpa` compiler
  plugin specifically to work around the latter, see
  [`backend.md`](./backend.md)). Not chosen here, likely to stay on the
  more battle-tested, Spring-native path rather than combine two
  less-common choices (Spring Boot + Exposed) at once.
- *jOOQ* — another type-safe SQL alternative with less "magic" than JPA,
  but with a paid license for some databases and less default Spring Boot
  integration than Spring Data JPA.

---

## ADR-006: Docker for the local database

**Decision:** Run MySQL locally via Docker Compose
([`docker-compose.yml`](../docker-compose.yml)) instead of requiring a
natively installed MySQL server per developer machine.

**Reason:** Docker Compose gives every developer (and CI) an identical,
disposable, versioned (`mysql:8.4`) database with one command
(`docker compose up -d`), with credentials/ports centralized in
`.env`/`.env.example` (see [`database.md`](./database.md)) instead of each
machine's MySQL being configured slightly differently. It also means
resetting to a clean database is a single command
(`docker compose down -v && docker compose up -d`) rather than manually
dropping/recreating databases.

**Alternatives considered:**
- *Natively installed MySQL per machine* — works, but versions and
  configuration inevitably drift between developers' machines over time,
  and onboarding a new machine takes longer.
- *A shared remote dev database* — removes the "identical environment"
  benefit (everyone shares state and can break each other's data) and adds
  a network dependency for local development; not appropriate for a local
  dev loop.
- *Testcontainers-only (spun up per test run, no persistent local
  instance)* — good for tests, but not a substitute for a database you can
  manually poke at with a MySQL shell while developing (see
  [`commands.md`](./commands.md)); the two approaches are complementary,
  not mutually exclusive, and this repo hasn't adopted Testcontainers yet.

---

## ADR-007: Monorepo structure

**Decision:** Keep the frontend clients (`androidApp`, `desktopApp`,
`webApp`, `iosApp`, `shared`) and the backend (`backend`) in a single
Gradle multi-module repository (see
[`settings.gradle.kts`](../settings.gradle.kts)), rather than splitting the
backend into its own repository.

**Reason:** For a small/early-stage project built by one team, a monorepo
keeps client and server changes that depend on each other (e.g. a new API
contract needed by a new client feature) atomic and reviewable in a single
PR, and avoids cross-repo versioning overhead. `:backend` is built with the
same root Gradle wrapper as the client modules (see the root
[`README.md`](../README.md)) even though it's functionally independent and
deployed separately.

**Alternatives considered:**
- *Polyrepo* (separate repository for `:backend`) — better isolation
  (independent CI, access control, release cadence) and prevents backend
  changes from ever accidentally depending on client-module Gradle
  configuration or vice versa. Usually adopted once a backend team is
  distinct from the client team(s), or once the backend needs a
  significantly different release cadence/CI pipeline — not a clear win at
  this project's current size.
- *Multiple repos per client platform* — would fragment `:shared` code
  reuse entirely, defeating ADR-001.
