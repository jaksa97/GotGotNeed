# GotGotNeed Developer Documentation

This directory is the single source of truth for how the **GotGotNeed** monorepo
is set up, built, run, and why it's structured the way it is. It exists so
that setup steps, commands, and architectural reasoning don't live only in
someone's head (or a chat history) and have to be rediscovered every time.

Keep it up to date: if you change a Gradle module, add a dependency, change a
Docker/database setting, or make a real architectural decision, update the
relevant file in the same PR.

## How to use these docs

- New to the project? Start with [`setup.md`](./setup.md), then skim
  [`architecture.md`](./architecture.md).
- Forgot a command? Check [`commands.md`](./commands.md) — it's a flat
  reference, not a tutorial.
- Something broke? Check [`troubleshooting.md`](./troubleshooting.md) before
  asking around.
- Wondering *why* something is built a certain way? Check
  [`decisions.md`](./decisions.md).
- Working specifically on the server or the database? See
  [`backend.md`](./backend.md) / [`database.md`](./database.md).
- Working on any of the Compose Multiplatform clients? See
  [`frontend.md`](./frontend.md).

## Index

| File | Purpose |
|---|---|
| [`setup.md`](./setup.md) | One-time machine setup: required software, environment, cloning, first build. Read this first on a new machine. |
| [`commands.md`](./commands.md) | Copy-pasteable reference of every Git, Gradle, Docker, and MySQL command used day-to-day, with purpose and "when to use it". |
| [`architecture.md`](./architecture.md) | How the modules fit together: frontend module responsibilities, dependency direction, backend layering, and how (and whether) the client talks to the server today. |
| [`decisions.md`](./decisions.md) | Architecture Decision Records (ADRs) — the significant technical choices already reflected in the repo, why they were made, and what alternatives were considered. |
| [`backend.md`](./backend.md) | Spring Boot backend: how to run it, its configuration files, its dependencies, and a recommended package layout as it grows. |
| [`database.md`](./database.md) | The local MySQL setup: Docker Compose, environment variables, Spring datasource/JPA configuration, and the future migration strategy. |
| [`frontend.md`](./frontend.md) | Compose Multiplatform setup: what's shared vs. platform-specific, and how to run each target (Android, iOS, Desktop, Web). |
| [`troubleshooting.md`](./troubleshooting.md) | Known issues and their fixes: Gradle daemon problems, iOS-on-Linux limitations, Compose resources, Node/Yarn quirks, generic build fixes. |

## Scope and accuracy

Everything in these docs was written by reading the actual repository state
(Gradle files, `gradle/libs.versions.toml`, `application.yaml`,
`docker-compose.yml`, source files) at the time of writing, rather than
assumed or templated. Where the project is a scaffold and hasn't implemented
something yet (e.g. HTTP calls from the client to the backend), that's
called out explicitly instead of being described as if it already existed.
If you notice something here that has drifted from the code, fix the doc.
