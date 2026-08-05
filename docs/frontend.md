# Frontend (Compose Multiplatform)

## Compose Multiplatform setup

- **Compose Multiplatform:** `1.11.1`
- **Kotlin:** `2.4.10`
- **Kotlin Multiplatform targets** (declared in
  [`shared/build.gradle.kts`](../shared/build.gradle.kts)):
  `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `jvm`, `js` (browser),
  `wasmJs` (browser).

Compose plugins used across the client modules:
`org.jetbrains.compose` (Compose Multiplatform) and
`org.jetbrains.kotlin.plugin.compose` (the Compose compiler plugin), both
version-matched via `gradle/libs.versions.toml`.

Compose resources (images, strings, etc.) are configured with a custom
package in `:shared`:

```kotlin
// shared/build.gradle.kts
compose.resources {
    packageOfResClass = "com.gotgotneed.generated.resources"
}
```

which is why generated resource references look like
`com.gotgotneed.generated.resources.Res`.

## Shared code strategy

Everything UI/business-logic related that doesn't need a platform API lives
in [`:shared`'s `commonMain`](../shared/src/commonMain/kotlin/com/gotgotneed):

- `App.kt` — the actual `@Composable fun App()` screen, using
  Material 3 (`compose-material3`), shared by all four client targets.
- `Platform.kt` — declares `expect fun getPlatform(): Platform`, the
  pattern used for anything that must differ per platform (see below).
- `Greeting.kt` / `GreetingUtil.kt` — small demo logic showing how plain
  Kotlin (non-Compose) code is shared identically across targets.

Each platform app (`androidApp`, `desktopApp`, `webApp`, `iosApp`) is a
thin wrapper that does nothing but host `App()`:

```kotlin
// androidApp/src/main/kotlin/com/gotgotneed/MainActivity.kt
setContent { App() }

// desktopApp/src/main/kotlin/com/gotgotneed/main.kt
fun main() = application { Window(...) { App() } }

// webApp/src/webMain/kotlin/com/gotgotneed/main.kt
fun main() { ComposeViewport { App() } }
```

`iosApp` is the exception: it's a plain SwiftUI app
(`iosApp/iosApp/iOSApp.swift`) that hosts the `Shared.framework` compiled
from `:shared`'s iOS targets via `ContentView.swift`.

**Guideline:** if you're about to write the same logic twice for two
platforms, it almost certainly belongs in `:shared`'s `commonMain` instead.

## Platform-specific code

Handled via Kotlin Multiplatform's `expect`/`actual` mechanism. Currently
the only `expect` declaration is `Platform`:

```
shared/src/commonMain/kotlin/com/gotgotneed/Platform.kt   → expect fun getPlatform(): Platform
shared/src/androidMain/kotlin/com/gotgotneed/Platform.android.kt → actual
shared/src/iosMain/kotlin/com/gotgotneed/Platform.ios.kt         → actual
shared/src/jvmMain/kotlin/com/gotgotneed/Platform.jvm.kt         → actual
shared/src/jsMain/kotlin/com/gotgotneed/Platform.js.kt           → actual
shared/src/wasmJsMain/kotlin/com/gotgotneed/Platform.wasmJs.kt   → actual
```

As the app grows, anything needing a platform API (local storage, secure
storage, notifications, HTTP client engine — see
[`architecture.md`](./architecture.md)'s offline-first section) should
follow this same pattern: declare the contract in `commonMain`, implement
it per platform in the matching `*Main` source set.

Beyond `Platform`, each platform module also owns genuinely
platform-specific concerns that don't belong in `:shared` at all:
`androidApp`'s `AndroidManifest.xml`/launcher icons, `desktopApp`'s native
packaging (`compose.desktop.application.nativeDistributions`), `webApp`'s
`index.html`/`styles.css`, and `iosApp`'s Xcode project/Info.plist.

## Compiler/JVM target notes

These differ per module (see [`troubleshooting.md`](./troubleshooting.md)
for why this matters if you hit a toolchain error):

| Module | JVM target |
|---|---|
| `:shared` (Android source set) | JVM 17 |
| `:androidApp` | JVM 11 |
| `:backend` | JVM 17 (toolchain) |
| Gradle daemon itself | JDK 21 |

## How to run each target

```bash
# Android — builds a debug APK; run via Android Studio's device/emulator picker
# for an actual install+launch, or use this for a pure build check:
./gradlew :androidApp:assembleDebug

# Desktop (JVM) — opens a native window directly, no emulator needed
./gradlew :desktopApp:run
# ...or with hot reload while you edit:
./gradlew :desktopApp:hotRun --auto

# Web — Wasm target (faster, needs a modern browser)
./gradlew :webApp:wasmJsBrowserDevelopmentRun
# Web — JS target (slower, supports older browsers)
./gradlew :webApp:jsBrowserDevelopmentRun

# iOS — not a Gradle task. Open iosApp/iosApp.xcodeproj in Xcode (macOS only)
# and run from there; Xcode invokes the Kotlin/Native build for you.
```

See [`troubleshooting.md`](./troubleshooting.md) for why iOS specifically
cannot be built/run on this project's Linux dev environment.

## See also

- [`architecture.md`](./architecture.md) — module responsibilities and
  dependency direction in more depth, plus the (currently unimplemented)
  backend-communication and offline-first plans.
- [`commands.md`](./commands.md) — flat command reference.
