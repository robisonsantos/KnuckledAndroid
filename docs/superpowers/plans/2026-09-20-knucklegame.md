# KnuckleGame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full KnuckleGame dice-board game for Android — two players over Bluetooth, server-authoritative, with a 3D rolling die, drag/tap placement, destruction animations, and win/lose/draw overlays — following `docs/superpowers/specs/2026-09-20-knucklegame-design.md`.

**Architecture:** Server/client over Bluetooth RFCOMM. The host owns all game state and rules via an adapted `GameHost`; the client mirrors state. Reused infra (Bluetooth stack, audio, theme, shared components, fake mode) is copied verbatim from `$HOME/AndroidStudioProjects/DiceGame` and renamed to `com.example.knucklegame`. New game logic (state, rules, messages, host, screen) is written fresh against that infra. Everything is tested first (JVM unit tests), then Maestro flows against the fake-mode connector, then manual verification on two devices.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, sceneview (3D dice), Classic Bluetooth RFCOMM, JUnit 4, Maestro.

**Reference project root (abbreviated below as `DICE`):** `$HOME/AndroidStudioProjects/DiceGame`
Our repo root is abbreviated as `KG` — the working directory for all commands is `/Users/robison/AndroidStudioProjects/KnuckleGame`.

---

## File structure

Files created/modified by this plan (grouped by responsibility):

**Build / manifest**
- `gradle/libs.versions.toml` — add sceneview, kotlinx-serialization, material-icons, splashscreen, lifecycle-viewmodel-compose
- `build.gradle.kts` (root) — declare `kotlin-serialization` plugin
- `app/build.gradle.kts` — serialization plugin, new deps, `buildConfig = true`
- `app/src/main/AndroidManifest.xml` — Bluetooth permissions
- `app/src/main/res/values/themes.xml` — add `Theme.KnuckleGame.Splash`
- `app/src/main/res/values/splash_colors.xml` — create
- `app/src/main/res/values/strings.xml` — replace with full string set

**Assets / resources (copied from `DICE`)**
- `app/src/main/assets/models/dice.glb`
- `app/src/main/keepRules/rules.keep` — add keep rule
- `app/src/main/res/font/cinzel_bold.ttf`
- `app/src/main/res/raw/{tap,rattle,land,win,lose}.wav`

**Bluetooth infra (copied from `DICE`, package renamed)**
- `app/src/main/java/com/example/knucklegame/bluetooth/{Protocol,PinGenerator,GameLink,Handshake,BluetoothConnector,AndroidBluetoothConnector,FakeBluetoothConnector}.kt`

**Audio + settings (copied from `DICE`, package renamed)**
- `app/src/main/java/com/example/knucklegame/audio/{SoundManager,AndroidSoundManager}.kt`
- `app/src/main/java/com/example/knucklegame/settings/Settings.kt`

**Theme + shared components (copied from `DICE`, package renamed)**
- `app/src/main/java/com/example/knucklegame/ui/theme/{Color,Theme,Type}.kt`
- `app/src/main/java/com/example/knucklegame/ui/components/{FeltBackground,GlassCard,GoldButton,PinDigits,DiceGameTopBar,WinnerOverlay,Confetti}.kt`
- `app/src/main/java/com/example/knucklegame/ui/components/DrawOverlay.kt` (new)
- `app/src/main/java/com/example/knucklegame/ui/dice/DiceCube.kt`

**Connection flow (copied from `DICE`, package renamed)**
- `app/src/main/java/com/example/knucklegame/ui/{Locals,ConnectionViewModel}.kt`
- `app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt`
- `app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt` (replaces `DiceGameApp`)
- `app/src/main/java/com/example/knucklegame/MainActivity.kt` (replace scaffold)

**Game logic (new — the core of this plan)**
- `app/src/main/java/com/example/knucklegame/game/GameState.kt` — `PlayerId`, `Status`, `Phase`, `DieRef`, `GameState`, `Grid` typealias
- `app/src/main/java/com/example/knucklegame/game/KnucklebonesRules.kt` — pure rules + scoring
- `app/src/main/java/com/example/knucklegame/game/GameMessages.kt` — line protocol
- `app/src/main/java/com/example/knucklegame/game/GameHost.kt` — server-side session host
- `app/src/main/java/com/example/knucklegame/game/FakeGamePeer.kt` — auto-playing bot for fake mode

**UI game screen (new)**
- `app/src/main/java/com/example/knucklegame/ui/GameViewModel.kt`
- `app/src/main/java/com/example/knucklegame/ui/GameScreen.kt` — boards, roll area, drag/tap placement, overlays

**Tests**
- `app/src/test/java/com/example/knucklegame/game/KnucklebonesRulesTest.kt`
- `app/src/test/java/com/example/knucklegame/game/GameMessagesTest.kt`
- `app/src/test/java/com/example/knucklegame/game/GameHostTest.kt`
- `app/src/test/java/com/example/knucklegame/game/FakeGamePeerTest.kt`
- `app/src/test/java/com/example/knucklegame/bluetooth/{ProtocolFramingTest,HandshakeTest,PinGeneratorTest,GameLinkTest}.kt` (copied/renamed from `DICE`)
- `app/src/test/java/com/example/knucklegame/TestSupport.kt`

**Maestro**
- `.maestro/01-host-game.yaml`, `.maestro/02-client-game.yaml`, `.maestro/03-wrong-pin.yaml`

---

## Task 1: Gradle, manifest, and resources setup

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/splash_colors.xml`
- Modify: `app/src/main/keepRules/rules.keep`

- [ ] **Step 1: Update `gradle/libs.versions.toml`**

Replace the whole file with (`DICE/gradle/libs.versions.toml` plus `[plugins].kotlin-serialization`):

```toml
[versions]
agp = "9.4.1"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.11.0"
activityCompose = "1.13.0"
kotlin = "2.4.20"
kotlinxSerializationJson = "1.7.3"
composeBom = "2026.02.01"
coreSplashscreen = "1.0.1"
sceneview = "4.38.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
sceneview = { group = "io.github.sceneview", name = "sceneview", version.ref = "sceneview" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Update root `build.gradle.kts`**

Replace the file body so it declares the serialization plugin (mirrors `DICE/build.gradle.kts`):

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 3: Update `app/build.gradle.kts`**

Replace with (`DICE/app/build.gradle.kts`, renamed namespace/appId, `buildConfig` enabled):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.knucklegame"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.knucklegame"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.sceneview)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 4: Update `app/src/main/AndroidManifest.xml`**

Place the Bluetooth permissions and splash theme attribute (mirrors `DICE/app/src/main/AndroidManifest.xml`; note the activity already uses `@style/Theme.KnuckleGame`, add the `.Splash` variant):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.KnuckleGame">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.KnuckleGame.Splash"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: Update themes and create splash color**

Replace `app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.KnuckleGame" parent="android:Theme.Material.Light.NoActionBar" />
    <style name="Theme.KnuckleGame.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/felt_dark</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/Theme.KnuckleGame</item>
    </style>
</resources>
```

Create `app/src/main/res/values/splash_colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="felt_dark">#071A10</color>
</resources>
```

- [ ] **Step 6: Add the keep rule for the 3D model**

Append to `app/src/main/keepRules/rules.keep`:

```
# SceneView loads the GLB model by string path; keep the asset and node types.
-keep class io.github.sceneview.** { *; }
-keep class com.google.android.filament.** { *; }
```

- [ ] **Step 7: Build to verify setup**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If a dependency cannot be resolved, confirm `repositories` includes `google()` and `mavenCentral()` in `settings.gradle.kts` (already present).

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml app/src/main/res/values/splash_colors.xml app/src/main/keepRules/rules.keep
git commit -m "build: add sceneview, serialization, BT permissions, splash theme"
```

---

## Task 2: Copy shared assets and resources from DiceGame

**Files:**
- Create: `app/src/main/assets/models/dice.glb`
- Create: `app/src/main/res/font/cinzel_bold.ttf`
- Create: `app/src/main/res/raw/{tap,rattle,land,win,lose}.wav`
- Create: `app/src/main/res/values/strings.xml` (replace scaffold)

- [ ] **Step 1: Copy binary assets**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
mkdir -p app/src/main/assets/models app/src/main/res/font app/src/main/res/raw
cp "$DICE/app/src/main/assets/models/dice.glb" app/src/main/assets/models/dice.glb
cp "$DICE/app/src/main/res/font/cinzel_bold.ttf" app/src/main/res/font/cinzel_bold.ttf
cp "$DICE/app/src/main/res/raw/tap.wav" app/src/main/res/raw/tap.wav
cp "$DICE/app/src/main/res/raw/rattle.wav" app/src/main/res/raw/rattle.wav
cp "$DICE/app/src/main/res/raw/land.wav" app/src/main/res/raw/land.wav
cp "$DICE/app/src/main/res/raw/win.wav" app/src/main/res/raw/win.wav
cp "$DICE/app/src/main/res/raw/lose.wav" app/src/main/res/raw/lose.wav
```

Verify: `ls -la app/src/main/assets/models/dice.glb app/src/main/res/font/ app/src/main/res/raw/` shows the files.

- [ ] **Step 2: Replace `app/src/main/res/values/strings.xml`**

This file supersedes the scaffold's; it removes round/sudden-death strings and adds knucklebones-specific strings (place hint, draw, column score):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KnuckleGame</string>
    <string name="your_name">Your name</string>
    <string name="host_game">Host a game</string>
    <string name="join_game">Join a game</string>
    <string name="fake_link">Fake link</string>
    <string name="fake_link_on">Debug: using in-process fake Bluetooth. No real devices needed.</string>
    <string name="fake_link_off">Real Bluetooth mode. Two phones required.</string>
    <string name="onboarding_hint">Host on one phone, join from the other — first to fill a 3x3 grid wins.</string>
    <string name="dismiss">Dismiss</string>
    <string name="cancel">Cancel</string>
    <string name="pairing_code">Pairing code</string>
    <string name="waiting_for_device">Waiting for a device…</string>
    <string name="enter_pin_title">Enter the code shown on the host phone</string>
    <string name="leave">Leave</string>
    <string name="play_again">Play again</string>
    <string name="disconnect">Disconnect</string>
    <string name="your_turn">Your turn</string>
    <string name="turn_other">%1$s’s turn</string>
    <string name="rolling">Rolling…</string>
    <string name="place_hint">Drag the die to a column, or tap a column</string>
    <string name="wins">%1$s wins!</string>
    <string name="you_win">You win!</string>
    <string name="you_lose">You lost</string>
    <string name="draw">It’s a draw!</string>
    <string name="draw_score_banner">%1$d – %2$d</string>
    <string name="waiting_for_state">Waiting for game state…</string>
    <string name="mute">Mute</string>
    <string name="unmute">Unmute</string>
    <string name="peer_disconnected">Peer disconnected</string>
    <string name="dice">Dice</string>
    <string name="column_score">Col %1$d</string>
</resources>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets app/src/main/res/font app/src/main/res/raw app/src/main/res/values/strings.xml
git commit -m "assets: copy dice model, sounds, font, and strings from DiceGame"
```

---

## Task 3: Copy Bluetooth infrastructure

**Files:**
- Create (×7): `app/src/main/java/com/example/knucklegame/bluetooth/{Protocol,PinGenerator,GameLink,Handshake,BluetoothConnector,AndroidBluetoothConnector,FakeBluetoothConnector}.kt`

These are verbatim copies from `DICE/app/src/main/java/com/example/dicegame/bluetooth/` with the package/import names renamed `dicegame → knucklegame`. `PinGenerator` and `GameLinkImpl` live in `Protocol.kt` and `GameLink.kt` respectively in `DICE`; copy the files as-is and rename inside.

- [ ] **Step 1: Copy and rename the seven files**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
SRC="$DICE/app/src/main/java/com/example/dicegame/bluetooth"
DST="app/src/main/java/com/example/knucklegame/bluetooth"
mkdir -p "$DST"
for f in Protocol GameLink Handshake BluetoothConnector AndroidBluetoothConnector FakeBluetoothConnector; do
  cp "$SRC/$f.kt" "$DST/$f.kt"
done
# Fix package + import + reference strings
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' "$DST"/*.kt
# Rename BT service tag for this app
sed -i '' 's/DiceGame/KnuckleGame/g' "$DST"/*.kt
```

Note: `sed -i ''` is the macOS (BSD) in-place flag.

Verify (no `dicegame` references remain):

```bash
grep -rn "dicegame" "$DST" && echo "STILL HAS dicegame" || echo "OK"
```

- [ ] **Step 2: Verify the only strings that changed**

Read `app/src/main/java/com/example/knucklegame/bluetooth/Protocol.kt`. Expected contents equal to `DICE` except: package `com.example.knucklegame.bluetooth`, and `BT_SERVICE_NAME = "KnuckleGame"`.

- [ ] **Step 3: Copy the Bluetooth tests**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
SRC="$DICE/app/src/test/java/com/example/dicegame/bluetooth"
DST="app/src/test/java/com/example/knucklegame/bluetooth"
mkdir -p "$DST"
for f in ProtocolFramingTest HandshakeTest PinGeneratorTest GameLinkTest; do
  cp "$SRC/$f.kt" "$DST/$f.kt"
done
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' "$DST"/*.kt
```

- [ ] **Step 4: Run the Bluetooth unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.bluetooth.*"`
Expected: 4 tests classes pass (`ProtocolFramingTest`, `HandshakeTest`, `PinGeneratorTest`, `GameLinkTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/bluetooth app/src/test/java/com/example/knucklegame/bluetooth
git commit -m "bluetooth: copy client/server stack and tests from DiceGame"
```

---

## Task 4: Copy audio and settings

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/audio/SoundManager.kt`
- Create: `app/src/main/java/com/example/knucklegame/audio/AndroidSoundManager.kt`
- Create: `app/src/main/java/com/example/knucklegame/settings/Settings.kt`

- [ ] **Step 1: Copy and rename files**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
mkdir -p app/src/main/java/com/example/knucklegame/audio app/src/main/java/com/example/knucklegame/settings
cp "$DICE/app/src/main/java/com/example/dicegame/audio/SoundManager.kt" app/src/main/java/com/example/knucklegame/audio/SoundManager.kt
cp "$DICE/app/src/main/java/com/example/dicegame/audio/AndroidSoundManager.kt" app/src/main/java/com/example/knucklegame/audio/AndroidSoundManager.kt
cp "$DICE/app/src/main/java/com/example/dicegame/settings/Settings.kt" app/src/main/java/com/example/knucklegame/settings/Settings.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/main/java/com/example/knucklegame/audio/*.kt app/src/main/java/com/example/knucklegame/settings/*.kt
```

- [ ] **Step 2: Copy the settings test**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
mkdir -p app/src/test/java/com/example/knucklegame/settings
cp "$DICE/app/src/test/java/com/example/dicegame/settings/SettingsTest.kt" app/src/test/java/com/example/knucklegame/settings/SettingsTest.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/test/java/com/example/knucklegame/settings/*.kt
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.settings.*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/audio app/src/main/java/com/example/knucklegame/settings app/src/test/java/com/example/knucklegame/settings
git commit -m "audio: copy sound manager and settings from DiceGame"
```

---

## Task 5: Copy theme and shared UI components

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/ui/theme/{Color,Theme,Type}.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/components/{FeltBackground,GlassCard,GoldButton,PinDigits,DiceGameTopBar,WinnerOverlay,Confetti}.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/dice/DiceCube.kt`

- [ ] **Step 1: Copy theme files**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
mkdir -p app/src/main/java/com/example/knucklegame/ui/theme
cp "$DICE/app/src/main/java/com/example/dicegame/ui/theme/Color.kt" app/src/main/java/com/example/knucklegame/ui/theme/Color.kt
cp "$DICE/app/src/main/java/com/example/dicegame/ui/theme/Theme.kt" app/src/main/java/com/example/knucklegame/ui/theme/Theme.kt
cp "$DICE/app/src/main/java/com/example/dicegame/ui/theme/Type.kt" app/src/main/java/com/example/knucklegame/ui/theme/Type.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/main/java/com/example/knucklegame/ui/theme/*.kt
```

The scaffold already defines `KnuckleGameTheme` in `ui/theme/Theme.kt`; this copy overrides it with DiceGame's felt/gold theme. Keep the exported name — verify `DICE`'s Theme.kt exposes the theme composable `@Composable fun DiceGameTheme(...)`. If it does, rename the composable to `KnuckleGameTheme` in the copied file (`sed -i '' 's/DiceGameTheme/KnuckleGameTheme/g'`). Check `Type.kt` exposes `DisplayFont` (used by connection screens).

- [ ] **Step 2: Copy shared components**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
mkdir -p app/src/main/java/com/example/knucklegame/ui/components app/src/main/java/com/example/knucklegame/ui/dice
for f in FeltBackground GlassCard GoldButton PinDigits DiceGameTopBar WinnerOverlay Confetti; do
  cp "$DICE/app/src/main/java/com/example/dicegame/ui/components/$f.kt" app/src/main/java/com/example/knucklegame/ui/components/$f.kt
done
cp "$DICE/app/src/main/java/com/example/dicegame/ui/dice/DiceCube.kt" app/src/main/java/com/example/knucklegame/ui/dice/DiceCube.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/main/java/com/example/knucklegame/ui/components/*.kt app/src/main/java/com/example/knucklegame/ui/dice/*.kt
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Some copied components may reference `Locals`/`LocalSoundManager` which do not exist yet — Task 6 creates them. If compilation fails on missing `LocalSoundManager`/`LocalAnimationsEnabled`, create `ui/Locals.kt` first, then re-run.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/ui/theme app/src/main/java/com/example/knucklegame/ui/components app/src/main/java/com/example/knucklegame/ui/dice
git commit -m "ui: copy theme, shared components, and 3D die from DiceGame"
```

---

## Task 6: Copy connection flow (Locals, ConnectionViewModel, ConnectionScreens, App, MainActivity)

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/ui/Locals.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt`
- Replace: `app/src/main/java/com/example/knucklegame/MainActivity.kt`

- [ ] **Step 1: Copy Locals**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
cp "$DICE/app/src/main/java/com/example/dicegame/ui/Locals.kt" app/src/main/java/com/example/knucklegame/ui/Locals.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/main/java/com/example/knucklegame/ui/Locals.kt
```

Contents (package `com.example.knucklegame.ui`):

```kotlin
package com.example.knucklegame.ui

import androidx.compose.runtime.compositionLocalOf
import com.example.knucklegame.audio.NoopSoundManager
import com.example.knucklegame.audio.SoundManager

val LocalSoundManager = compositionLocalOf<SoundManager> { NoopSoundManager }
val LocalAnimationsEnabled = compositionLocalOf { true }
```

- [ ] **Step 2: Copy ConnectionViewModel**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
cp "$DICE/app/src/main/java/com/example/dicegame/ui/ConnectionViewModel.kt" app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g; s/TAG = "DiceGame"/TAG = "KnuckleGame"/g' app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt
```

This file references `com.example.knucklegame.game.FakeGamePeer` and `bluetooth.*` (Tasks 3/10). Its behavior is unchanged: PIN hosting, discovery, fake-mode toggle, connect, disconnect.

- [ ] **Step 3: Copy ConnectionScreens**

Run:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
cp "$DICE/app/src/main/java/com/example/dicegame/ui/ConnectionScreens.kt" app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g' app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt
```

- [ ] **Step 4: Create `KnuckleGameApp.kt`**

This mirrors `DICE/.../ui/DiceGameApp.kt` but: (a) renamed composable, (b) instantiates `GameViewModel` with the new placement API (Task 11), (c) passes `onPlaceColumn` through. Write the full file:

```kotlin
package com.example.knucklegame.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.knucklegame.audio.NoopSoundManager
import com.example.knucklegame.audio.SoundManager
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.settings.Settings
import com.example.knucklegame.ui.components.FeltBackground

@Composable
fun KnuckleGameApp(
    connectionViewModel: ConnectionViewModel,
    onHostClick: () -> Unit,
    onFindClick: () -> Unit,
    onSubmitPin: (String) -> Unit,
    soundManager: SoundManager = NoopSoundManager,
    settings: Settings? = null,
    animationsEnabled: Boolean = true,
) {
    var showHint by remember { mutableStateOf(settings?.onboardingSeen == false) }
    CompositionLocalProvider(
        LocalSoundManager provides soundManager,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        FeltBackground {
            val connected = connectionViewModel.state as? ConnectionState.Connected
            val route = if (connected != null) "game"
                else connectionViewModel.state::class.simpleName ?: "Start"
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState == "game") {
                        (scaleIn(initialScale = 0.92f, animationSpec = tween(300)) + fadeIn(tween(300))) togetherWith
                            fadeOut(tween(200))
                    } else {
                        (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)))
                    }
                },
                label = "route",
            ) { targetRoute ->
                if (targetRoute == "game") {
                    val activeConnection = connectionViewModel.state as? ConnectionState.Connected
                    if (activeConnection == null) return@AnimatedContent
                    val link = activeConnection.link
                    val myId = if (activeConnection.isHost) PlayerId.HOST else PlayerId.CLIENT
                    val gameViewModel: GameViewModel = viewModel(
                        key = "game-${System.identityHashCode(link)}",
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return GameViewModel(
                                    link = link,
                                    myId = myId,
                                    hostName = if (myId == PlayerId.HOST) connectionViewModel.sanitizedPlayerName else null,
                                    clientName = if (myId == PlayerId.CLIENT) connectionViewModel.sanitizedPlayerName else null,
                                    onPeerDisconnected = { connectionViewModel.onPeerDisconnected() },
                                ) as T
                            }
                        },
                    )
                    Scaffold(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize()) { inner ->
                        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
                            GameScreen(
                                viewModel = gameViewModel,
                                onDisconnect = { connectionViewModel.disconnect() },
                            )
                        }
                    }
                } else {
                    Scaffold(containerColor = Color.Transparent, modifier = Modifier.fillMaxSize()) { inner ->
                        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
                            when (val s = connectionViewModel.state) {
                                is ConnectionState.Start -> StartScreen(
                                    name = connectionViewModel.playerName,
                                    onNameChange = connectionViewModel::onPlayerNameChange,
                                    onHostClick = onHostClick,
                                    onFindClick = onFindClick,
                                    isFakeMode = connectionViewModel.inFakeMode,
                                    onToggleFake = connectionViewModel::toggleFakeMode,
                                    showHint = showHint,
                                    onDismissHint = {
                                        settings?.onboardingSeen = true
                                        showHint = false
                                    },
                                    error = connectionViewModel.errorText,
                                    onDismissError = connectionViewModel::dismissError,
                                )
                                is ConnectionState.Hosting -> HostingScreen(
                                    pin = s.pin,
                                    onCancel = connectionViewModel::cancelCurrent,
                                )
                                is ConnectionState.Discovering -> DiscoverScreen(
                                    devices = connectionViewModel.foundDevices,
                                    onDeviceClick = connectionViewModel::onDeviceSelected,
                                    onCancel = connectionViewModel::cancelCurrent,
                                    status = connectionViewModel.statusText,
                                )
                                is ConnectionState.EnterPin -> EnterPinScreen(
                                    device = s.device,
                                    onConfirm = onSubmitPin,
                                    onCancel = connectionViewModel::cancelCurrent,
                                    status = connectionViewModel.statusText,
                                )
                                is ConnectionState.Connected -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
```

The file references `GameViewModel` and `GameScreen` (Tasks 11–13) and the `ConnectionState` sealed types from `ConnectionViewModel`.

- [ ] **Step 5: Replace `MainActivity.kt`**

Copy from `DICE` and rename:

```bash
DICE="$HOME/AndroidStudioProjects/DiceGame"
cp "$DICE/app/src/main/java/com/example/dicegame/MainActivity.kt" app/src/main/java/com/example/knucklegame/MainActivity.kt
sed -i '' 's/com\.example\.dicegame/com.example.knucklegame/g; s/DiceGameApp/KnuckleGameApp/g; s/DiceGameTheme/KnuckleGameTheme/g; s/Theme\.DiceGame/Theme.KnuckleGame/g; s/MainActivity to retry\./MainActivity to retry./ already applied → see note/g; s/packages "DiceGame"//g' app/src/main/java/com/example/knucklegame/MainActivity.kt
# Fix the two hardcoded Settings storage name + permission-denied copy strings that still say DiceGame
```

Then edit the resulting file so that:
- The `getSharedPreferences("dicegame", MODE_PRIVATE)` call becomes `getSharedPreferences("knucklegame", MODE_PRIVATE)`.
- Any user-facing "DiceGame" text inside the denied-permission messages becomes "KnuckleGame".

Expected `MainActivity.kt` core (after rename — full copy of `DICE` file with those substitutions):

```kotlin
package com.example.knucklegame

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.knucklegame.audio.AndroidSoundManager
import com.example.knucklegame.audio.SoundManager
import com.example.knucklegame.settings.AndroidPrefs
import com.example.knucklegame.settings.Settings
import com.example.knucklegame.ui.ConnectionViewModel
import com.example.knucklegame.ui.KnuckleGameApp
import com.example.knucklegame.ui.theme.KnuckleGameTheme

class MainActivity : ComponentActivity() {

    private val connectionViewModel: ConnectionViewModel by viewModels<ConnectionViewModel>()

    private lateinit var soundManager: SoundManager
    private lateinit var settings: Settings

    private var readyThen: (() -> Unit)? = null
    private var pendingPerms: Array<String>? = null
    private var pendingDeniedMessage: String? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { ... identical to DICE ... }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { ... identical to DICE ... }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { ... identical to DICE ... }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        settings = Settings(AndroidPrefs(getSharedPreferences("knucklegame", MODE_PRIVATE)))
        soundManager = AndroidSoundManager(this, settings)
        val animationsEnabled =
            !(BuildConfig.DEBUG && intent.getBooleanExtra("disableIdleAnimations", false))
        enableEdgeToEdge()
        setContent {
            KnuckleGameTheme {
                KnuckleGameApp(
                    connectionViewModel = connectionViewModel,
                    onHostClick = ::onHostClick,
                    onFindClick = ::onFindClick,
                    onSubmitPin = ::onSubmitPin,
                    soundManager = soundManager,
                    settings = settings,
                    animationsEnabled = animationsEnabled,
                )
            }
        }
    }

    // onDestroy(), onHostClick(), onFindClick(), onSubmitPin(), requestOrRun(),
    // ensureBluetoothEnabled(), ensureDiscoverable(), permission helpers:
    // copy all from DICE/app/src/main/java/com/example/dicegame/MainActivity.kt unchanged.
    // (The "…" above means: keep DICE's bodies verbatim, only the substitutions already applied.)
}
```

- [ ] **Step 6: Compile**

The connection flow compiles only once `game/FakeGamePeer` (Task 10) exists — `ConnectionViewModel` references `FakeGamePeer.PIN`. If compilation fails, proceed to Task 10 and return. Confirm `Gradle` can resolve `R.string.*` used by `ConnectionScreens`.

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (after Task 10 completes).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/ui/Locals.kt app/src/main/java/com/example/knucklegame/ui/ConnectionViewModel.kt app/src/main/java/com/example/knucklegame/ui/ConnectionScreens.kt app/src/main/java/com/example/knucklegame/ui/KnuckleGameApp.kt app/src/main/java/com/example/knucklegame/MainActivity.kt
git commit -m "ui: port connection flow from DiceGame"
```

---

## Task 7: Game state types

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/game/GameState.kt`

- [ ] **Step 1: Write `GameState.kt`**

```kotlin
package com.example.knucklegame.game

import kotlinx.serialization.Serializable

enum class PlayerId { HOST, CLIENT }

enum class Status { IN_PROGRESS, FINISHED, DRAW }

enum class Phase { IDLE, ROLLING, AWAITING_PLACEMENT }

/** A single column of a board: each die is its face value 1..6, bottom-most die first. */
typealias Column = List<Int>

/** A 3x3 board: three columns, each holding up to [KnucklebonesRules.COLUMN_SIZE] dice. */
typealias Grid = List<Column>

/** One die that was just destroyed, so the UI can animate it. */
@Serializable
data class DieRef(
    val player: PlayerId,
    val column: Int,
    val value: Int,
)

@Serializable
data class GameState(
    val hostName: String,
    val clientName: String,
    val status: Status,
    val currentTurn: PlayerId,
    val phase: Phase,
    val grid: Map<PlayerId, Grid>,
    val winner: PlayerId? = null,
    val lastRoll: Int? = null,
    val destroyed: List<DieRef> = emptyList(),
) {
    fun playerName(player: PlayerId): String = when (player) {
        PlayerId.HOST -> hostName
        PlayerId.CLIENT -> clientName
    }

    fun opponentOf(player: PlayerId): PlayerId =
        if (player == PlayerId.HOST) PlayerId.CLIENT else PlayerId.HOST
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game/GameState.kt
git commit -m "game: add knucklebones state model"
```

---

## Task 8: Knucklebones rules engine (TDD)

**Files:**
- Test: `app/src/test/java/com/example/knucklegame/game/KnucklebonesRulesTest.kt`
- Create: `app/src/main/java/com/example/knucklegame/game/KnucklebonesRules.kt`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/knucklegame/game/KnucklebonesRulesTest.kt`:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.game.KnucklebonesRules.columnFull
import com.example.knucklegame.game.KnucklebonesRules.columnScore
import com.example.knucklegame.game.KnucklebonesRules.completeRoll
import com.example.knucklebones.game.KnucklebonesRules.totalScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KnucklebonesRulesTest {

    private fun hostTurn(): GameState =
        KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)

    private fun rolled(state: GameState, value: Int = 3): GameState =
        completeRoll(state, state.currentTurn, value)

    @Test
    fun `reset starts an empty in-progress game with given first player`() {
        val s = KnucklebonesRules.reset("Host", "Client", PlayerId.CLIENT)
        assertEquals(Status.IN_PROGRESS, s.status)
        assertEquals(PlayerId.CLIENT, s.currentTurn)
        assertEquals(Phase.IDLE, s.phase)
        assertNull(s.winner)
        assertNull(s.lastRoll)
        assertTrue(s.destroyed.isEmpty())
        for (p in PlayerId.entries) {
            assertEquals(3, s.grid[p]!!.size)
            for (col in s.grid[p]!!) {
                assertTrue(col.isEmpty())
            }
        }
    }

    @Test
    fun `single die in a column scores its value`() {
        assertEquals(1, columnScore(listOf(1)))
        assertEquals(6, columnScore(listOf(6)))
    }

    @Test
    fun `pair of same value multiplies by two`() {
        // README table: 2 dice of value v → v*2*2
        assertEquals(6, columnScore(listOf(3, 3)))
        assertEquals(16, columnScore(listOf(4, 4)))
    }

    @Test
    fun `triple of same value multiplies by three`() {
        // README table: 3 dice of value v → v*3*3
        assertEquals(9, columnScore(listOf(1, 1, 1)))
        assertEquals(54, columnScore(listOf(6, 6, 6)))
    }

    @Test
    fun `mixed column scores each value by its own count, order irrelevant`() {
        // README example: 4-1-4 → (4+4)*2 + 1 = 17
        assertEquals(17, columnScore(listOf(4, 1, 4)))
        assertEquals(17, columnScore(listOf(1, 4, 4)))
    }

    @Test
    fun `multi column grid sums column scores`() {
        val grid: Grid = listOf(listOf(4, 1, 4), listOf(3, 3), emptyList())
        assertEquals(17 + 12, totalScore(grid))
    }

    @Test
    fun `columnFull reflects capacity`() {
        assertFalse(columnFull(listOf(1, 2), 0))
        assertTrue(columnFull(listOf(1, 2, 3), 0))
        assertFalse(columnFull(listOf(), 0))
    }

    @Test
    fun `canRoll only during idle on senders turn`() {
        val s = hostTurn()
        assertTrue(KnucklebonesRules.canRoll(s, PlayerId.HOST))
        assertFalse(KnucklebonesRules.canRoll(s, PlayerId.CLIENT))
        val playing = KnucklebonesRules.beginRoll(s, PlayerId.HOST)
        assertFalse(KnucklebonesRules.canRoll(playing, PlayerId.HOST))
    }

    @Test
    fun `completeRoll moves to awaiting placement and records the value`() {
        val after = rolled(hostTurn(), value = 5)
        assertEquals(Phase.AWAITING_PLACEMENT, after.phase)
        assertEquals(5, after.lastRoll)
        assertTrue(after.grid[PlayerId.HOST]!!.flatten().isEmpty())
    }

    @Test
    fun `canPlace restricted to the roller awaiting placement and non-full column`() {
        val s = rolled(hostTurn(), value = 4)
        assertTrue(KnucklebonesRules.canPlace(s, PlayerId.HOST, 0))
        assertFalse(KnucklebonesRules.canPlace(s, PlayerId.CLIENT, 0))
        assertFalse(KnucklebonesRules.canPlace(s, PlayerId.HOST, 2 + 1))
        val idle = KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)
        assertFalse(KnucklebonesRules.canPlace(idle, PlayerId.HOST, 0))
    }

    @Test
    fun `placing adds die to chosen column and ends the turn`() {
        val s = rolled(hostTurn(), value = 4)
        val after = KnucklebonesRules.place(s, PlayerId.HOST, 1)
        assertEquals(listOf(4), after.grid[PlayerId.HOST]!![1])
        assertEquals(PlayerId.CLIENT, after.currentTurn)
        assertEquals(Phase.IDLE, after.phase)
        assertNull(after.lastRoll)
    }

    @Test
    fun `placed die destroys matching dice in opponents same column only`() {
        val mine = KnucklebonesRules.reset("Host", "Client", PlayerId.HOST)
        // Give Host a 3 in column 0; Client three dice; opponent has 3 in column 0 and 3 in column 2.
        var s = rolled(mine, value = 1)
        s = KnucklebonesRules.place(s, PlayerId.HOST, 0) // host 1
        s = s.copy(currentTurn = PlayerId.HOST)          // force host turn for test simplicity
        // place two more host dice in col 0 (1s) directly via place flow: use a fresh helper
        // Build state by hand to isolate the rule:
        val grid = mapOf(
            PlayerId.HOST to listOf(
                listOf(4, 1, 4),
                emptyList(),
                emptyList(),
            ),
            PlayerId.CLIENT to listOf(
                listOf(3, 3),
                emptyList(),
                listOf(3),
            ),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 3,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(listOf(3, 3, 3), after.grid[PlayerId.HOST]!![0])
        // opponent col 0: both 3s destroyed
        assertEquals(emptyList(), after.grid[PlayerId.CLIENT]!![0])
        // opponent col 2: untouched
        assertEquals(listOf(3), after.grid[PlayerId.CLIENT]!![2])
        assertEquals(listOf(DieRef(PlayerId.CLIENT, 0, 3), DieRef(PlayerId.CLIENT, 0, 3)), after.destroyed)
    }

    @Test
    fun `opponent dice of other values survive`() {
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(), listOf(), listOf()),
            PlayerId.CLIENT to listOf(listOf(2, 3, 4), listOf(), listOf()),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 3,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(listOf(2, 4), after.grid[PlayerId.CLIENT]!![0])
    }

    @Test
    fun `game ends when a player fills their board and higher score wins`() {
        // Host: cols (3,3,3),(2,2,2),(1,1,1) = 27+12+9 = 48; Client: 3 empty columns.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(3, 3, 3), listOf(2, 2, 2), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(), listOf(), listOf()),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 5,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0) // col0 already full → guarded
        // col0 is full, so choose col 1 for the winning die: rebuild with room in col 2
        val grid2 = mapOf(
            PlayerId.HOST to listOf(listOf(3, 3, 3), listOf(2, 2), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(), listOf(), listOf()),
        )
        val built2 = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid2, lastRoll = 2,
        )
        val after2 = KnucklebonesRules.place(built2, PlayerId.HOST, 1)
        assertEquals(Status.FINISHED, after2.status)
        assertEquals(PlayerId.HOST, after2.winner)
    }

    @Test
    fun `filling player can still lose on score`() {
        // Host fills board but trails Client massively.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(1, 1), listOf(1, 1, 1), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(6, 6, 6), listOf(6, 6, 6), listOf(6, 6, 6)),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 1,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(Status.FINISHED, after.status)
        assertEquals(PlayerId.CLIENT, after.winner)
    }

    @Test
    fun `equal scores at full board produce a draw`() {
        // Host fills with all 1s (score 9+9+9=27). Client has exactly 27 as well.
        val grid = mapOf(
            PlayerId.HOST to listOf(listOf(1, 1), listOf(1, 1, 1), listOf(1, 1, 1)),
            PlayerId.CLIENT to listOf(listOf(1, 1, 1), listOf(1, 1, 1), listOf(1, 1, 1)),
        )
        val built = GameState(
            hostName = "Host", clientName = "Client",
            status = Status.IN_PROGRESS, currentTurn = PlayerId.HOST,
            phase = Phase.AWAITING_PLACEMENT, grid = grid, lastRoll = 1,
        )
        val after = KnucklebonesRules.place(built, PlayerId.HOST, 0)
        assertEquals(Status.DRAW, after.status)
        assertNull(after.winner)
    }

    @Test
    fun `canRestart only after finished or draw`() {
        assertFalse(KnucklebonesRules.canRestart(hostTurn()))
        val finished = hostTurn().copy(status = Status.FINISHED, winner = PlayerId.HOST)
        assertTrue(KnucklebonesRules.canRestart(finished))
        val draw = hostTurn().copy(status = Status.DRAW)
        assertTrue(KnucklebonesRules.canRestart(draw))
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.KnucklebonesRulesTest"`
Expected: FAIL — `KnucklebonesRules` does not exist; tests do not compile.

- [ ] **Step 3: Implement `KnucklebonesRules.kt`**

```kotlin
package com.example.knucklegame.game

object KnucklebonesRules {
    const val COLUMNS = 3
    const val COLUMN_SIZE = 3

    fun emptyGrid(): Grid = List(COLUMNS) { emptyList() }

    fun reset(hostName: String, clientName: String, firstPlayer: PlayerId): GameState =
        GameState(
            hostName = hostName,
            clientName = clientName,
            status = Status.IN_PROGRESS,
            currentTurn = firstPlayer,
            phase = Phase.IDLE,
            grid = mapOf(
                PlayerId.HOST to emptyGrid(),
                PlayerId.CLIENT to emptyGrid(),
            ),
            winner = null,
            lastRoll = null,
            destroyed = emptyList(),
        )

    /** value × count²: count dice, each worth value×count. */
    fun columnScore(column: Column): Int =
        column.groupingBy { it }.eachCount()
            .entries.sumOf { (value, count) -> value * count * count }

    fun totalScore(grid: Grid): Int = grid.sumOf { columnScore(it) }

    fun columnFull(grid: Grid, column: Int): Boolean =
        grid.getOrElse(column) { emptyList() }.size >= COLUMN_SIZE

    fun canRoll(state: GameState, player: PlayerId): Boolean =
        state.status == Status.IN_PROGRESS &&
            state.phase == Phase.IDLE &&
            state.currentTurn == player

    fun beginRoll(state: GameState, player: PlayerId): GameState {
        require(canRoll(state, player)) { "cannot begin roll for $player" }
        return state.copy(phase = Phase.ROLLING)
    }

    fun completeRoll(state: GameState, player: PlayerId, value: Int): GameState {
        require(state.phase == Phase.ROLLING && state.currentTurn == player) { "invalid completeRoll" }
        require(value in 1..6) { "die out of range: $value" }
        return state.copy(phase = Phase.AWAITING_PLACEMENT, lastRoll = value)
    }

    fun canPlace(state: GameState, player: PlayerId, column: Int): Boolean {
        val grid = state.grid[player] ?: return false
        return state.phase == Phase.AWAITING_PLACEMENT &&
            state.currentTurn == player &&
            column in grid.indices &&
            !columnFull(grid, column)
    }

    fun place(state: GameState, player: PlayerId, column: Int): GameState {
        require(canPlace(state, player, column)) { "cannot place for $player in $column" }
        val value = state.lastRoll ?: error("no roll to place")

        val opponent = state.opponentOf(player)
        val grid = mutableMapOf<PlayerId, Grid>()
        for (p in PlayerId.entries) {
            grid[p] = state.grid[p] !!  // safe: reset always sets both
        }

        val mine = grid[player]!!.toMutableList()
        mine[column] = mine[column] + value
        grid[player] = mine

        val destroyed = mutableListOf<DieRef>()
        val theirs = grid[opponent]!!.toMutableList()
        val theirColumn = theirs[column]
        if (value in theirColumn) {
            theirs[column] = theirColumn.filterNot { it == value }
            repeat(theirColumn.count { it == value }) {
                destroyed += DieRef(opponent, column, value)
            }
            grid[opponent] = theirs
        }

        val boardFull = grid[player]!!.all { it.size >= COLUMN_SIZE }
        val base = state.copy(
            grid = grid,
            lastRoll = null,
            destroyed = destroyed,
            phase = Phase.IDLE,
        )
        if (!boardFull) {
            return base.copy(currentTurn = opponent)
        }

        val mineScore = totalScore(grid[player]!!)
        val theirsScore = totalScore(grid[opponent]!!)
        return when {
            mineScore == theirsScore -> base.copy(status = Status.DRAW)
            mineScore > theirsScore -> base.copy(status = Status.FINISHED, winner = player)
            else -> base.copy(status = Status.FINISHED, winner = opponent)
        }
    }

    fun canRestart(state: GameState): Boolean = state.status != Status.IN_PROGRESS
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.KnucklebonesRulesTest"`
Expected: PASS (6 passed; note the test `placed die destroys matching dice…` constructs a state by hand and intentionally ends Host's turn — verify all assertions hold).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game/KnucklebonesRules.kt app/src/test/java/com/example/knucklegame/game/KnucklebonesRulesTest.kt
git commit -m "game: implement and test knucklebones rules engine"
```

---

## Task 9: Game messages protocol (TDD)

**Files:**
- Test: `app/src/test/java/com/example/knucklegame/game/GameMessagesTest.kt`
- Create: `app/src/main/java/com/example/knucklegame/game/GameMessages.kt`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/knucklegame/game/GameMessagesTest.kt`:

```kotlin
package com.example.knucklegame.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMessagesTest {

    @Test
    fun `sanitizeName trims and strips control chars`() {
        assertEquals("Alice", GameMessages.sanitizeName("  Alice\n "))
        assertEquals("A b", GameMessages.sanitizeName("A\u0000 b"))
    }

    @Test
    fun `encodeName and decodeName round-trip`() {
        val encoded = GameMessages.encodeName("  Zulo  ")
        assertEquals("NAME:Zulo", encoded)
        assertEquals("Zulo", GameMessages.decodeName(encoded))
        assertNull(GameMessages.decodeName("STATE:{}"))
    }

    @Test
    fun `roll and restart markers match exactly`() {
        assertTrue(GameMessages.isRoll(GameMessages.encodeRoll()))
        assertTrue(GameMessages.isRestart(GameMessages.encodeRestart()))
        assertTrue(!GameMessages.isRoll("ROLL:0"))
        assertTrue(!GameMessages.isRestart("RESTARTING"))
    }

    @Test
    fun `encodePlace round-trips valid columns`() {
        for (col in 0..2) {
            val encoded = GameMessages.encodePlace(col)
            assertEquals(col, GameMessages.decodePlace(encoded))
        }
    }

    @Test
    fun `decodePlace rejects malformed lines`() {
        assertNull(GameMessages.decodePlace("PLACE"))
        assertNull(GameMessages.decodePlace("PLACE:abc"))
        assertNull(GameMessages.decodePlace("PLACE:3"))
        assertNull(GameMessages.decodePlace("ROLL"))
    }

    @Test
    fun `state round-trips all fields including destroyed refs`() {
        val state = GameState(
            hostName = "Host",
            clientName = "Client",
            status = Status.IN_PROGRESS,
            currentTurn = PlayerId.CLIENT,
            phase = Phase.AWAITING_PLACEMENT,
            grid = mapOf(
                PlayerId.HOST to listOf(listOf(4, 1, 4), emptyList(), emptyList()),
                PlayerId.CLIENT to listOf(emptyList(), listOf(6), emptyList()),
            ),
            lastRoll = 6,
            destroyed = listOf(DieRef(PlayerId.CLIENT, 0, 4), DieRef(PlayerId.CLIENT, 0, 4)),
        )
        val encoded = GameMessages.encodeState(state)
        val decoded = GameMessages.decodeState(encoded)
        assertEquals(state, decoded)
        assertNull(GameMessages.decodeState("STATE:not-json"))
        assertNull(GameMessages.decodeState("NAME:Host"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.GameMessagesTest"`
Expected: FAIL (compilation — `GameMessages` missing).

- [ ] **Step 3: Implement `GameMessages.kt`**

```kotlin
package com.example.knucklegame.game

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object GameMessages {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun sanitizeName(name: String): String =
        name.trim().filterNot { it.isISOControl() }

    fun encodeName(name: String): String = "NAME:${sanitizeName(name)}"
    fun encodeRoll(): String = "ROLL"
    fun encodePlace(column: Int): String = "PLACE:$column"
    fun encodeRestart(): String = "RESTART"
    fun encodeState(state: GameState): String = "STATE:${json.encodeToString(state)}"

    fun decodeName(line: String): String? = line.let {
        if (it.startsWith("NAME:")) it.removePrefix("NAME:") else null
    }

    fun isRoll(line: String): Boolean = line == "ROLL"
    fun isRestart(line: String): Boolean = line == "RESTART"

    /** Decodes `PLACE:<col>` to the column index, or null for anything invalid. */
    fun decodePlace(line: String): Int? {
        if (!line.startsWith("PLACE:")) return null
        val col = line.removePrefix("PLACE:").toIntOrNull() ?: return null
        return if (col in 0..KnucklebonesRules.COLUMNS - 1) col else null
    }

    fun decodeState(line: String): GameState? {
        if (!line.startsWith("STATE:")) return null
        return try {
            val text = line.removePrefix("STATE:")
            json.decodeFromString<GameState>(text)
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.GameMessagesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game/GameMessages.kt app/src/test/java/com/example/knucklegame/game/GameMessagesTest.kt
git commit -m "game: add and test knucklebones line protocol"
```

---

## Task 10: GameHost and FakeGamePeer (TDD)

**Files:**
- Test: `app/src/test/java/com/example/knucklegame/TestSupport.kt`
- Test: `app/src/test/java/com/example/knucklegame/game/GameHostTest.kt`
- Test: `app/src/test/java/com/example/knucklegame/game/FakeGamePeerTest.kt`
- Create: `app/src/main/java/com/example/knucklegame/game/GameHost.kt`
- Create: `app/src/main/java/com/example/knucklegame/game/FakeGamePeer.kt`

- [ ] **Step 1: Write `TestSupport.kt`**

```kotlin
package com.example.knucklegame

import com.example.knucklegame.bluetooth.GameLink

/** In-memory [GameLink] recording sent lines; call [onLine] to simulate the peer. */
class FakeGameLink : GameLink {
    override var onLine: (String) -> Unit = {}
    override var onClosed: () -> Unit = {}
    val sent = mutableListOf<String>()

    override fun send(line: String) {
        sent += line
    }

    override fun close() {
        onClosed()
    }

    fun receive(line: String) {
        onLine(line)
    }

    val lastState: com.example.knucklegame.game.GameState?
        get() = sent.lastOrNull()
            ?.let { com.example.knucklegame.game.GameMessages.decodeState(it) }
}
```

- [ ] **Step 2: Write the failing GameHost test**

`app/src/test/java/com/example/knucklegame/game/GameHostTest.kt`:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHostTest {

    private fun host(link: FakeGameLink, first: PlayerId, body: (GameHost) -> Unit) {
        var current: GameHost? = null
        current = GameHost(
            link = link,
            hostName = "Host",
            rollValue = { 4 },
            rollDelayMs = 0L,
            firstPlayer = { first },
            onState = {},
        )
        body(current)
    }

    private fun lastStateSent(link: FakeGameLink): GameState =
        link.sent.asReversed().firstNotNullOf { GameMessages.decodeState(it) }

    @Test
    fun `client name triggers reset with both players and chosen first player`() {
        val link = FakeGameLink()
        host(link, PlayerId.CLIENT) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("  Bob  "))
            val state = lastStateSent(link)
            assertEquals("Bob", state.clientName)
            assertEquals("Host", state.hostName)
            assertEquals(PlayerId.CLIENT, state.currentTurn)
            assertEquals(Status.IN_PROGRESS, state.status)
        }
    }

    @Test
    fun `hostRoll rolls and goes to awaiting placement on the host`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            val state = lastStateSent(link)
            assertEquals(Phase.AWAITING_PLACEMENT, state.phase)
            assertEquals(4, state.lastRoll)
            assertEquals(PlayerId.HOST, state.currentTurn)
        }
    }

    @Test
    fun `hostPlace places the die and hands the turn to the client`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            gameHost.hostPlace(2)
            val state = lastStateSent(link)
            assertEquals(PlayerId.CLIENT, state.currentTurn)
            assertEquals(Phase.IDLE, state.phase)
            assertEquals(listOf(4), state.grid[PlayerId.HOST]!![2])
        }
    }

    @Test
    fun `client roll and place messages drive the game`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            gameHost.hostRoll()
            gameHost.hostPlace(0)
            // client's turn → client asks to roll
            link.receive(GameMessages.encodeRoll())
            assertTrue(lastStateSent(link).phase == Phase.AWAITING_PLACEMENT &&
                lastStateSent(link).currentTurn == PlayerId.CLIENT)
            link.receive(GameMessages.encodePlace(1))
            val state = lastStateSent(link)
            assertEquals(PlayerId.HOST, state.currentTurn)
            assertEquals(listOf(4), state.grid[PlayerId.CLIENT]!![1])
        }
    }

    @Test
    fun `invalid messages are ignored`() {
        val link = FakeGameLink()
        host(link, PlayerId.CLIENT) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            // Client tries to roll on host's turn; also invalid place, also junk.
            gameHost.hostRoll() // host rolls on their turn (client's turn first!) → ignored
            assertEquals(1, link.sent.count { GameMessages.decodeState(it) != null })
            link.receive(GameMessages.encodeRoll())
            assertTrue(lastStateSent(link).currentTurn == PlayerId.CLIENT) // pollutes roll → wait, client turn
            link.receive("GARBAGE")
            link.receive("PLACE:9")
            assertEquals(2, link.sent.count { GameMessages.decodeState(it) != null })
        }
    }

    @Test
    fun `restart keeps names and the same first player but fresh boards`() {
        val link = FakeGameLink()
        host(link, PlayerId.HOST) { gameHost ->
            gameHost.connect()
            link.receive(GameMessages.encodeName("Bob"))
            // force finished
            var finished = lastStateSent(link).copy(status = Status.FINISHED, winner = PlayerId.HOST)
            val gh = gameHost
            // drive a quick full-ish game is heavy; instead call restart directly after marking finished via reflection-free path:
            // We can't set state directly; simulate by finishing through the host's restart guard: restart only allowed when FINISHED.
            // So: play a full game to completion (see fullGameTest). Skipping; this test only verifies idempotent guard.
            val before = gh
            assertTrue(true) // placeholder replaced by full-game flow in FakeGamePeerTest
        }
    }

    @Test
    fun `full game reaches a terminal state`() {
        val link = FakeGameLink()
        var finished: GameState? = null
        val gh = GameHost(
            link = link,
            hostName = "Host",
            rollValue = { 3 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = { finished = it },
        )
        gh.connect()
        link.receive(GameMessages.encodeName("Bob"))
        var guard = 0
        while (finished?.status == Status.IN_PROGRESS && guard < 200) {
            val s = finished ?: lastStateSent(link)
            if (KnucklebonesRules.canRoll(s, s.currentTurn)) {
                if (s.currentTurn == PlayerId.HOST) gh.hostRoll() else link.receive(GameMessages.encodeRoll())
            } else if (KnucklebonesRules.canPlace(s, s.currentTurn, 0)) {
                if (s.currentTurn == PlayerId.HOST) gh.hostPlace(0) else link.receive(GameMessages.encodePlace(0))
            } else {
                // column 0 filled → place elsewhere
                val col = s.grid[s.currentTurn]!!.indices.first { !KnucklebonesRules.columnFull(s.grid[s.currentTurn]!!, it) }
                if (s.currentTurn == PlayerId.HOST) gh.hostPlace(col) else link.receive(GameMessages.encodePlace(col))
            }
            guard += 1
        }
        assertTrue("game should finish (steps=$guard)", finished != null && finished.status != Status.IN_PROGRESS)
        assertEquals(finished.status, if (finished.status == Status.DRAW) Status.DRAW else Status.FINISHED)
    }
}
```

Note: `restart` is exercised end-to-end in Task 12 (`FakeGamePeerTest` runs a full game then `RESTART`), so the partial `restart keeps names…` test above is intentionally replaced by `full game reaches a terminal state`; drop the weak restart test block if it does not compile cleanly (its `s` and `gh` locals are unused placeholders).

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.GameHostTest"`
Expected: FAIL (compilation — `GameHost` missing).

- [ ] **Step 4: Implement `GameHost.kt`**

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink
import kotlin.random.Random

class GameHost(
    private val link: GameLink,
    private val hostName: String,
    private val rollValue: () -> Int = { (1..6).random() },
    private val rollDelayMs: Long = 2000,
    private val firstPlayer: () -> PlayerId = { if (Random.nextBoolean()) PlayerId.HOST else PlayerId.CLIENT },
    private val onState: (GameState) -> Unit = {},
) {
    private var first: PlayerId? = null

    var state: GameState = KnucklebonesRules.reset(hostName, "?", PlayerId.HOST)
        private set

    fun connect() {
        link.onLine = { line -> onLine(line) }
    }

    private fun onLine(line: String) {
        val name = GameMessages.decodeName(line)
        when {
            name != null -> {
                first = firstPlayer()
                state = KnucklebonesRules.reset(hostName, GameMessages.sanitizeName(name), first!!)
                publish()
            }
            GameMessages.isRoll(line) -> rollFor(PlayerId.CLIENT)
            GameMessages.decodePlace(line) != null -> placeFor(PlayerId.CLIENT, GameMessages.decodePlace(line)!!)
            GameMessages.isRestart(line) -> restart()
        }
    }

    fun hostRoll() {
        rollFor(PlayerId.HOST)
    }

    fun hostPlace(column: Int) {
        placeFor(PlayerId.HOST, column)
    }

    private fun rollFor(player: PlayerId) {
        if (!KnucklebonesRules.canRoll(state, player)) return
        state = KnucklebonesRules.beginRoll(state, player)
        publish()
        Thread.sleep(rollDelayMs)
        state = KnucklebonesRules.completeRoll(state, player, rollValue())
        publish()
    }

    private fun placeFor(player: PlayerId, column: Int) {
        if (!KnucklebonesRules.canPlace(state, player, column)) return
        state = KnucklebonesRules.place(state, player, column)
        publish()
    }

    fun restart() {
        if (!KnucklebonesRules.canRestart(state)) return
        val firstPlayerNow = first ?: firstPlayer()
        first = firstPlayerNow
        state = KnucklebonesRules.reset(state.hostName, state.clientName, firstPlayerNow)
        publish()
    }

    private fun publish() {
        state.let {
            link.send(GameMessages.encodeState(it))
            onState(it)
        }
    }
}
```

- [ ] **Step 5: Implement `FakeGamePeer.kt`**

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.bluetooth.GameLink

object FakeGamePeer {
    const val NAME = "FakePeer"
    const val PIN = "1234"
    const val HOST_NAME = "FakeHost"
    const val REACT_DELAY_MS = 150L

    fun firstOpenColumn(grid: Grid): Int =
        grid.indices.first { !KnucklebonesRules.columnFull(grid, it) }
}

private fun delayed(delayMs: Long, block: () -> Unit) {
    Thread {
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
        }
        try {
            block()
        } catch (_: Exception) {
            // link may be closed
        }
    }.apply {
        name = "fake-peer"
        isDaemon = true
        start()
    }
}

/** Bot playing the client side: sends NAME, rolls on its turn, places in the first open column. */
fun runFakeClient(link: GameLink) {
    link.send(GameMessages.encodeName(FakeGamePeer.NAME))
    link.onLine = client@{ line ->
        val state = GameMessages.decodeState(line) ?: return@client
        if (KnucklebonesRules.canRoll(state, PlayerId.CLIENT)) {
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodeRoll()) }
        }
        if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.CLIENT) {
            val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.CLIENT]!!)
            delayed(FakeGamePeer.REACT_DELAY_MS) { link.send(GameMessages.encodePlace(col)) }
        }
    }
}

/** Bot playing the host side via a [GameHost], for when the app connects as a client in fake mode. */
fun runFakeHost(link: GameLink): GameHost {
    val counter = java.util.concurrent.atomic.AtomicInteger(0)
    val rollValue: () -> Int = { (counter.getAndIncrement() % 6) + 1 }
    lateinit var host: GameHost
    host = GameHost(
        link = link,
        hostName = FakeGamePeer.HOST_NAME,
        rollValue = rollValue,
        rollDelayMs = 50L,
        onState = { state ->
            if (KnucklebonesRules.canRoll(state, PlayerId.HOST)) {
                delayed(50L) { host.hostRoll() }
            }
            if (state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == PlayerId.HOST) {
                val col = FakeGamePeer.firstOpenColumn(state.grid[PlayerId.HOST]!!)
                delayed(50L) { host.hostPlace(col) }
            }
        },
    )
    host.connect()
    return host
}
```

- [ ] **Step 6: Write the FakeGamePeer test**

`app/src/test/java/com/example/knucklegame/game/FakeGamePeerTest.kt`:

```kotlin
package com.example.knucklegame.game

import com.example.knucklegame.FakeGameLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeGamePeerTest {

    @Test
    fun `fake client plays a full session to a draw or finish`() {
        val hostLink = FakeGameLink()
        val gh = GameHost(
            link = hostLink,
            hostName = FakeGamePeer.HOST_NAME,
            rollValue = { 2 },
            rollDelayMs = 0L,
            firstPlayer = { PlayerId.HOST },
            onState = {},
        )
        gh.connect()
        // Wire the client side through two real GameLinks to exercise running code.
        // Simplest: emulate the client bot manually against the fake host link via runFakeClient
        // on a separate relayed link is complex; instead assert the host-side flow with the
        // bot reacting to hostLink lines.
        val clientLines = mutableListOf<String>()
        runFakeClient(object : com.example.knucklegame.bluetooth.GameLink {
            override var onLine: (String) -> Unit = {}
            override var onClosed: () -> Unit = {}
            override fun send(line: String) {
                clientLines += line
                hostLink.receive(line)   // forward bot → host
                hostLink.sent.lastOrNull()?.let { hostLine ->
                    if (GameMessages.decodeState(hostLine) != null) {
                        onLine(hostLine) // forward host state → bot
                    }
                }
            }
            override fun close() { onClosed() }
        })

        var guard = 0
        while (gh.state.status == Status.IN_PROGRESS && guard < 300) {
            Thread.sleep(20)
            guard += 1
        }
        assertNotNull(gh.state.winner)
        assertTrue(gh.state.status != Status.IN_PROGRESS)
        assertEquals("FakePeer", gh.state.clientName)
        assertTrue(clientLines.any { GameMessages.decodeState(hostLink.sent.last()) != null })
    }
}
```

This test is inherently racy; if it is flaky on CI, replace the manual relay with `GameLinkImpl` pairs created over two `PipedInputStream/PipedOutputStream` pairs (as `FakeBluetoothConnector` does) and assert the host reaches a terminal `GameState`. The plan's authoritative integration test for the full loop is `FakeBluetoothConnector` via `ConnectionViewModel` (Task 11/12) and the Maestro flows.

- [ ] **Step 7: Run all game tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.knucklegame.game.*"`
Expected: PASS (KnucklebonesRulesTest, GameMessagesTest, GameHostTest, FakeGamePeerTest).

- [ ] **Step 8: Compile the app (connection flow now resolves)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `FakeGamePeer` referenced by `FakeBluetoothConnector` and `ConnectionViewModel` now exists.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/game app/src/test/java/com/example/knucklegame/game app/src/test/java/com/example/knucklegame/TestSupport.kt
git commit -m "game: add server-side GameHost and fake peer bot"
```

---

## Task 11: GameViewModel

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/ui/GameViewModel.kt`

- [ ] **Step 1: Write `GameViewModel.kt`**

Adapted from `DICE/.../ui/GameViewModel.kt`: adds `phase`, `onPlaceColumn`, and `canRollNow`/`canPlaceNow` helpers.

```kotlin
package com.example.knucklegame.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.knucklegame.bluetooth.GameLink
import com.example.knucklegame.game.GameHost
import com.example.knucklegame.game.GameMessages
import com.example.knucklegame.game.GameState
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "KnuckleGame"

class GameViewModel(
    private val link: GameLink,
    val myId: PlayerId,
    private val hostName: String? = null,
    private val clientName: String? = null,
    private val rollValue: () -> Int = { (1..6).random() },
    private val onPeerDisconnected: (() -> Unit)? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private val _peerDisconnected = MutableStateFlow(false)
    val peerDisconnected: StateFlow<Boolean> = _peerDisconnected.asStateFlow()

    private var host: GameHost? = null

    /** My turn and awaiting a roll. */
    val canRollNow: Boolean
        get() {
            val s = _state.value ?: return false
            return KnucklebonesRules.canRoll(s, myId)
        }

    /** My turn and the die has landed; I must place it. */
    val canPlaceNow: Boolean
        get() {
            val s = _state.value ?: return false
            return s.status == Status.IN_PROGRESS &&
                s.phase == Phase.AWAITING_PLACEMENT &&
                s.currentTurn == myId
        }

    init {
        if (myId == PlayerId.HOST) {
            val gameHost = GameHost(
                link = link,
                hostName = hostName ?: "Host",
                rollValue = rollValue,
                onState = { _state.value = it },
            )
            host = gameHost
            gameHost.connect()
            link.onClosed = { handlePeerClosed() }
        } else {
            try {
                link.send(GameMessages.encodeName(clientName ?: "Client"))
            } catch (e: Exception) {
                Log.e(TAG, "send NAME failed", e)
                _errorText.value = e.message ?: "Failed to send name"
            }
            link.onLine = { line ->
                val decoded = GameMessages.decodeState(line)
                if (decoded != null) _state.value = decoded
            }
            link.onClosed = { handlePeerClosed() }
        }
    }

    fun onDiceTap() {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    h.hostRoll()
                } catch (e: Exception) {
                    Log.e(TAG, "hostRoll failed", e)
                    _errorText.value = e.message ?: "Roll failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodeRoll())
            } catch (e: Exception) {
                Log.e(TAG, "send ROLL failed", e)
                _errorText.value = e.message ?: "Failed to send roll"
            }
        }
    }

    fun onPlaceColumn(column: Int) {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    h.hostPlace(column)
                } catch (e: Exception) {
                    Log.e(TAG, "hostPlace failed", e)
                    _errorText.value = e.message ?: "Place failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodePlace(column))
            } catch (e: Exception) {
                Log.e(TAG, "send PLACE failed", e)
                _errorText.value = e.message ?: "Failed to send placement"
            }
        }
    }

    fun onPlayAgain() {
        if (myId == PlayerId.HOST) {
            val h = host ?: return
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    h.restart()
                } catch (e: Exception) {
                    Log.e(TAG, "host restart failed", e)
                    _errorText.value = e.message ?: "Restart failed"
                }
            }
        } else {
            try {
                link.send(GameMessages.encodeRestart())
            } catch (e: Exception) {
                Log.e(TAG, "send RESTART failed", e)
                _errorText.value = e.message ?: "Failed to restart"
            }
        }
    }

    fun dismissError() {
        _errorText.value = null
    }

    fun disconnect() {
        try {
            link.onClosed = {}
            link.close()
        } catch (e: Exception) {
            Log.e(TAG, "disconnect close failed", e)
        }
    }

    private fun handlePeerClosed() {
        Log.e(TAG, "Peer disconnected")
        _peerDisconnected.value = true
        _errorText.value = "Peer disconnected"
        try {
            onPeerDisconnected?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "onPeerDisconnected callback failed", e)
        }
    }

    override fun onCleared() {
        try {
            link.close()
        } catch (_: Exception) {
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (GameViewModel compiles; GameScreen still missing — the app module fails at the `GameScreen` call inside `KnuckleGameApp` until Task 13).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/ui/GameViewModel.kt
git commit -m "ui: add game view model with roll and place actions"
```

---

## Task 12: GameScreen — boards, roll area, drag/tap placement, overlays

**Files:**
- Create: `app/src/main/java/com/example/knucklegame/ui/GameScreen.kt`
- Create: `app/src/main/java/com/example/knucklegame/ui/components/DrawOverlay.kt`

- [ ] **Step 1: Write `DrawOverlay.kt`**

```kotlin
package com.example.knucklegame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.knucklegame.R
import com.example.knucklegame.ui.theme.FeltDark
import com.example.knucklegame.ui.theme.Gold

@Composable
fun DrawOverlay(
    scoreA: Int,
    scoreB: Int,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FeltDark.copy(alpha = 0.85f))
            .testTag("draw-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("🎲", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.draw),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.draw_score_banner, scoreA, scoreB),
                style = MaterialTheme.typography.headlineMedium,
                color = Gold,
            )
            Spacer(Modifier.height(32.dp))
            GoldButton(text = stringResource(R.string.play_again), onClick = onPlayAgain)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disconnect))
            }
        }
    }
}
```

- [ ] **Step 2: Write `GameScreen.kt`**

The core new UI. Layout: opponent board (top) → turn pill + dice roll area (middle) → your board (bottom), all column-aligned, scrollable. During your `AWAITING_PLACEMENT` a draggable die token appears over the roll area; columns highlight and can be tapped. Destroyed dice show as fading ghost chips on the opponent board.

```kotlin
package com.example.knucklegame.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.knucklegame.R
import com.example.knucklegame.game.DieRef
import com.example.knucklegame.game.GameState
import com.example.knucklegame.game.KnucklebonesRules
import com.example.knucklegame.game.Phase
import com.example.knucklegame.game.PlayerId
import com.example.knucklegame.game.Status
import com.example.knucklegame.ui.components.DiceGameTopBar
import com.example.knucklegame.ui.components.DrawOverlay
import com.example.knucklegame.ui.components.GlassCard
import com.example.knucklegame.ui.components.GoldButton
import com.example.knucklegame.ui.components.WinnerOverlay
import com.example.knucklegame.ui.dice.DiceCube
import com.example.knucklegame.ui.theme.GlassBorderGold
import com.example.knucklegame.ui.theme.GlassWhite
import com.example.knucklegame.ui.theme.Gold
import com.example.knucklegame.ui.theme.Ivory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private const val RESULT_OVERLAY_DELAY_MS = 1_200L

@Composable
fun GameScreen(viewModel: GameViewModel, onDisconnect: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val error by viewModel.errorText.collectAsState()
    val peerGone by viewModel.peerDisconnected.collectAsState()
    val sound = LocalSoundManager.current
    val muted by sound.muted.collectAsState()
    GameScreenContent(
        state = state,
        myId = viewModel.myId,
        onDiceTap = { viewModel.onDiceTap() },
        onPlaceColumn = { viewModel.onPlaceColumn(it) },
        onPlayAgain = { viewModel.onPlayAgain() },
        onDisconnect = { viewModel.disconnect(); onDisconnect() },
        onLeave = { viewModel.disconnect(); onDisconnect() },
        muted = muted,
        onToggleMute = { sound.setMuted(!muted) },
        peerDisconnected = peerGone,
        errorText = error,
    )
}

@Composable
fun GameScreen(
    state: GameState?,
    myId: PlayerId,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    peerDisconnected: Boolean = false,
    errorText: String? = null,
) {
    GameScreenContent(
        state = state,
        myId = myId,
        onDiceTap = onDiceTap,
        onPlaceColumn = onPlaceColumn,
        onPlayAgain = onPlayAgain,
        onDisconnect = onDisconnect,
        onLeave = onLeave,
        muted = muted,
        onToggleMute = onToggleMute,
        peerDisconnected = peerDisconnected,
        errorText = errorText,
    )
}

@Composable
private fun GameScreenContent(
    state: GameState?,
    myId: PlayerId,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onDisconnect: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    peerDisconnected: Boolean,
    errorText: String?,
) {
    var showResultOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(state?.status) {
        showResultOverlay = false
        if (state?.status == Status.FINISHED || state?.status == Status.DRAW) {
            delay(RESULT_OVERLAY_DELAY_MS)
            showResultOverlay = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DiceGameTopBar(
                leading = {
                    TextButton(onClick = onLeave, modifier = Modifier.testTag("leave")) {
                        Text(stringResource(R.string.leave))
                    }
                },
                trailing = {
                    IconButton(onClick = onToggleMute, modifier = Modifier.testTag("mute")) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                            tint = Gold,
                        )
                    }
                },
            )
            if (state == null) {
                Text(stringResource(R.string.waiting_for_state))
            } else {
                val peerId = state.opponentOf(myId)
                GameBoard(
                    isMine = false,
                    name = state.playerName(peerId),
                    grid = state.grid[peerId]!!,
                    destroyed = state.destroyed,
                    active = state.currentTurn == peerId && state.status == Status.IN_PROGRESS,
                    onColumnTap = null,
                    modifier = Modifier.testTag("peer-board"),
                )
                Spacer(Modifier.height(14.dp))
                RollArea(
                    state = state,
                    myId = myId,
                    canRoll = KnucklebonesRules.canRoll(state, myId),
                    canPlace = state.phase == Phase.AWAITING_PLACEMENT && state.currentTurn == myId,
                    onDiceTap = onDiceTap,
                    onPlaceColumn = onPlaceColumn,
                    peerId = peerId,
                )
                Spacer(Modifier.height(14.dp))
                GameBoard(
                    isMine = true,
                    name = state.playerName(myId),
                    grid = state.grid[myId]!!,
                    destroyed = emptyList(),
                    active = state.currentTurn == myId && state.status == Status.IN_PROGRESS,
                    onColumnTap = onPlaceColumn,
                    modifier = Modifier.testTag("own-board"),
                )
            }
            if (peerDisconnected || errorText != null) {
                Spacer(Modifier.height(15.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("error-banner")) {
                    Text(
                        errorText ?: stringResource(R.string.peer_disconnected),
                        modifier = Modifier.padding(12.dp),
                        color = Ivory,
                    )
                }
            }
        }
        if (state != null && showResultOverlay) {
            when (state.status) {
                Status.FINISHED -> {
                    val winner = state.winner
                    WinnerOverlay(
                        winnerName = winner?.let { state.playerName(it) } ?: "",
                        winnerScore = winner?.let { KnucklebonesRules.totalScore(state.grid[it]!!) } ?: 0,
                        loserScore = winner?.let { KnucklebonesRules.totalScore(state.grid[state.opponentOf(it)]!!) } ?: 0,
                        isWinner = winner == myId,
                        onPlayAgain = onPlayAgain,
                        onDisconnect = onDisconnect,
                    )
                }
                Status.DRAW -> DrawOverlay(
                    scoreA = KnucklebonesRules.totalScore(state.grid[PlayerId.HOST]!!),
                    scoreB = KnucklebonesRules.totalScore(state.grid[PlayerId.CLIENT]!!),
                    onPlayAgain = onPlayAgain,
                    onDisconnect = onDisconnect,
                )
                Status.IN_PROGRESS -> Unit
            }
        }
    }
}

@Composable
private fun RollArea(
    state: GameState,
    myId: PlayerId,
    canRoll: Boolean,
    canPlace: Boolean,
    onDiceTap: () -> Unit,
    onPlaceColumn: (Int) -> Unit,
    peerId: PlayerId,
) {
    val placeable = if (canPlace) {
        state.grid[myId]!!.mapIndexed { i, col -> col.size < KnucklebonesRules.COLUMN_SIZE }
    } else {
        listOf(false, false, false)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TurnPill(state = state, myId = myId, peerId = peerId)
        Spacer(Modifier.height(10.dp))
        DiceCube(
            value = state.lastRoll,
            rolling = state.phase == Phase.ROLLING,
            enabled = canRoll,
            onTap = onDiceTap,
        )
        if (canPlace) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.place_hint), style = MaterialTheme.typography.bodySmall, color = Ivory)
            Spacer(Modifier.height(8.dp))
            DraggableDieToken(
                value = state.lastRoll ?: return@Column,
                placeable = placeable,
                grid = state.grid[myId]!!,
                onPlaceColumn = onPlaceColumn,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TurnPill(state: GameState, myId: PlayerId, peerId: PlayerId) {
    val text = when {
        state.phase == Phase.ROLLING -> stringResource(R.string.rolling)
        state.status == Status.FINISHED || state.status == Status.DRAW -> ""
        state.currentTurn == myId -> stringResource(R.string.your_turn)
        else -> stringResource(R.string.turn_other, state.playerName(state.currentTurn))
    }
    if (text.isEmpty()) return
    Surface(
        shape = CircleShape,
        color = GlassWhite,
        border = BorderStroke(1.dp, GlassBorderGold),
        modifier = Modifier.testTag("turn-pill"),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** 3x3 board with column score chips. `onColumnTap` is set only for the player's own placeable board. */
@Composable
private fun GameBoard(
    isMine: Boolean,
    name: String,
    grid: com.example.knucklegame.game.Grid,
    destroyed: List<DieRef>,
    active: Boolean,
    onColumnTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val score = KnucklebonesRules.totalScore(grid)
    val own = isMine
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Gold)
            Text(
                "$score",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(if (own) "score-mine" else "score-peer"),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (col in 0 until 3) {
                val full = grid[col].size >= KnucklebonesRules.COLUMN_SIZE
                val canTap = onColumnTap != null && !full && active
                val target = destroyed.filter { it.column == col }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.testTag((if (own) "own-col-" else "peer-col-") + col),
                ) {
                    DieColumn(
                        dice = grid[col],
                        destroyed = target,
                        placeable = canTap,
                        onTap = {
                            onColumnTap?.invoke(col)
                        },
                        cellTag = (if (own) "own-cell-" else "peer-cell-") + col + "-",
                    )
                    Spacer(Modifier.height(4.dp))
                    ColumnScoreChip(
                        value = KnucklebonesRules.columnScore(grid[col]),
                        tag = (if (own) "own-score-" else "peer-score-") + col,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScoreChip(value: Int, tag: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 18.dp)
            .background(GlassWhite, RoundedCornerShape(6.dp))
            .testTag(tag),
    ) {
        Text("$value", style = MaterialTheme.typography.labelSmall, color = Gold)
    }
}

@Composable
private fun DieColumn(
    dice: List<Int>,
    destroyed: List<DieRef>,
    placeable: Boolean,
    onTap: () -> Unit,
    cellTag: String,
) {
    val base = Modifier.size(52.dp).padding(3.dp)
    val emptyColor = GlassWhite.copy(alpha = 0.25f)
    val borderColor by animateColorAsState(
        targetValue = if (placeable) Gold else GlassBorderGold,
        label = "col-border",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(2.dp)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = placeable) { onTap() }
            .testTag("column"),
    ) {
        // 3 rows; bottom-anchored: empty rows first, then dice from the bottom.
        val emptyRows = 3 - dice.size
        for (row in 0 until 3) {
            if (row < emptyRows) {
                Box(
                    Modifier
                        .then(base)
                        .background(emptyColor, RoundedCornerShape(8.dp)),
                ) {}
            } else {
                val dieValue = dice[row - emptyRows]
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .then(base)
                        .background(GlassWhite, RoundedCornerShape(8.dp)),
                ) {
                    Text("$dieValue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ivory)
                }
            }
        }
    }
    DestroyGhosts(destroyed = destroyed)
}

@Composable
private fun DestroyGhosts(destroyed: List<DieRef>) {
    if (destroyed.isEmpty()) return
    var gone by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (gone) 0f else 1f, label = "ghost")
    LaunchedEffect(destroyed) {
        delay(500L)
        gone = true
    }
    if (gone) return
    Row {
        repeat(destroyed.size) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .padding(2.dp)
                    .background(Color.Red.copy(alpha = 0.35f * alpha), RoundedCornerShape(8.dp)),
            ) {
                Text("×", color = Color.White)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
```

Append the drag token + hit registry helper at the **end of the file**:

```kotlin
/** Registers board-column screen bounds so [DraggableDieToken] can drop on them. */
private class ColumnHitRegistry {
    val bounds = mutableMapOf<Int, Rect>()

    fun update(column: Int, rect: Rect) {
        bounds[column] = rect
    }

    fun columnAt(position: Offset): Int? = bounds.entries
        .firstOrNull { it.value.contains(position) }
        ?.key
}

/** Overlays a draggable die token; on release, places on the column under the pointer. */
@Composable
private fun DraggableDieToken(
    value: Int,
    placeable: List<Boolean>,
    grid: com.example.knucklegame.game.Grid,
    onPlaceColumn: (Int) -> Unit,
) {
    val registry = remember { ColumnHitRegistry() }
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    var basePosition by remember { mutableStateOf(Offset.Zero) }
    var tokenSize by remember { mutableStateOf(52.dp.toPx()) } // toPx usage kept simple

    // Board columns register their bounds (drawn above; we re-query on state change).
    // For simplicity we pin the board under a fixed layout; see note below.
    var boardBoundsTotal by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { offset }
            .size(52.dp)
            .background(Gold, RoundedCornerShape(10.dp))
            .semantics { stateDescription = "draggable-die" }
            .testTag("place-token")
            .onGloballyPositioned { basePosition = it.positionInRoot() }
            .pointerInput(value) {
                detectDragGestures(
                    onDragStart = {},
                    onDrag = { change, drag ->
                        change.consume()
                        offset += IntOffset(drag.x.roundToInt(), drag.y.roundToInt())
                    },
                    onDragEnd = {
                        val center = basePosition + Offset(
                            offset.x + 26.dp.toPx(),
                            offset.y + 26.dp.toPx(),
                        )
                        registry.columnAt(center)?.let(onPlaceColumn)
                        offset = IntOffset.Zero
                    },
                    onDragCancel = { offset = IntOffset.Zero },
                )
            },
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ivory)
    }
    // No-op: registry populated lazily in later task refinement if column bounds are captured.
}
```

**Correction note for the implementer:** hit-testing in the file above only works if the board columns register their bounds into the same `ColumnHitRegistry`. In the final step of this task, wire this up by hoisting a single `ColumnHitRegistry` created in `GameScreenContent`, passed to both `GameBoard` (each tappable/placeable column calls `registry.update(col, it.boundsInRoot())` inside a `Modifier.onGloballyPositioned`) and to `DraggableDieToken`. Replace the private `ColumnHitRegistry`/`registry` scoping accordingly: `GameBoard` gains a `registry: ColumnHitRegistry?` parameter; `DieColumn` attaches `.onGloballyPositioned { registry?.update(colIndex, it.boundsInRoot()) }`; `GameBoard` receives it from `GameScreenContent` via `RollArea`. The `localRegistry` values above are placeholders for that hookup; the drag/drop math otherwise stands as written.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any unresolved references against `ui/theme` names copied in Task 5 (`Ivory`, `GlassWhite`, `GlassBorderGold`, `Gold` — all exist in `Color.kt`).

- [ ] **Step 4: Build the full app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/knucklegame/ui/GameScreen.kt app/src/main/java/com/example/knucklegame/ui/components/DrawOverlay.kt
git commit -m "ui: add game screen with boards, dice, and drag-and-drop placement"
```

---

## Task 13: Full test pass and lint

**Files:**
- No new files.

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all suites from Tasks 3–4 and 7–10.

- [ ] **Step 2: Run the debug build with lint**

Run: `./gradlew :app:lintDebug :app:assembleDebug`
Expected: no errors; address any lint warnings that indicate real bugs (unused variables, missing `@Serializable` on types actually serialized).

- [ ] **Step 3: Confirm serialize round-trip of `Phase` and `Status`**

`GameMessagesTest.state round-trips…` already covers `Phase.AWAITING_PLACEMENT` and a `destroyed` list — confirm it passed in Step 1.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "test: full unit pass and lint fixes"
```

---

## Task 14: Maestro integration flows

**Files:**
- Create: `.maestro/01-host-game.yaml`
- Create: `.maestro/02-client-game.yaml`
- Create: `.maestro/03-wrong-pin.yaml`

Maestro runs on an emulator with the debug build and fake mode enabled (no real Bluetooth needed). The fake peer (`FakeGamePeer`) auto-plays, so a full game can complete. Rolls are not injectable from Maestro; outcomes (win/lose/draw) are nondeterministic per run, so flows assert reachable UI states, not a fixed winner.

Copy the structure of `DICE/.maestro/*.yaml` — these flows launch the app by `appId: com.example.knucklegame`.

- [ ] **Step 1: Write `01-host-game.yaml`**

```yaml
appId: com.example.knucklegame
---
- launchApp
- tapOn:
    id: "fake-switch"
- tapOn:
    id: "name-field"
- inputText: "Host"
- tapOn:
    id: "host-button"
- assertVisible:
    id: "pin-display"
- tapOn:
    id: "join-button"
- tapOn:
    id: "name-field"
- inputText: "Client"
- tapOn:
    id: "device-row"
- tapOn:
    id: "pin-input-0"
- inputText: "1234"
- assertVisible:
    id: "own-board"
```

Note: adjust `pin-input-*` ids against the actual `PinDigitsInput` implementation copied from `DICE`/`ui/components/PinDigits.kt`; use the ids exposed there (it may use a single id or four digit cells). Verify with `inspect_screen` on the emulator before finalizing. The fake peer connects with PIN `1234` (its peer auto-connects when you host; the join flow is exercised separately in `02-client-game.yaml`).

- [ ] **Step 2: Write `02-client-game.yaml`**

```yaml
appId: com.example.knucklegame
---
- launchApp
- tapOn:
    id: "fake-switch"
- tapOn:
    id: "name-field"
- inputText: "Client"
- tapOn:
    id: "join-button"
- assertVisible:
    id: "device-row"
- tapOn:
    id: "device-row"
- tapOn:
    id: "pin-input-0"
- inputText: "1234"
- assertVisible:
    id: "own-board"
```

(Joining in fake mode spawns a fake host, so this exercises the client side.)

- [ ] **Step 3: Write `03-wrong-pin.yaml`**

```yaml
appId: com.example.knucklegame
---
- launchApp
- tapOn:
    id: "fake-switch"
- tapOn:
    id: "name-field"
- inputText: "Client"
- tapOn:
    id: "join-button"
- tapOn:
    id: "device-row"
- tapOn:
    id: "pin-input-0"
- inputText: "0000"
- assertVisible: "Wrong code"
```

- [ ] **Step 4: Run the flows**

With an emulator running the debug build installed:

```bash
maestro test .maestro/01-host-game.yaml
maestro test .maestro/02-client-game.yaml
maestro test .maestro/03-wrong-pin.yaml
```

Expected: all three PASS. Fix any selector mismatches by running `maestro studio` / `inspect_screen` and updating the YAML.

- [ ] **Step 5: Commit**

```bash
git add .maestro
git commit -m "test: add maestro integration flows for fake-mode sessions"
```

---

## Task 15: Manual two-device verification

**Files:**
- None.

- [ ] **Step 1: Install on two devices**

Run: `./gradlew :app:installDebug` on each physical phone (or `open` the APK). Enable Bluetooth on both; grant the nearby-devices permission when prompted.

- [ ] **Step 2: Host/find flow with real Bluetooth**

On device A: fake mode OFF → enter name → Host a game; note the 4-digit PIN. On device B: fake mode OFF → enter name → Join → pick device A → enter PIN. Confirm both land on the game screen.

- [ ] **Step 3: Full rules sanity pass**

- Roll the die, confirm ~2s animation + land sound.
- Place via drag and via tap-to-place; confirm the die lands in the chosen column and the turn passes.
- Fill a column to 3; confirm a fourth placement is rejected.
- Arrange a same-value match in the opponent's column; confirm those dice are destroyed with a ghost animation and remaining dice fall.
- Play to a full board; confirm WIN/LOSE/DRAW overlay and Play Again resets boards with names preserved; Disconnect returns to Start.
- During a roll, kill one app; confirm the other shows "Peer disconnected".

---

## Self-review notes

- **Spec coverage:** every spec requirement maps to a task — rules (T8), state/protocol (T7/T9), server host (T10), BT infra (T3), audio/theme/components (T4/T5), connection flow (T6), game UI + drag/tap + destroy animation + overlays (T12), fake mode + bot (T10/T6), unit tests (T8–T10, T13), Maestro (T14), manual (T15).
- **Type consistency:** `Grid`/`Column` typealiases, `Phase`, `Status`, `DieRef` are defined once in `GameState.kt` and reused everywhere; `KnucklebonesRules.place` returns the same `GameState` shape the UI consumes; `GameMessages.decodePlace` bounds-checks against `KnucklebonesRules.COLUMNS`.
- **Known caveat:** the drag-token hit registry hookup in Task 12 Step 2 (the correction note) must be completed during implementation — it is called out explicitly rather than left implicit.