# Architecture

## Bird's-eye view

```
                       ┌─────────────────────────┐
                       │        :shared           │
                       │  (Compose Multiplatform  │
                       │   UI + expect/actual     │
                       │   platform code)         │
                       └────────────┬─────────────┘
                                    │ implementation(project(":shared"))
        ┌───────────────┬──────────┼──────────┬───────────────┐
        │               │          │          │               │
  ┌─────▼─────┐   ┌─────▼─────┐   ▼    ┌──────▼─────┐   ┌─────▼─────┐
  │ androidApp │   │ desktopApp │ webApp│   iosApp    │   │  backend  │
  │  (Android) │   │   (JVM)    │(JS/  │ (Xcode, not │   │(Spring    │
  │            │   │            │ Wasm) │ a Gradle    │   │ Boot API) │
  │            │   │            │       │  module)    │   │           │
  └────────────┘   └────────────┘└──────┘└────────────┘   └───────────┘
```

`:backend` is **not** a dependency of `:shared` or any client — it's a
separate, independently deployable Gradle module that happens to live in
the same repo (see [`decisions.md`](./decisions.md) for why it's a
monorepo).

## Frontend

### `:shared` ([`shared/src`](../shared/src))

The Compose Multiplatform module. It targets `androidTarget`, `iosArm64`,
`iosSimulatorArm64`, `jvm`, `js`, and `wasmJs` (see
[`shared/build.gradle.kts`](../shared/build.gradle.kts)). Source sets:

- [`commonMain`](../shared/src/commonMain/kotlin/com/gotgotneed) — code
  shared by **every** target. Today this is the actual UI
  (`App.kt`, a Compose `@Composable` screen), a small `Greeting`/`sayHello`
  demo utility, and the `Platform` `expect` declaration.
- `androidMain` / `iosMain` / `jvmMain` / `jsMain` / `wasmJsMain` — the
  `actual` implementations of `Platform` for each target, plus any
  platform-specific code. Right now these are minimal (just the
  `getPlatform()` actuals) — this is where you'd add e.g. platform-specific
  storage, notifications, or HTTP client engine wiring as the app grows.

**Responsibility:** owns the UI (via Compose Multiplatform) and any business
logic that should behave identically across platforms. Client apps should
be thin shells around it.

### Platform apps

| Module | Target | Responsibility |
|---|---|---|
| [`androidApp`](../androidApp) | Android (AGP 9.0.1) | Hosts `:shared`'s `App()` composable inside a single `MainActivity`. Owns Android manifest, launcher icon, Android-specific packaging. |
| [`desktopApp`](../desktopApp) | JVM (Compose Desktop) | Hosts `App()` inside a native window (via `androidx.compose.ui.window.Window`). Owns desktop packaging config (`Dmg`/`Msi`/`Deb` targets). |
| [`webApp`](../webApp) | Kotlin/JS and Kotlin/Wasm (browser) | Hosts `App()` via `ComposeViewport`. Owns `index.html`/`styles.css` and the two browser build variants. |
| [`iosApp`](../iosApp) | iOS (Xcode project, **not** a Gradle module) | A thin SwiftUI shell (`iOSApp.swift` → `ContentView`) that embeds the `Shared.framework` produced by `:shared`'s `iosArm64`/`iosSimulatorArm64` targets. |

**Dependency direction:** every platform app depends on `:shared`; `:shared`
depends on nothing platform-specific in `commonMain` (only on Compose
Multiplatform libraries). This keeps logic flowing one way — UI/business
logic lives in `:shared`, and platform apps just supply the entry point and
platform glue. No platform app module depends on another platform app
module.

## Backend

### `:backend` ([`backend/src`](../backend/src))

A Spring Boot 4 (Kotlin) application (see
[`backend/build.gradle.kts`](../backend/build.gradle.kts) and
[`backend.md`](./backend.md) for details). Current dependencies indicate the
intended shape rather than a fully built-out API yet:

- `spring-boot-starter-webmvc` — for exposing a REST API (no controllers
  exist yet; [`BackendApplication.kt`](../backend/src/main/kotlin/com/gotgotneed/backend/BackendApplication.kt)
  is currently just the bootstrap `@SpringBootApplication` class).
- `spring-boot-starter-data-jpa` + `mysql-connector-j` — for persistence
  against MySQL via Spring Data JPA (no `@Entity`/`@Repository` classes
  exist yet either).
- `spring-boot-starter-security` — for authn/authz (not yet configured).
- `spring-boot-starter-validation` — for request validation (`jakarta.validation`).
- `spring-boot-starter-flyway` + `flyway-mysql` — present on the classpath
  for future schema migrations, currently **disabled**
  (`spring.flyway.enabled: false`) in favor of Hibernate `ddl-auto: update`
  for local development. See [`database.md`](./database.md).

**Responsibility (intended):** own persistence and business rules that must
be server-authoritative (e.g. anything involving trust, shared state across
users/devices, or data too large/sensitive to keep only on-device). It's a
normal layered Spring Boot app — controllers → services → repositories →
JPA entities — even though only the bootstrap class exists today.

## Communication between frontend and backend

**Current state: there is none yet.** No module in `:shared` or any
platform app makes an HTTP call, and `:backend` exposes no endpoints. This
is a scaffolded monorepo, not (yet) a wired-up client/server system.

**Recommended approach when this is built out**, consistent with the
Kotlin Multiplatform choice already made (see
[`decisions.md`](./decisions.md)):

- Add an HTTP client (e.g. Ktor Client) to `:shared`'s `commonMain`, with
  platform-specific engines supplied per target (`androidMain` → OkHttp/CIO,
  `iosMain` → Darwin, `jsMain`/`wasmJsMain` → Js, `jvmMain` → CIO/OkHttp).
- Define request/response DTOs once in `:shared` (or a future `:shared`
  sub-package) so both client and server-facing code agree on shapes;
  alternatively keep backend DTOs separate and accept some duplication if
  you want the backend to evolve independently of the client's release
  train.
- Point the client at `:backend`'s base URL via configuration (build-time
  constant or runtime setting per platform), analogous to how
  `application.yaml` externalizes the database connection today (see
  [`database.md`](./database.md)).

## Offline-first approach

**Current state: not implemented.** There is no local persistence layer in
`:shared` today (no database, no cache, no sync queue).

**Recommended approach**, to keep in mind so early decisions don't preclude
it later:

1. Treat the backend as the source of truth, but never block the UI on a
   network round trip. Compose screens should render from local state
   first.
2. Add a local data layer in `:shared`'s `commonMain` (e.g. SQLDelight,
   which supports all of this project's KMP targets) so each platform
   persists the same way.
3. Writes go to local storage immediately and are queued for sync to
   `:backend`; reads come from local storage and are refreshed from the
   network in the background.
4. Because `:shared` already centralizes UI and platform `expect`/`actual`
   code, the sync/queue logic belongs there too, so all four client
   platforms get it for free instead of reimplementing it per platform.

This section will need to be rewritten to describe the *actual*
implementation once one exists — until then, treat it as a design
constraint to keep in mind, not a description of current behavior.
