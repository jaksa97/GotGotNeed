This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

* [/backend](./backend/src) is a Spring Boot (Kotlin) server module, built with the same root Gradle
  wrapper as the client modules above.
  - Run it: `./gradlew :backend:bootRun`
  - Test it: `./gradlew :backend:test`

## Running backend with Docker

The backend can also run fully containerized, alongside MySQL, via
[`docker-compose.yml`](./docker-compose.yml). Compose starts MySQL first, waits
until it reports **healthy** (accepts connections), and only then starts the
backend container — see [`backend/Dockerfile`](./backend/Dockerfile) and
[`docs/database.md`](./docs/database.md) for details.

```bash
# 1. Copy the environment template once (git-ignored; safe to edit locally)
cp .env.example .env

# 2. Build the backend image and start both containers
docker compose up --build

# Or in the background:
docker compose up --build -d
```

Other useful commands:

```bash
# Stop and remove the containers (the MySQL data volume is kept)
docker compose down

# Follow just the backend's logs
docker compose logs -f backend

# Check container status/health
docker compose ps
```

**Accessing the backend** once it's up:

- API base URL: `http://localhost:8080` (or `${BACKEND_PORT}` from `.env` if customized)
- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI docs: `http://localhost:8080/api-docs`

This is independent from local development: `./gradlew :backend:bootRun` still
works exactly as before (see above) — Docker is only needed if you specifically
want the backend running in a container.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).