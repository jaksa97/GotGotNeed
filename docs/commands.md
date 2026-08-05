# Command Reference

A flat, copy-pasteable reference. Every command below is grounded in what
actually exists in this repo (module names, `docker-compose.yml`,
`.env`/`.env.example`, `application.yaml`) — nothing here is guessed.

Run all Gradle/Docker commands from the repository root unless noted
otherwise.

## Git commands

| Command | Purpose | When to use it |
|---|---|---|
| `git clone <url> GotGotNeed` | Copies the remote repository to your machine. | Once, when first setting up the project (see [`setup.md`](./setup.md)). |
| `git status` | Shows which files are staged, modified, or untracked. | Before committing, or any time you're unsure what state your working tree is in. |
| `git branch` | Lists local branches; `git branch <name>` creates one. | Starting new work — always branch off `main`/`master` instead of committing directly to it. |
| `git checkout -b <name>` | Creates and switches to a new branch in one step. | The usual way to start a feature/fix branch. |
| `git add <path>` / `git add -A` | Stages changes for commit. | Before `git commit`, once you know what should go in the commit. |
| `git commit -m "message"` | Records a commit from staged changes. | After `git add`, once a logical unit of work is ready. |
| `git pull` | Fetches and merges/rebases remote changes into your current branch. | Before starting new work, and before pushing, to reduce conflicts. |
| `git push` | Uploads local commits to the remote. | After committing, to share work or open a pull request. |
| `git push -u origin <branch>` | Pushes a new branch and sets it to track the remote branch. | The first push of a newly created branch. |

## Gradle commands

All modules: `:androidApp`, `:desktopApp`, `:webApp`, `:shared`, `:backend`
(see [`settings.gradle.kts`](../settings.gradle.kts)). This repo always uses
the wrapper (`./gradlew`), pinned to Gradle 9.1.0 — never a global `gradle`
install.

| Command | Purpose | When to use it |
|---|---|---|
| `./gradlew build` | Compiles, tests, and assembles every applicable module (excludes iOS, which Xcode builds). | Before pushing, or in CI, to confirm the whole JVM/KMP side is healthy. |
| `./gradlew assemble` | Compiles and packages, **skipping tests**. | Quick sanity check on compilation without waiting for the test suite. |
| `./gradlew clean` | Deletes all `build/` output directories. | When you suspect stale build outputs/caches are causing incorrect behavior. |
| `./gradlew :shared:build` | Builds just the `:shared` KMP module (all its targets: Android, iOS, JVM, JS, Wasm). | After changing shared code, to check every target still compiles. |
| `./gradlew :androidApp:assembleDebug` | Builds a debug APK for the Android app. | Producing an installable Android build without Android Studio's UI. |
| `./gradlew :backend:build` | Compiles and tests just the Spring Boot backend. | Iterating on backend code without rebuilding the client modules. |
| `./gradlew :backend:bootRun` | Starts the Spring Boot backend (embedded Tomcat, port 8080 by default). | Running the API locally — requires MySQL to be up first, see [`database.md`](./database.md). |
| `./gradlew :backend:test` | Runs backend unit/integration tests. | Before committing backend changes. |
| `./gradlew :desktopApp:run` | Runs the Compose Multiplatform desktop app. | Fastest way to manually check UI changes — no emulator/simulator needed. |
| `./gradlew :desktopApp:hotRun --auto` | Runs the desktop app with hot reload on file changes. | Iterating on UI quickly. |
| `./gradlew :webApp:wasmJsBrowserDevelopmentRun` | Serves the web app compiled to Wasm and opens it in a browser. | Web development on modern browsers (faster build/reload than JS). |
| `./gradlew :webApp:jsBrowserDevelopmentRun` | Serves the web app compiled to JS. | Web development when you need older-browser support. |
| `./gradlew --stop` | Stops all running Gradle daemons. | Daemon acting up / stuck / consuming too much memory (see [`troubleshooting.md`](./troubleshooting.md)). |
| `./gradlew <task> --refresh-dependencies` | Re-resolves all dependencies, ignoring the cache. | Dependency resolution looks stale/corrupted after a version bump. |

For iOS there is no Gradle task — see [`frontend.md`](./frontend.md), it's
built and run from Xcode against `iosApp/iosApp.xcodeproj`.

## Docker commands

These operate on the `docker-compose.yml` at the repo root, which defines
the `gotgotneed-mysql` service.

| Command | Purpose | When to use it |
|---|---|---|
| `docker compose up -d` | Starts (and creates, if needed) the MySQL container in the background. | Beginning of a work session, before running the backend. |
| `docker compose ps` | Shows container status, including the health check state. | Confirming MySQL is up and `healthy` before starting the backend. |
| `docker compose stop` | Stops the container without removing it (data volume untouched). | Pausing work without losing the container's config/state. |
| `docker compose down` | Stops and removes the container (the named volume `gotgotneed-mysql-data` is kept). | End of a work session / freeing up resources, while keeping your data. |
| `docker compose down -v` | Stops and removes the container **and** deletes the data volume. | You want a completely fresh database (e.g. to re-test init scripts under `docker/mysql/init/`). Destroys all local data. |
| `docker compose up -d --build` | Rebuilds images (if any are custom-built) then starts. | After changing a custom Dockerfile — not currently used here since `mysql:8.4` is a stock image, but kept for future services. |
| `docker compose logs -f mysql` | Streams the MySQL container's logs. | Diagnosing startup failures or query issues. |
| `docker compose exec mysql bash` | Opens a shell inside the running MySQL container. | Poking around the container's filesystem/config. |
| `docker exec -it gotgotneed-mysql mysql -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE` | Opens a MySQL shell inside the container using the app credentials. | Quickest way to inspect/query the database from the terminal (see MySQL commands below). |
| `docker ps` | Lists all running containers (not just this project's). | Checking whether `gotgotneed-mysql` (or anything else) is already running. |

## MySQL commands

Run these after connecting with:

```bash
docker exec -it gotgotneed-mysql mysql -ugotgotneed -pgotgotneed gotgotneed
```

(credentials come from `.env` — defaults are `gotgotneed` / `gotgotneed` /
database `gotgotneed`, see [`database.md`](./database.md)).

| Command | Purpose | When to use it |
|---|---|---|
| `SHOW DATABASES;` | Lists all databases visible to the current user. | Confirming the `gotgotneed` database exists. |
| `USE gotgotneed;` | Switches the active database for the session. | If you connected without specifying a database on the command line. |
| `SHOW TABLES;` | Lists tables in the current database. | Checking what Hibernate (`ddl-auto: update`) has created so far. |
| `DESCRIBE <table>;` | Shows a table's columns, types, and keys. | Inspecting the schema Hibernate generated for an entity. |
| `SELECT * FROM <table>;` | Reads rows from a table. | Verifying data written by the backend during manual testing. |
| `SHOW CREATE TABLE <table>;` | Shows the full `CREATE TABLE` statement, including indexes/constraints. | Debugging schema issues or writing a future Flyway migration from the current state. |
| `SHOW PROCESSLIST;` | Lists active connections/queries. | Debugging connection pool exhaustion or a hung query. |
| `exit` | Leaves the MySQL shell. | Done inspecting the database. |

## Troubleshooting commands

| Command | Purpose | When to use it |
|---|---|---|
| `./gradlew --status` | Lists running Gradle daemons and their status. | Checking whether a daemon is stuck before deciding to stop it. |
| `./gradlew --stop` | Stops all Gradle daemons. | Daemon crashed, is stuck, or is consuming too much RAM (see [`troubleshooting.md`](./troubleshooting.md)). |
| `./gradlew clean` | Deletes `build/` directories across modules. | Stale build output is suspected of causing an incorrect build. |
| `rm -rf ~/.gradle/caches/` | Wipes the global Gradle dependency/build cache. | Deep dependency-resolution corruption that `--refresh-dependencies` didn't fix. Next build will be slow while it re-downloads everything. |
| `java -version` | Prints the active `java` on your `PATH` and its version. | Confirming you have JDK 21 available before reporting a "wrong JDK" build error. |
| `./gradlew -q javaToolchains` | Lists JDK toolchains Gradle has detected/can provision. | Diagnosing "no compatible toolchain found" errors for `:backend` (17) or `:shared`'s Android source set (17). |
| `docker info` | Shows Docker daemon status and configuration. | Confirming Docker is actually running before `docker compose up` fails mysteriously. |
| `docker compose ps` | Shows container health (`starting` / `healthy` / `unhealthy`). | Confirming MySQL is actually ready before blaming the backend for connection errors. |
| `docker compose logs mysql` | Prints MySQL container logs. | The health check never turns healthy — read the actual startup error. |
