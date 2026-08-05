# Troubleshooting

## Gradle daemon crashes due to low RAM

**Symptom:** builds die with `Gradle build daemon disappeared unexpectedly`,
`OutOfMemoryError`, or the daemon becomes unresponsive — more likely on this
project because a single Gradle daemon now builds the Android/KMP/JS/Wasm
client modules **and** the Spring Boot backend together (see the comment in
[`gradle.properties`](../gradle.properties)).

**Fix:**

```bash
./gradlew --stop      # kill any stuck/oversized daemons
```

If it recurs, the daemon heap is already raised once in
[`gradle.properties`](../gradle.properties):

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

If your machine has less than ~6-8GB free RAM, this is a likely cause —
close other memory-heavy applications (Android Studio + emulator + a
browser + Docker Desktop all running at once is a common culprit), or
lower `-Xmx` if your machine can't sustain 4GB for Gradle alone. Building
only the module you need (e.g. `./gradlew :backend:build` instead of
`./gradlew build`) also avoids configuring/loading the entire project
graph.

## iOS simulator cannot run on Linux

**Symptom:** a build/task graph calculation prints a warning like:

```
⚠️ Native task 'iosSimulatorArm64Test' is disabled
Task 'iosSimulatorArm64Test' for target 'ios_simulator_arm64' cannot run on the current host (linux-x86_64).
Reason: simulator tests require macOS
```

**Why:** Kotlin/Native's iOS targets (`iosArm64`, `iosSimulatorArm64` in
[`shared/build.gradle.kts`](../shared/build.gradle.kts)) can only be
compiled/run/tested on macOS, because they require Apple's toolchain
(Xcode, the iOS SDK, the simulator runtime) which doesn't exist for Linux.
This project's primary dev environment is Ubuntu, so this is expected and
not a bug.

**Fix / what to do instead:**
- To silence the warning (it's informational, not a failure), add to
  [`gradle.properties`](../gradle.properties):
  ```properties
  kotlin.native.ignoreDisabledTargets=true
  ```
- Gradle can still **compile** the iOS klibs/framework metadata on Linux in
  many cases, but you cannot **run** the iOS app or its simulator tests
  without macOS.
- To actually build/run/test `iosApp`, use a Mac: open
  [`iosApp/iosApp.xcodeproj`](../iosApp/iosApp.xcodeproj) in Xcode, which
  invokes the Kotlin/Native build for you (see
  [`frontend.md`](./frontend.md)).

## Compose resource configuration

**Symptom:** `Unresolved reference: Res` or generated resource classes
(e.g. `com.gotgotneed.generated.resources.Res`) not found after adding a
new resource.

**Why:** `:shared` configures a custom generated-resources package:

```kotlin
// shared/build.gradle.kts
compose.resources {
    packageOfResClass = "com.gotgotneed.generated.resources"
}
```

Compose Multiplatform generates the `Res` accessor object (and per-resource
references, e.g. `Res.drawable.compose_multiplatform`) from files under
`shared/src/commonMain/composeResources/` at build time — it isn't
hand-written, so IDE autocomplete can lag until a build has run at least
once.

**Fix:**
- Run any build/sync task once after adding new resources so the generator
  runs, e.g. `./gradlew :shared:build` (or trigger a Gradle sync in your
  IDE).
- Double-check new resource files live under the correct
  `composeResources/<type>/` subdirectory (e.g. `drawable/`, `values/`) and
  use valid Compose resource file-naming rules (lowercase, underscores, no
  leading digits) — invalid names silently fail to generate a reference.
- If the generated class still isn't picked up, `./gradlew clean` followed
  by a rebuild forces regeneration (see the general cache-cleanup command
  below).

## Node/Yarn issues

**Symptom:** the first build of `:webApp` or `:shared`'s `js`/`wasmJs`
targets hangs or fails while "resolving"/"setting up" Node.js or Yarn, or
fails behind a corporate proxy/firewall.

**Why:** this project does **not** require a manually installed Node/Yarn
(see [`setup.md`](./setup.md)) — the Kotlin Gradle plugin downloads its own
Node.js and Yarn distributions automatically the first time a `js`/`wasmJs`
task runs, and manages dependencies via the committed
`kotlin-js-store/yarn.lock` and `kotlin-js-store/wasm/yarn.lock` files.
That first download can fail or hang if network access to Yarn's/Node's
distribution servers is blocked.

**Fix:**
- Confirm you have network access to `nodejs.org`/the Yarn registry (or
  whatever mirror your network requires); retry once connectivity is
  confirmed.
- If you're behind a proxy, configure Gradle's proxy settings
  (`~/.gradle/gradle.properties`: `systemProp.https.proxyHost`/`proxyPort`,
  etc.) so the Kotlin Gradle plugin's downloader can reach the network
  through it.
- If lockfile resolution complains about a mismatch, don't hand-edit
  `kotlin-js-store/yarn.lock` — let Gradle regenerate it by re-running the
  failing task; if it's persistently stuck, delete the relevant lockfile
  and re-run so it's regenerated from `build.gradle.kts`'s declared
  dependencies.
- As a last resort, install Node/Yarn yourself and point the Kotlin Gradle
  plugin at them instead of letting it auto-provision — only worth doing if
  automatic download is fundamentally blocked on your network.

## Common build fixes

| Symptom | Try |
|---|---|
| Weird/incremental-compilation-looking errors that don't match your actual code | `./gradlew clean` then rebuild. |
| Dependency version seems stuck on an old value after bumping it in `libs.versions.toml` | `./gradlew <task> --refresh-dependencies`. |
| Gradle can't find/resolve a JDK toolchain (e.g. for `:backend`'s Java 17 requirement) | Confirm a compatible JDK is installed/discoverable: `./gradlew -q javaToolchains`. Gradle can also auto-download toolchains via the `org.gradle.toolchains.foojay-resolver-convention` plugin (already applied in [`settings.gradle.kts`](../settings.gradle.kts)) if your network allows it. |
| Everything seems broken after switching branches | `./gradlew --stop` (stale daemon state can persist across branch switches with different Gradle/plugin configs), then rebuild. |
| `:backend:bootRun` fails with a MySQL connection error | MySQL likely isn't running/healthy yet — see the next entry and [`database.md`](./database.md). |
| `docker compose ps` never shows `healthy` for `gotgotneed-mysql` | Check `docker compose logs mysql` for the actual startup error; common causes are a corrupted data volume (`docker compose down -v` to reset, **destroys local data**) or a port `3306` conflict with an already-running MySQL on the host (change `MYSQL_PORT` in `.env`). |
| `docker compose up` fails with a permissions/socket error | Docker daemon isn't running, or your user isn't in the `docker` group — see [`setup.md`](./setup.md)'s environment setup step. |
| Android Studio reports the project is incompatible / won't sync | Confirm you're on Android Studio **Otter 3 Feature Drop (2025.2.3) or newer** — this project uses AGP 9.0.1, and older Android Studio versions (and IntelliJ IDEA, as of 2026.1) don't support it. See [`setup.md`](./setup.md). |

## See also

- [`commands.md`](./commands.md) — the exact commands referenced above.
- [`setup.md`](./setup.md) — required software/versions this project
  assumes.
