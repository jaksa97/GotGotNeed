# Machine Setup

Follow this once per machine. It's written for **Ubuntu** (the primary dev
environment for this project), with notes for macOS/Windows where they
matter (mainly for iOS, which requires macOS + Xcode regardless).

## 1. Required software

| Tool | Version needed | Why |
|---|---|---|
| **JDK** | 21 (the Gradle daemon toolchain, see [`gradle/gradle-daemon-jvm.properties`](../gradle/gradle-daemon-jvm.properties), pins **Azul Zulu 21**) | Runs Gradle itself and the `:backend` module at build/run time. |
| **Git** | any recent 2.x | Version control. |
| **Docker Desktop** (or Docker Engine + Compose plugin on Ubuntu) | Compose v2 (`docker compose ...`) | Runs the local MySQL database (see [`database.md`](./database.md)). |
| **Android Studio** | **Otter 3 Feature Drop (2025.2.3) or newer** | Required to build/run `:androidApp` — the project uses **AGP 9.0.1**, which needs Android Studio Otter 3 or later. |
| **IntelliJ IDEA** | Optional, latest stable | IntelliJ IDEA does **not** support AGP 9.0 projects as of 2026.1, so it can't be used for the `:androidApp`/`:shared` Android target. It works fine for JVM-only work: `:backend`, `:desktopApp`, and the non-Android parts of `:shared`. Use Android Studio as the primary IDE for this repo. |
| **Xcode** | Latest stable (macOS only) | Required to build/run [`iosApp`](../iosApp) — there is no way to build/run the iOS target on Linux (see [`troubleshooting.md`](./troubleshooting.md)). |
| **Node.js / Yarn** | Not required to install manually | The Kotlin Gradle plugin auto-provisions its own Node.js and Yarn the first time you build the `js`/`wasmJs` targets (`:webApp`, `:shared`). You'll see them appear under Gradle's cache and `kotlin-js-store/`/`kotlin-js-store/wasm/`'s `yarn.lock` files, which are already committed. Only install Node/Yarn yourself if you need to run tooling outside Gradle, or if you're on a network that blocks Gradle's automatic download (see [`troubleshooting.md`](./troubleshooting.md)). |

### Verify what you have

```bash
java -version              # expect 21.x
git --version
docker --version
docker compose version     # must be the v2 "docker compose" plugin, not the old "docker-compose"
```

> **Note on JDK versions in this repo:** the Gradle daemon toolchain is JDK
> 21, but a few subprojects still pin older per-module targets found in
> their own `build.gradle.kts` (`:backend` → toolchain 17, `:androidApp` →
> JVM 11, `:shared`'s Android source set → JVM 17). Gradle will
> automatically provision/select whichever toolchain each module asks for
> as long as JDK 21 (or a matching one) is available and toolchain
> auto-detection/download is enabled — you generally don't need to install
> multiple JDKs by hand. See [`troubleshooting.md`](./troubleshooting.md) if
> a module fails to resolve a toolchain.

## 2. Environment setup

1. Install the tools above.
2. Make sure Docker is running and your user can run `docker` without `sudo`
   (on Ubuntu: `sudo usermod -aG docker $USER`, then log out/in).
3. If you'll build the Android target, open Android Studio once and let it
   install the Android SDK — this generates your local `local.properties`
   (already gitignored).

## 3. Clone the repository

```bash
git clone <repository-url> GotGotNeed
cd GotGotNeed
```

(Replace `<repository-url>` with the actual Git remote URL for this repo.)

## 4. Configure local environment variables

Copy the Docker/database environment template and adjust it if you need
non-default values (the defaults already match what the backend expects,
see [`database.md`](./database.md)):

```bash
cp .env.example .env
```

## 5. Gradle setup

You don't need to install Gradle — the repo ships the Gradle **wrapper**
(`./gradlew`), pinned to **Gradle 9.1.0** in
[`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties).
Always use `./gradlew` (or `gradlew.bat` on Windows), never a locally
installed `gradle`, so everyone builds with the exact same version.

First time, let the wrapper download itself and warm up the dependency
cache:

```bash
./gradlew --version
```

This also confirms Gradle can resolve the JDK 21 toolchain declared in
[`gradle/gradle-daemon-jvm.properties`](../gradle/gradle-daemon-jvm.properties).

## 6. Start the local database

```bash
docker compose up -d
docker compose ps   # wait until STATUS shows "healthy"
```

See [`database.md`](./database.md) for details on what this starts.

## 7. First build

Build everything the JVM/Gradle side can build (Android, Desktop, Web,
backend — excludes iOS, which is built from Xcode):

```bash
./gradlew build
```

Or just compile without running tests, if you want a faster first check:

```bash
./gradlew assemble
```

## 8. Run something to confirm it all works

```bash
# Backend (needs the database from step 6 running)
./gradlew :backend:bootRun

# Desktop app (no emulator/simulator needed)
./gradlew :desktopApp:run
```

If both come up without errors, your machine is set up correctly. For every
other run target (Android, Web, iOS) see [`frontend.md`](./frontend.md).
