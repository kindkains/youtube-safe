# youtube-safe Android `core` + Eltern-App — Implementierungsplan (Plan 2 von 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die gemeinsamen Android-Bausteine (`core`, `core-android`) und die komplette Eltern-App aus der Spec (`docs/superpowers/specs/2026-11-08-youtube-safe-design.md`, Abschnitte 4, 5, 7, 8, 10), lauffähig gegen den deployten Server `https://safetube.isoletta.me`.

**Architecture:** Drei Gradle-Module unter `android/`. `core` ist **reines Kotlin/JVM** (kein Android) und enthält alles, was sich ohne Gerät testen lässt: API-Modelle und -Client, `ContentGate`, YouTube-Link-Erkennung, ntfy-Datenstrom, Push-Auswertung, Anzeigeformate und die Anbindung an NewPipeExtractor. `core-android` enthält die Android-Teile, die auch die Kinder-App (Plan 3) braucht: Theme, Vorschau-Player (Media3), Push-Vordergrunddienst, Akku-Ausnahme. `parent` ist die Eltern-App (Jetpack Compose, ViewModels mit `StateFlow`, manuelle Abhängigkeiten über einen `AppContainer`, kein DI-Framework). Jeder Bildschirm besteht aus einem ViewModel (JVM-getestet mit `FakeParentApi`) und einer zustandslosen `…Content`-Composable.

**Tech Stack:** Kotlin 2.1.0, Gradle 8.11.1, AGP 8.7.3, JDK 17; kotlinx.serialization 1.7.3, Coroutines 1.9.0, OkHttp 4.12.0 (+ MockWebServer); NewPipeExtractor v0.27.1 (JitPack); Compose BOM 2024.12.01, Material 3, Navigation Compose 2.8.5, Lifecycle 2.8.7; Media3 1.5.1; DataStore 1.1.1; WorkManager 2.10.0; Coil 3.0.4; ZXing 3.5.3; JUnit 4.13.2, Robolectric 4.14.1.

**Die vier Pläne:**
1. Server — erledigt, deployt
2. **Android `core` + Eltern-App** (dieser Plan)
3. Kinder-App
4. CI-Release-Pipeline (täglicher Extractor-Check, Live-Selbsttest, Signieren, `/updates/`) und Self-Update

**Arbeitsweise:** Commits wie in `CLAUDE.md` beschrieben (`git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit …` mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`); in den Schritten steht nur noch `git commit -m "…"`.

---

## Festlegungen, die über die Spec hinausgehen

Beim Ausplanen nötig geworden; bitte beim Review mitprüfen:

1. **Modulschnitt** `core` (JVM) / `core-android` / `parent`. `settings.gradle.kts` bindet die beiden Android-Module nur ein, wenn ein Android-SDK gefunden wird (`ANDROID_HOME` oder `sdk.dir` in `local.properties`). Damit läuft `./gradlew :core:test` auf dem Pi (arm64, kein `aapt2`), die Android-Module bauen auf dem Thinkbook und in GitHub Actions.
2. **Paket- und App-Namen:** Paket `de.safetube`, App-ID der Eltern-App `de.safetube.parent` (Kinder-App in Plan 3: `de.safetube.kid`), Anzeigename „SafeTube Eltern“ (Jessica 16.11.: kein Personenname im Paket). Für den privaten Gebrauch reicht ein Platzhalter; vor einer Veröffentlichung kommt ohnehin ein neuer Name (Spec 1), dann mit einer Domain, die uns gehört, oder `io.github.<konto>.<app>`.
3. **minSdk 31** (Android 12, Voraussetzung für das Self-Update in Plan 4), compile-/targetSdk 35. **Core-Library-Desugaring mit `desugar_jdk_libs_nio`** ist laut NewPipeExtractor unter API 33 Pflicht.
4. **Versionen sind fest** und untereinander verträglich (alle im Dezember 2024 erschienen, Existenz am 15.11.2026 geprüft); einzig NewPipeExtractor ist aktuell (v0.27.1). Hochziehen gehört in Plan 4 (tägliche Prüfung).
5. **Server-Adresse** ist im Login-Bildschirm änderbar, Standard `https://safetube.isoletta.me` (`BuildConfig.DEFAULT_SERVER`).
6. **Sitzung** (Server, Token, Elternteil, ntfy-Topic, letzte ntfy-ID, gemeldete Anfragen) liegt in DataStore im privaten App-Speicher; `android:allowBackup="false"`.
7. **Push-Dienst** ist ein Vordergrunddienst vom Typ `specialUse` (Typ `dataSync` ist ab Android 15 auf 6 h pro Tag begrenzt). Beim Neuverbinden holt er mit `since=<letzte ID>` verpasste Nachrichten nach; die letzte ID wird gespeichert, damit auch ein Neustart nichts verliert. Lesen braucht kein Token (Topic = Geheimnis, Plan 1 Festlegung 2).
8. **15-Minuten-Abgleich** per WorkManager: offene Anfragen, zu denen noch keine Benachrichtigung kam, werden nachgemeldet (Liste der gemeldeten IDs, höchstens 500). Der Worker startet außerdem den Push-Dienst neu, falls Android ihn beendet hat.
9. **Push-Aktionen:** [Freigeben] = `approve` mit Bereich „dieses Kind“ (Spec 7), [Ablehnen] = `reject` ohne Grund. Nachrichten mit Tag `decided:<id>` (anderes Elternteil hat entschieden) entfernen die offene Benachrichtigung zu dieser Anfrage.
10. **„Ablehnen + Kanal sperren“:** Der Server sperrt bei `reject_and_block` genau das angefragte Ziel. Die App bietet deshalb: bei Kanal-Anfragen „Ablehnen + Kanal sperren“ (= `reject_and_block`); bei Video-Anfragen „Ablehnen + Video sperren“ (= `reject_and_block`) und „Ablehnen + Kanal sperren“ (= `reject` und danach Kanal-Regel `block`). Alle Entscheidungen gelten wahlweise für „dieses Kind“ oder „alle Kinder“.
15. **Aufhebung einer Sperre** (Jessica 16.11.): Das Kind darf auch zu gesperrten Videos und Kanälen jederzeit anfragen, optional mit Begründung (die Kinder-UI kommt in Plan 3). Der Server merkt sich beim Anlegen `lifts_block` (galt eine Sperrregel?) und betitelt die Eltern-Meldung dann „… bittet um Aufhebung einer Sperre“. Die Eltern-App zeigt das in Liste und Detail als Hinweis. Eine Freigabe für „alle Kinder“ entfernt dabei eine Sperre nur für das anfragende Kind (sonst würde sie gewinnen). Server-Nachtrag: Commit `1ed2fc5`, Migration 0002.
16. **Kind löschen** (Jessica 16.11.): `DELETE /parent/children/{id}` löscht Kind, Geräte, Kopplungscodes, eigene Regeln, Anfragen und Schauzeit; Regeln für alle Kinder bleiben. Das Kind bekommt Push `unpaired` (Kinder-App reagiert in Plan 3). In der Eltern-App unter Kind-Einstellungen mit Rückfrage, die den Namen nennt.
11. **Vorschau-Player** (ungefiltert, Elternvorschau): bevorzugt den kombinierten Stream (Bild+Ton, meist 360p) — einfach und für eine Vorschau ausreichend; sonst Video-only + Audio zusammengeführt (bis 720p), sonst HLS. Robuste Wiedergabe über DASH-Manifeste (wie NewPipe) kommt mit dem Kinder-Player in Plan 3 und wird dann in `core-android` geteilt.
12. **Kanal-IDs:** Regeln speichern immer die Kanal-ID `UC…` — dieselbe, die die Kinder-App aus den Streamdaten liest. Links mit `@handle`, `/c/…`, `/user/…` löst die App über den Extractor zur ID auf.
13. **Tests:** `core` auf dem Pi (`./gradlew :core:test`); ViewModels, Robolectric- und Compose-Tests auf dem Thinkbook bzw. in CI (`./gradlew testDebugUnitTest`). **Emulator-UI-Tests (Spec 12) entfallen in Plan 2** zugunsten von Robolectric-Compose-Tests; ein Emulator-Lauf kann in Plan 4 dazukommen (der Thinkbook hat KVM). Live-Tests gegen YouTube nur mit `-Plive`.
14. **Bauen auf dem Thinkbook** über `android/tools/remote-build.sh` (rsync vom Pi, Gradle als Benutzer `sandro`, SDK unter `/home/sandro/youtube-safe/android-sdk`). Der Laptop geht gern in den Ruhezustand — bricht die SSH-Verbindung ab, einfach erneut aufrufen.

## Dateistruktur

```
android/
├── settings.gradle.kts            Module; Android-Module nur mit SDK
├── build.gradle.kts               Plugin-Versionen (apply false)
├── gradle.properties
├── gradle/libs.versions.toml      alle Versionen
├── gradlew, gradlew.bat, gradle/wrapper/…
├── tools/remote-build.sh          Build/Tests auf dem Thinkbook
├── core/                          reines Kotlin/JVM
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/de/safetube/core/
│       │   ├── api/
│       │   │   ├── InstantSerializer.kt   ISO-8601 mit „Z“
│       │   │   ├── ApiJson.kt             gemeinsame Json-Konfiguration
│       │   │   ├── Models.kt              alle DTOs der Eltern-API
│       │   │   ├── Errors.kt              ApiException, attempt(), userMessage()
│       │   │   ├── HttpTransport.kt       OkHttp, Bearer, Fehlerabbildung
│       │   │   └── ParentApi.kt           Schnittstelle + HttpParentApi
│       │   ├── gate/ContentGate.kt        Entscheidungslogik (wie server/app/gate.py)
│       │   ├── youtube/YouTubeIds.kt      Links → Video-/Kanal-ID
│       │   ├── push/
│       │   │   ├── Ntfy.kt                NtfyEvent, NtfyParser
│       │   │   ├── NtfyStream.kt          Dauerverbindung mit Neuverbinden
│       │   │   ├── ParentPush.kt          Push-Nachricht → Art (Anfrage, Info, Warnung)
│       │   │   └── SeenIds.kt             gemeldete Anfragen, OpenRequestCheck
│       │   ├── format/
│       │   │   ├── Formats.kt             Dauer, Datum, Restzeit
│       │   │   └── RewardDuration.kt      Eingaben des Belohnungsmodus prüfen
│       │   └── extractor/
│       │       ├── OkHttpDownloader.kt    HTTP für NewPipeExtractor
│       │       ├── YouTubeModels.kt       eigene Typen (VideoItem, …)
│       │       ├── Mapping.kt             NewPipe-Typen → eigene Typen
│       │       ├── StreamChoice.kt        welcher Stream für die Vorschau
│       │       └── YouTubeSource.kt       Schnittstelle + NewPipeSource
│       └── test/kotlin/de/safetube/core/
│           ├── api/ModelsTest.kt, HttpTransportTest.kt, HttpParentApiTest.kt
│           ├── gate/ContentGateTest.kt
│           ├── youtube/YouTubeIdsTest.kt
│           ├── push/NtfyParserTest.kt, NtfyStreamTest.kt, ParentPushTest.kt, SeenIdsTest.kt
│           ├── format/FormatsTest.kt, RewardDurationTest.kt
│           ├── extractor/OkHttpDownloaderTest.kt, MappingTest.kt, StreamChoiceTest.kt
│           └── live/LiveYouTubeTest.kt     nur mit -Plive
├── core-android/                  Android-Bibliothek (Namespace de.safetube.android)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/AndroidManifest.xml
│       ├── main/kotlin/de/safetube/android/
│       │   ├── ui/Theme.kt
│       │   ├── player/PreviewPlayer.kt     Media3, PlaybackSource → MediaSource
│       │   ├── push/NtfyForegroundService.kt
│       │   └── system/BatteryOptimization.kt
│       └── test/kotlin/de/safetube/android/system/BatteryOptimizationTest.kt
└── parent/                        Eltern-App
    ├── build.gradle.kts
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/drawable/ic_launcher.xml, ic_notification.xml
        ├── main/res/values/strings.xml
        ├── main/kotlin/de/safetube/parent/
        │   ├── ParentApp.kt, AppContainer.kt, MainActivity.kt
        │   ├── session/SessionStore.kt, Auth.kt
        │   ├── nav/Routes.kt, ParentNavHost.kt
        │   ├── ui/Common.kt                 Laden/Fehler/Kind-Auswahl
        │   ├── login/LoginViewModel.kt, LoginScreen.kt
        │   ├── overview/OverviewViewModel.kt, OverviewScreen.kt
        │   ├── requests/RequestsViewModel.kt, RequestsScreen.kt,
        │   │            RequestDetailViewModel.kt, RequestDetailScreen.kt
        │   ├── rules/RulesViewModel.kt, RulesScreen.kt, AddRuleViewModel.kt, AddRuleScreen.kt
        │   ├── history/HistoryViewModel.kt, HistoryScreen.kt
        │   ├── stats/StatsViewModel.kt, StatsScreen.kt
        │   ├── reward/RewardViewModel.kt, RewardScreen.kt
        │   ├── child/ChildSettingsViewModel.kt, ChildSettingsScreen.kt, QrCode.kt
        │   ├── parents/ParentsViewModel.kt, ParentsScreen.kt
        │   └── push/ParentNotifier.kt, ParentPushService.kt, DecisionReceiver.kt,
        │            BootReceiver.kt, OpenRequestsWorker.kt, PushControl.kt
        └── test/kotlin/de/safetube/parent/
            ├── testing/FakeParentApi.kt, FakeYouTubeSource.kt, MainDispatcherRule.kt, Samples.kt
            ├── SmokeTest.kt
            ├── session/AuthTest.kt
            ├── login/LoginViewModelTest.kt, LoginScreenTest.kt
            ├── overview/OverviewViewModelTest.kt
            ├── requests/RequestsViewModelTest.kt, RequestDetailViewModelTest.kt, RequestDetailScreenTest.kt
            ├── rules/RulesViewModelTest.kt, AddRuleViewModelTest.kt
            ├── history/HistoryViewModelTest.kt
            ├── stats/StatsViewModelTest.kt
            ├── reward/RewardViewModelTest.kt
            ├── child/ChildSettingsViewModelTest.kt, QrCodeTest.kt
            └── parents/ParentsViewModelTest.kt
.github/workflows/android.yml       core-Tests, Android-Unit-Tests, Debug-APK
```

---

### Task 0: Gradle-Grundgerüst

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/.gitignore`, `android/core/build.gradle.kts`, `android/tools/remote-build.sh`
- Create (generiert): `android/gradlew`, `android/gradlew.bat`, `android/gradle/wrapper/gradle-wrapper.jar`, `android/gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Gradle 8.11.1 auf dem Pi bereitstellen** (nur im eigenen Home, das Debian-Gradle 4.4.1 ist zu alt)

```bash
mkdir -p ~/.local/share && cd ~/.local/share
curl -fsSLO https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
echo "$(curl -fsSL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256)  gradle-8.11.1-bin.zip" | sha256sum -c
unzip -q gradle-8.11.1-bin.zip && rm gradle-8.11.1-bin.zip
~/.local/share/gradle-8.11.1/bin/gradle --version | grep -E "^Gradle|JVM"
```

Expected: `gradle-8.11.1-bin.zip: OK`, `Gradle 8.11.1`, JVM 17.

- [ ] **Step 2: Projektdateien anlegen**

`android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // NewPipeExtractor und seine nanojson-Abhängigkeit
    }
}

rootProject.name = "safetube"

include(":core")

// Die Android-Module nur mit SDK einbinden. Auf dem Pi gibt es keins (aapt2 läuft nur auf x86),
// dort genügt :core.
val localProperties = file("local.properties")
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    (localProperties.exists() && localProperties.readText().contains("sdk.dir"))
if (hasAndroidSdk) {
    include(":core-android", ":parent")
}
```

`android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`android/gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
coroutines = "1.9.0"
serialization = "1.7.3"
okhttp = "4.12.0"
newpipe = "v0.27.1"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigation = "2.8.5"
media3 = "1.5.1"
datastore = "1.1.1"
work = "2.10.0"
coreKtx = "1.15.0"
coil = "3.0.4"
zxing = "3.5.3"
desugar = "2.1.3"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
newpipe-extractor = { module = "com.github.teamnewpipe:NewPipeExtractor", version.ref = "newpipe" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing" }
desugar-nio = { module = "com.android.tools:desugar_jdk_libs_nio", version.ref = "desugar" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`android/.gitignore`:

```gitignore
.gradle/
.kotlin/
build/
local.properties
.idea/
*.iml
```

`android/core/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Gemeinsame Testtabelle der Entscheidungslogik (wird auch von den Server-Tests benutzt).
val gateCases = rootDir.resolve("../shared/gate-cases.json")

tasks.test {
    inputs.file(gateCases)
    systemProperty("gateCases", gateCases.absolutePath)
    // Live-Tests gegen YouTube nur auf ausdrücklichen Wunsch: ./gradlew :core:test -Plive
    val live = project.hasProperty("live")
    inputs.property("live", live)
    if (!live) {
        exclude("**/live/**")
    }
}
```

`android/tools/remote-build.sh`:

```bash
#!/usr/bin/env bash
# Baut bzw. testet die Android-Module auf einem x86-Rechner mit Android-SDK.
# Auf dem Pi geht das nicht: aapt2 gibt es von Google nur für x86.
#
# Aufruf aus beliebigem Verzeichnis:
#   android/tools/remote-build.sh :parent:testDebugUnitTest assembleDebug
# Voreinstellungen per Umgebung änderbar (REMOTE, REMOTE_USER, REMOTE_DIR, SDK_DIR).
set -euo pipefail

REMOTE="${REMOTE:-thinkbook}"
REMOTE_USER="${REMOTE_USER:-sandro}"
REMOTE_DIR="${REMOTE_DIR:-/home/sandro/youtube-safe/repo}"
SDK_DIR="${SDK_DIR:-/home/sandro/youtube-safe/android-sdk}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [ "$#" -eq 0 ]; then
    echo "Aufruf: $0 <gradle-Aufgaben …>" >&2
    exit 2
fi

ssh "$REMOTE" "mkdir -p '$REMOTE_DIR'"
# build/ und .gradle/ bleiben drüben erhalten (schnellere Folge-Builds), local.properties schreiben wir selbst.
rsync -a --delete \
    --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude 'local.properties' \
    "$ROOT/android" "$ROOT/shared" "$REMOTE:$REMOTE_DIR/"

ssh "$REMOTE" "set -e
    printf 'sdk.dir=%s\n' '$SDK_DIR' > '$REMOTE_DIR/android/local.properties'
    chown -R '$REMOTE_USER': '$REMOTE_DIR'
    su - '$REMOTE_USER' -c \"cd '$REMOTE_DIR/android' && ./gradlew --console=plain $*\""
```

```bash
chmod +x android/tools/remote-build.sh
```

- [ ] **Step 3: Gradle-Wrapper erzeugen**

```bash
cd android
SUM=$(curl -fsSL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256)
~/.local/share/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1 \
    --distribution-type bin --gradle-distribution-sha256-sum "$SUM"
grep -E "distributionUrl|distributionSha256Sum" gradle/wrapper/gradle-wrapper.properties
```

Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip` und eine `distributionSha256Sum`-Zeile.

- [ ] **Step 4: Auflösung von `core` auf dem Pi prüfen** (lädt Kotlin-Plugin und NewPipeExtractor von JitPack)

```bash
cd android && ./gradlew --console=plain :core:dependencies --configuration runtimeClasspath | grep -E "NewPipeExtractor|okhttp:|serialization-json"
```

Expected: Zeilen mit `com.github.teamnewpipe:NewPipeExtractor:v0.27.1`, `com.squareup.okhttp3:okhttp:4.12.0`, `kotlinx-serialization-json:1.7.3`. Die Android-Module erscheinen auf dem Pi nicht (kein SDK) — das ist gewollt.

- [ ] **Step 5: Auf dem Thinkbook prüfen, dass alle drei Module erkannt werden**

```bash
android/tools/remote-build.sh projects
```

Expected: `BUILD SUCCESSFUL`; die Projektliste nennt `:core`, `:core-android` und `:parent` (das SDK wird über `local.properties` gefunden). Für die beiden Android-Module warnt Gradle bis Task 10, dass ihre Verzeichnisse fehlen — das ist erwartet. Der erste Lauf lädt Gradle und die Plugins und dauert einige Minuten. Bricht die Verbindung ab (Ruhezustand), Thinkbook wecken und erneut aufrufen.

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "Android: Gradle-Grundgerüst mit Versionskatalog und Modul core"
```

---

### Task 1: API-Modelle (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/api/InstantSerializer.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/api/ApiJson.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/api/Models.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/api/ModelsTest.kt`

Die Feldnamen folgen 1:1 `server/app/schemas.py`. Snake-Case-Namen werden einzeln per `@SerialName` gesetzt (keine Naming-Strategy — die ist experimentell und würde auch Enum-Werte nicht sauber abdecken). `null` wird explizit mitgeschickt (wichtig für `child_id: null` = „alle Kinder“), Standardwerte ebenfalls.

- [ ] **Step 1: Failing test schreiben**

`android/core/src/test/kotlin/de/safetube/core/api/ModelsTest.kt`:

```kotlin
package de.safetube.core.api

import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `Anfrage wie vom Server liefert alle Felder`() {
        val json = """
            {"id": 7, "child_id": 1, "child_name": "Mia", "target_type": "channel",
             "target_id": "UCabcdefghijklmnopqrstuv", "title": "Maus", "channel_id": "UCabcdefghijklmnopqrstuv",
             "channel_name": "Die Maus", "url": "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
             "thumbnail_url": "", "child_message": "bitte!", "source_video_id": "dQw4w9WgXcQ",
             "source_video_title": "Folge 1", "status": "open", "lifts_block": true,
             "decided_by_name": null, "decided_at": null,
             "reason": "", "created_at": "2026-11-15T06:12:48.123456Z", "unbekannt": 1}
        """.trimIndent()

        val request = ApiJson.decodeFromString<ContentRequest>(json)

        assertEquals(7, request.id)
        assertEquals(TargetType.CHANNEL, request.targetType)
        assertEquals(RequestStatus.OPEN, request.status)
        assertEquals("dQw4w9WgXcQ", request.sourceVideoId)
        assertTrue(request.liftsBlock)
        assertNull(request.decidedAt)
        assertEquals(Instant.parse("2026-11-15T06:12:48.123456Z"), request.createdAt)
    }

    @Test
    fun `Kind ohne Belohnungsmodus`() {
        val json = """{"id": 1, "name": "Mia", "display_mode": "compact", "shorts_enabled": false,
            "reward_active": false, "reward_until": null, "reward_includes_blocked": false}"""

        val child = ApiJson.decodeFromString<Child>(json)

        assertEquals(DisplayMode.COMPACT, child.displayMode)
        assertNull(child.rewardUntil)
    }

    @Test
    fun `Regel für alle Kinder schickt child_id null ausdrücklich mit`() {
        val text = ApiJson.encodeToString(
            RuleCreate(childId = null, targetType = TargetType.VIDEO, targetId = "dQw4w9WgXcQ", effect = Effect.BLOCK),
        )

        assertTrue(text, text.contains("\"child_id\":null"))
        assertTrue(text, text.contains("\"target_type\":\"video\""))
        assertTrue(text, text.contains("\"effect\":\"block\""))
        assertTrue(text, text.contains("\"note\":\"\""))
    }

    @Test
    fun `Entscheidung schickt Standardbereich und leeren Grund`() {
        val text = ApiJson.encodeToString(Decision(DecisionAction.APPROVE_VIDEO_ONLY))

        assertEquals("""{"action":"approve_video_only","scope":"child","reason":""}""", text)
    }

    @Test
    fun `Belohnung bis Zeitpunkt wird als UTC mit Z geschickt`() {
        val text = ApiJson.encodeToString(RewardStart(until = Instant.parse("2026-11-16T18:00:00Z"), includeBlocked = true))

        assertEquals("""{"minutes":null,"until":"2026-11-16T18:00:00Z","include_blocked":true}""", text)
    }

    @Test
    fun `Statistik und Verlauf`() {
        val stats = ApiJson.decodeFromString<Stats>(
            """{"days": [{"date": "2026-11-15", "seconds": 600, "reward_seconds": 60}],
                "top_channels": [{"channel_id": "UCx", "channel_name": "X", "seconds": 600}],
                "total_seconds": 600, "reward_seconds": 60}""",
        )
        val entry = ApiJson.decodeFromString<HistoryEntry>(
            """{"id": 3, "video_id": "v", "channel_id": "c", "title": "T", "channel_name": "C",
                "started_at": "2026-11-15T10:00:00Z", "played_seconds": 42, "in_reward_mode": true,
                "allowed_now": false, "reason_now": "default"}""",
        )

        assertEquals(60, stats.days.single().rewardSeconds)
        assertEquals("UCx", stats.topChannels.single().channelId)
        assertEquals(42, entry.playedSeconds)
        assertTrue(entry.inRewardMode)
    }

    @Test
    fun `Kopplungscode und Push-Info`() {
        val code = ApiJson.decodeFromString<PairingCode>(
            """{"code": "AB12CD34", "expires_at": "2026-11-15T10:10:00Z",
                "qr_payload": "{\"server\": \"https://safetube.isoletta.me\", \"code\": \"AB12CD34\"}"}""",
        )
        val push = ApiJson.decodeFromString<PushInfo>("""{"ntfy_url": "https://push.tube.isoletta.me", "ntfy_topic": "yts-1"}""")

        assertEquals("AB12CD34", code.code)
        assertTrue(code.qrPayload.contains("AB12CD34"))
        assertEquals("yts-1", push.ntfyTopic)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.ModelsTest'`
Expected: FAIL — Kompilierfehler `Unresolved reference: ApiJson` / `ContentRequest`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/api/InstantSerializer.kt`:

```kotlin
package de.safetube.core.api

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Zeitpunkte als ISO-8601 in UTC mit „Z“ — so liefert und erwartet sie der Server. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
```

`android/core/src/main/kotlin/de/safetube/core/api/ApiJson.kt`:

```kotlin
package de.safetube.core.api

import kotlinx.serialization.json.Json

/** Eine Json-Konfiguration für alle Server-Aufrufe. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true // neuere Server dürfen Felder ergänzen
    encodeDefaults = true // z. B. scope="child" und note="" immer mitschicken
    explicitNulls = true // child_id=null heißt „alle Kinder“ und muss ankommen
}
```

`android/core/src/main/kotlin/de/safetube/core/api/Models.kt`:

```kotlin
@file:UseSerializers(InstantSerializer::class)

package de.safetube.core.api

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Spiegel von server/app/schemas.py. Feldnamen dort ändern = hier ändern.

// --- Aufzählungen -------------------------------------------------------------------

@Serializable
enum class TargetType(val wire: String) {
    @SerialName("channel") CHANNEL("channel"),
    @SerialName("video") VIDEO("video");

    companion object {
        fun fromWire(value: String): TargetType = entries.first { it.wire == value }
    }
}

@Serializable
enum class Effect(val wire: String) {
    @SerialName("allow") ALLOW("allow"),
    @SerialName("block") BLOCK("block");

    companion object {
        fun fromWire(value: String): Effect = entries.first { it.wire == value }
    }
}

@Serializable
enum class DisplayMode {
    @SerialName("compact") COMPACT,
    @SerialName("full") FULL,
}

@Serializable
enum class RequestStatus(val wire: String) {
    @SerialName("open") OPEN("open"),
    @SerialName("approved") APPROVED("approved"),
    @SerialName("rejected") REJECTED("rejected"),
}

@Serializable
enum class DecisionAction {
    @SerialName("approve") APPROVE,
    @SerialName("approve_video_only") APPROVE_VIDEO_ONLY,
    @SerialName("reject") REJECT,
    @SerialName("reject_and_block") REJECT_AND_BLOCK,
}

@Serializable
enum class Scope {
    @SerialName("child") CHILD,
    @SerialName("all") ALL,
}

// --- Eltern -------------------------------------------------------------------------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class Parent(
    val id: Int,
    val name: String,
    val email: String,
    @SerialName("mail_new_request") val mailNewRequest: Boolean,
    @SerialName("mail_alerts") val mailAlerts: Boolean,
)

@Serializable
data class LoginResponse(val token: String, val parent: Parent)

@Serializable
data class ParentCreate(val name: String, val email: String, val password: String)

@Serializable
data class MeUpdate(
    val name: String? = null,
    @SerialName("mail_new_request") val mailNewRequest: Boolean? = null,
    @SerialName("mail_alerts") val mailAlerts: Boolean? = null,
    val password: String? = null,
)

@Serializable
data class PushInfo(
    @SerialName("ntfy_url") val ntfyUrl: String,
    @SerialName("ntfy_topic") val ntfyTopic: String,
)

// --- Kinder und Geräte --------------------------------------------------------------

@Serializable
data class Child(
    val id: Int,
    val name: String,
    @SerialName("display_mode") val displayMode: DisplayMode,
    @SerialName("shorts_enabled") val shortsEnabled: Boolean,
    @SerialName("reward_active") val rewardActive: Boolean,
    @SerialName("reward_until") val rewardUntil: Instant?,
    @SerialName("reward_includes_blocked") val rewardIncludesBlocked: Boolean,
)

@Serializable
data class ChildCreate(val name: String)

@Serializable
data class ChildUpdate(
    val name: String? = null,
    @SerialName("display_mode") val displayMode: DisplayMode? = null,
    @SerialName("shorts_enabled") val shortsEnabled: Boolean? = null,
)

@Serializable
data class Device(
    val id: Int,
    @SerialName("child_id") val childId: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("paired_at") val pairedAt: Instant,
    @SerialName("last_seen") val lastSeen: Instant,
    @SerialName("unpaired_at") val unpairedAt: Instant?,
)

@Serializable
data class PairingCode(
    val code: String,
    @SerialName("expires_at") val expiresAt: Instant,
    @SerialName("qr_payload") val qrPayload: String,
)

// --- Regeln -------------------------------------------------------------------------

@Serializable
data class Rule(
    val id: Int,
    @SerialName("child_id") val childId: Int?,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val title: String,
    @SerialName("channel_name") val channelName: String,
    val effect: Effect,
    val note: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)

@Serializable
data class RuleCreate(
    @SerialName("child_id") val childId: Int?,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val effect: Effect,
    val title: String = "",
    @SerialName("channel_name") val channelName: String = "",
    val note: String = "",
)

@Serializable
data class RuleUpdate(val effect: Effect? = null, val note: String? = null)

// --- Anfragen -----------------------------------------------------------------------

@Serializable
data class ContentRequest(
    val id: Int,
    @SerialName("child_id") val childId: Int,
    @SerialName("child_name") val childName: String,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val title: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_name") val channelName: String,
    val url: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("child_message") val childMessage: String,
    @SerialName("source_video_id") val sourceVideoId: String,
    @SerialName("source_video_title") val sourceVideoTitle: String,
    val status: RequestStatus,
    @SerialName("decided_by_name") val decidedByName: String?,
    @SerialName("decided_at") val decidedAt: Instant?,
    val reason: String,
    @SerialName("created_at") val createdAt: Instant,
    /** Beim Anfragen galt eine Sperrregel: das Kind bittet um Aufhebung (Festlegung 15). */
    @SerialName("lifts_block") val liftsBlock: Boolean = false,
)

@Serializable
data class Decision(
    val action: DecisionAction,
    val scope: Scope = Scope.CHILD,
    val reason: String = "",
)

/** Filter für GET /parent/requests; null = nicht filtern. */
data class RequestFilter(
    val childId: Int? = null,
    val status: RequestStatus? = null,
    val since: Instant? = null,
    val until: Instant? = null,
    val limit: Int = 200,
)

// --- Belohnungsmodus ----------------------------------------------------------------

/** Genau eines von [minutes] oder [until] setzen (prüft auch der Server). */
@Serializable
data class RewardStart(
    val minutes: Int? = null,
    val until: Instant? = null,
    @SerialName("include_blocked") val includeBlocked: Boolean = false,
)

// --- Verlauf und Statistik ----------------------------------------------------------

@Serializable
data class HistoryEntry(
    val id: Int,
    @SerialName("video_id") val videoId: String,
    @SerialName("channel_id") val channelId: String,
    val title: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("started_at") val startedAt: Instant,
    @SerialName("played_seconds") val playedSeconds: Int,
    @SerialName("in_reward_mode") val inRewardMode: Boolean,
    @SerialName("allowed_now") val allowedNow: Boolean,
    @SerialName("reason_now") val reasonNow: String,
)

@Serializable
data class DayStat(
    val date: String,
    val seconds: Int,
    @SerialName("reward_seconds") val rewardSeconds: Int,
)

@Serializable
data class ChannelStat(
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_name") val channelName: String,
    val seconds: Int,
)

@Serializable
data class Stats(
    val days: List<DayStat>,
    @SerialName("top_channels") val topChannels: List<ChannelStat>,
    @SerialName("total_seconds") val totalSeconds: Int,
    @SerialName("reward_seconds") val rewardSeconds: Int,
)
```

- [ ] **Step 4: Test laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.ModelsTest'`
Expected: PASS (7 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: API-Modelle passend zu server/app/schemas.py"
```

---

### Task 2: Entscheidungslogik `ContentGate` (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/gate/ContentGate.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/gate/ContentGateTest.kt`

Port von `server/app/gate.py`, Gründe (`reason`) mit denselben Zeichenketten. Getestet wird ausschließlich gegen `shared/gate-cases.json`, damit Server und App nie auseinanderlaufen.

- [ ] **Step 1: Failing test schreiben**

`android/core/src/test/kotlin/de/safetube/core/gate/ContentGateTest.kt`:

```kotlin
package de.safetube.core.gate

import de.safetube.core.api.ApiJson
import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import java.io.File
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentGateTest {

    private val cases = run {
        val path = System.getProperty("gateCases") ?: error("Systemeigenschaft gateCases fehlt (siehe core/build.gradle.kts)")
        ApiJson.parseToJsonElement(File(path).readText()).jsonObject.getValue("cases").jsonArray
    }

    @Test
    fun `alle gemeinsamen Fälle aus shared gate-cases json`() {
        assertTrue("zu wenige Fälle gefunden: ${cases.size}", cases.size >= 13)
        for (raw in cases) {
            val case = raw.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val rules = case.getValue("rules").jsonArray.map { element ->
                val rule = element.jsonObject
                RuleView(
                    childId = rule.getValue("child_id").jsonPrimitive.intOrNull,
                    targetType = TargetType.fromWire(rule.getValue("target_type").jsonPrimitive.content),
                    targetId = rule.getValue("target_id").jsonPrimitive.content,
                    effect = Effect.fromWire(rule.getValue("effect").jsonPrimitive.content),
                )
            }

            val decision = ContentGate.decide(
                rules = rules,
                childId = 1,
                videoId = case.getValue("video_id").jsonPrimitive.content,
                channelId = case.getValue("channel_id").jsonPrimitive.content,
                rewardActive = case.getValue("reward_active").jsonPrimitive.boolean,
                rewardIncludesBlocked = case.getValue("reward_includes_blocked").jsonPrimitive.boolean,
            )

            val expected = case.getValue("expected").jsonObject
            assertEquals(name, expected.getValue("allowed").jsonPrimitive.boolean, decision.allowed)
            assertEquals(name, expected.getValue("reason").jsonPrimitive.content, decision.reason.wire)
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.gate.ContentGateTest'`
Expected: FAIL — `Unresolved reference: RuleView` / `ContentGate`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/gate/ContentGate.kt`:

```kotlin
package de.safetube.core.gate

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType

/** Eine Regel, reduziert auf das, was die Entscheidung braucht. childId = null: gilt für alle Kinder. */
data class RuleView(
    val childId: Int?,
    val targetType: TargetType,
    val targetId: String,
    val effect: Effect,
)

enum class GateReason(val wire: String) {
    REWARD_ALL("reward_all"),
    VIDEO_BLOCK("video_block"),
    VIDEO_ALLOW("video_allow"),
    CHANNEL_BLOCK("channel_block"),
    CHANNEL_ALLOW("channel_allow"),
    REWARD("reward"),
    DEFAULT("default"),
}

data class GateDecision(val allowed: Boolean, val reason: GateReason)

/**
 * Darf ein Kind ein Video sehen? Gleiche Logik wie server/app/gate.py, beide gegen
 * shared/gate-cases.json getestet.
 *
 * Reihenfolge (erste zutreffende Stufe gewinnt):
 * 1. Belohnungsmodus mit „auch Gesperrtes“ → erlaubt
 * 2./3. Video-Regel (block / allow)
 * 4./5. Kanal-Regel (block / allow)
 * 6. Belohnungsmodus → erlaubt
 * 7. sonst → gesperrt
 * Innerhalb einer Stufe schlägt die Regel des Kindes die Regel für alle Kinder.
 */
object ContentGate {

    fun decide(
        rules: List<RuleView>,
        childId: Int,
        videoId: String,
        channelId: String,
        rewardActive: Boolean,
        rewardIncludesBlocked: Boolean,
    ): GateDecision {
        if (rewardActive && rewardIncludesBlocked) return GateDecision(true, GateReason.REWARD_ALL)

        when (effectiveEffect(rules, childId, TargetType.VIDEO, videoId)) {
            Effect.BLOCK -> return GateDecision(false, GateReason.VIDEO_BLOCK)
            Effect.ALLOW -> return GateDecision(true, GateReason.VIDEO_ALLOW)
            null -> Unit
        }
        when (effectiveEffect(rules, childId, TargetType.CHANNEL, channelId)) {
            Effect.BLOCK -> return GateDecision(false, GateReason.CHANNEL_BLOCK)
            Effect.ALLOW -> return GateDecision(true, GateReason.CHANNEL_ALLOW)
            null -> Unit
        }
        if (rewardActive) return GateDecision(true, GateReason.REWARD)
        return GateDecision(false, GateReason.DEFAULT)
    }

    private fun effectiveEffect(rules: List<RuleView>, childId: Int, type: TargetType, targetId: String): Effect? {
        var specific: RuleView? = null
        var general: RuleView? = null
        for (rule in rules) {
            if (rule.targetType != type || rule.targetId != targetId) continue
            when (rule.childId) {
                null -> general = rule
                childId -> specific = rule
            }
        }
        return (specific ?: general)?.effect
    }
}
```

- [ ] **Step 4: Test laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.gate.ContentGateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: ContentGate, getestet gegen shared/gate-cases.json"
```

---

### Task 3: YouTube-Links erkennen (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/youtube/YouTubeIds.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/youtube/YouTubeIdsTest.kt`

Wird gebraucht für „neue Regel per eingefügtem Link“ und um aus Extractor-URLs Video- und Kanal-IDs zu lesen. Eine nackte 11-Zeichen-Video-ID wird bewusst **nicht** erkannt (zu leicht mit Wörtern zu verwechseln), eine nackte Kanal-ID `UC…` schon.

- [ ] **Step 1: Failing test schreiben**

`android/core/src/test/kotlin/de/safetube/core/youtube/YouTubeIdsTest.kt`:

```kotlin
package de.safetube.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeIdsTest {

    private val video = YouTubeLink.Video("dQw4w9WgXcQ")
    private val channel = YouTubeLink.Channel("UCuAXFkgsw1L7xaCfnd5JJOw")

    @Test
    fun `Video-Links in allen üblichen Formen`() {
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42"))
        assertEquals(video, YouTubeIds.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("  https://youtu.be/dQw4w9WgXcQ?si=abc  "))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `Kanal-Links und nackte Kanal-ID`() {
        assertEquals(channel, YouTubeIds.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
        assertEquals(channel, YouTubeIds.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw/videos"))
        assertEquals(channel, YouTubeIds.parse("UCuAXFkgsw1L7xaCfnd5JJOw"))
    }

    @Test
    fun `Kanal-Aliase müssen später aufgelöst werden`() {
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/@DieSendungmitderMaus"),
            YouTubeIds.parse("https://www.youtube.com/@DieSendungmitderMaus/videos"),
        )
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/c/Beispiel"),
            YouTubeIds.parse("https://youtube.com/c/Beispiel"),
        )
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/user/Beispiel"),
            YouTubeIds.parse("https://www.youtube.com/user/Beispiel/featured"),
        )
    }

    @Test
    fun `Unsinn und fremde Seiten werden nicht erkannt`() {
        assertNull(YouTubeIds.parse(""))
        assertNull(YouTubeIds.parse("hallo welt"))
        assertNull(YouTubeIds.parse("dQw4w9WgXcQ"))
        assertNull(YouTubeIds.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/watch?v=zukurz"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/channel/UCzukurz"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/c"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/"))
    }

    @Test
    fun `IDs aus Extractor-URLs`() {
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", YouTubeIds.channelIdFromUrl("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
        assertNull(YouTubeIds.channelIdFromUrl("https://www.youtube.com/@handle"))
        assertNull(YouTubeIds.channelIdFromUrl(null))
        assertEquals("dQw4w9WgXcQ", YouTubeIds.videoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=abc", YouTubeIds.videoUrl("abc"))
        assertEquals("https://www.youtube.com/channel/UCx", YouTubeIds.channelUrl("UCx"))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.youtube.YouTubeIdsTest'`
Expected: FAIL — `Unresolved reference: YouTubeLink`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/youtube/YouTubeIds.kt`:

```kotlin
package de.safetube.core.youtube

import java.net.URI
import java.net.URISyntaxException

sealed interface YouTubeLink {
    data class Video(val videoId: String) : YouTubeLink
    data class Channel(val channelId: String) : YouTubeLink

    /** `@handle`, `/c/…` oder `/user/…`: die Kanal-ID kennt erst der Extractor. */
    data class ChannelAlias(val url: String) : YouTubeLink
}

object YouTubeIds {
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com",
    )
    private val VIDEO_PATHS = setOf("shorts", "live", "embed", "v")

    fun isVideoId(value: String): Boolean = VIDEO_ID.matches(value)
    fun isChannelId(value: String): Boolean = CHANNEL_ID.matches(value)

    fun videoUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
    fun channelUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

    fun parse(input: String): YouTubeLink? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (isChannelId(text)) return YouTubeLink.Channel(text)

        val uri = toUri(text) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }

        if (host == "youtu.be") {
            return segments.firstOrNull()?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
        }
        if (host !in HOSTS) return null

        val first = segments.firstOrNull() ?: return null
        return when {
            first == "watch" -> queryParameter(uri, "v")?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
            first in VIDEO_PATHS -> segments.getOrNull(1)?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
            first == "channel" -> segments.getOrNull(1)?.takeIf(::isChannelId)?.let(YouTubeLink::Channel)
            first.startsWith("@") && first.length > 1 -> YouTubeLink.ChannelAlias("https://www.youtube.com/$first")
            (first == "c" || first == "user") && segments.size >= 2 ->
                YouTubeLink.ChannelAlias("https://www.youtube.com/$first/${segments[1]}")
            else -> null
        }
    }

    fun channelIdFromUrl(url: String?): String? = (url?.let(::parse) as? YouTubeLink.Channel)?.channelId

    fun videoIdFromUrl(url: String?): String? = (url?.let(::parse) as? YouTubeLink.Video)?.videoId

    private fun toUri(text: String): URI? {
        val withScheme = if (text.contains("://")) text else "https://$text"
        return try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            null
        }
    }

    private fun queryParameter(uri: URI, key: String): String? =
        uri.rawQuery
            ?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it.first() == key }
            ?.getOrNull(1)
}
```

- [ ] **Step 4: Test laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.youtube.YouTubeIdsTest'`
Expected: PASS (5 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: YouTube-Links in Video- und Kanal-IDs zerlegen"
```

---

### Task 4: HTTP-Transport und Fehler (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/api/Errors.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/api/HttpTransport.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/api/HttpTransportTest.kt`

FastAPI liefert Fehler als `{"detail": "Text"}`, Validierungsfehler als `{"detail": [{"msg": …}, …]}`. Beides wird zu `ApiException(status, detail)`. Netzwerkfehler bleiben `IOException`. Server-Adresse und Token kommen über Funktionen herein, damit ein Login die Adresse wechseln kann, ohne den Transport neu zu bauen.

- [ ] **Step 1: Failing test schreiben**

`android/core/src/test/kotlin/de/safetube/core/api/HttpTransportTest.kt`:

```kotlin
package de.safetube.core.api

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpTransportTest {

    @Serializable
    data class Echo(val value: String)

    private lateinit var server: MockWebServer
    private var token: String? = "geheim"

    private fun transport() = HttpTransport(
        client = OkHttpClient(),
        baseUrl = { server.url("/").toString() },
        token = { token },
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET schickt Bearer-Token und Abfrageparameter ohne null`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value": "ok"}"""))

        val echo: Echo = transport().get("/ding", mapOf("a" to "1", "b" to null))

        assertEquals("ok", echo.value)
        val request = server.takeRequest()
        assertEquals("/api/v1/ding?a=1", request.path)
        assertEquals("Bearer geheim", request.getHeader("Authorization"))
    }

    @Test
    fun `ohne Token kein Authorization-Header`() = runBlocking {
        token = null
        server.enqueue(MockResponse().setBody("""{"value": "ok"}"""))

        transport().get<Echo>("/ding")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `POST schickt JSON`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value": "zurück"}"""))

        val echo: Echo = transport().post("/ding", Echo("hin"))

        assertEquals("zurück", echo.value)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"value":"hin"}""", request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `DELETE ohne Antwortinhalt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        transport().send("DELETE", "/ding/3")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/ding/3", request.path)
    }

    @Test
    fun `Fehlertext aus detail`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail": "Nicht angemeldet"}"""))

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals(401, error.status)
        assertEquals("Nicht angemeldet", error.detail)
    }

    @Test
    fun `Validierungsfehler werden zusammengefasst`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"detail": [{"loc": ["body", "password"], "msg": "zu kurz", "type": "x"},
                               {"loc": ["body", "email"], "msg": "fehlt", "type": "y"}]}""",
            ),
        )

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals("zu kurz; fehlt", error.detail)
    }

    @Test
    fun `Fehler ohne JSON`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad Gateway"))

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals(502, error.status)
        assertEquals("Bad Gateway", error.detail)
    }

    @Test
    fun `Server weg ist eine IOException mit verständlicher Meldung`() = runBlocking {
        server.shutdown()

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", error!!.userMessage())
    }

    @Test
    fun `attempt fängt Fehler, aber keine Abbrüche`() = runBlocking {
        val ok = attempt { 42 }
        val failed = attempt { throw ApiException(404, "weg") }

        assertEquals(42, ok.getOrNull())
        assertEquals("weg", failed.exceptionOrNull()!!.userMessage())
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.HttpTransportTest'`
Expected: FAIL — `Unresolved reference: HttpTransport`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/api/Errors.kt`:

```kotlin
package de.safetube.core.api

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Der Server hat geantwortet, aber mit einem Fehlerstatus. [detail] ist sein Fehlertext. */
class ApiException(val status: Int, val detail: String) : Exception("HTTP $status: $detail")

/** Fehler eines YouTube-Zugriffs (Extractor), mit fertiger Meldung für die Oberfläche. */
class YouTubeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Führt [block] aus und liefert Erfolg oder Fehler; Abbrüche (Coroutine-Cancel) werden weitergereicht. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Kurzer deutscher Text für die Oberfläche. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> if (status == 401) "Nicht (mehr) angemeldet. Bitte neu anmelden." else detail
    is YouTubeException -> message ?: "YouTube-Fehler"
    is IOException -> "Server nicht erreichbar. Internetverbindung prüfen."
    else -> message ?: javaClass.simpleName
}
```

Hinweis: Für 401 liefert `userMessage()` bewusst den Hinweis zum Neuanmelden. Der Test `Fehlertext aus detail` prüft `error.detail`, nicht `userMessage()`; `attempt fängt Fehler` nutzt Status 404.

`android/core/src/main/kotlin/de/safetube/core/api/HttpTransport.kt`:

```kotlin
package de.safetube.core.api

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Dünne Schicht über OkHttp: Basis-URL + /api/v1, Bearer-Token, JSON, Fehlerabbildung.
 * [baseUrl] und [token] werden bei jedem Aufruf neu gelesen.
 */
class HttpTransport(
    private val client: OkHttpClient,
    private val baseUrl: () -> String,
    private val token: () -> String?,
) {
    @PublishedApi
    internal val json: Json = ApiJson

    suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        json.decodeFromString(call("GET", path, query, null))

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        json.decodeFromString(call("POST", path, emptyMap(), json.encodeToString(body)))

    /** POST ohne Inhalt, Antwort mit Inhalt (z. B. Kopplungscode). */
    suspend inline fun <reified T> postEmpty(path: String): T =
        json.decodeFromString(call("POST", path, emptyMap(), ""))

    suspend inline fun <reified B, reified T> patch(path: String, body: B): T =
        json.decodeFromString(call("PATCH", path, emptyMap(), json.encodeToString(body)))

    /** DELETE mit Antwortinhalt (z. B. Belohnungsmodus beenden liefert das Kind). */
    suspend inline fun <reified T> deleteFor(path: String): T =
        json.decodeFromString(call("DELETE", path, emptyMap(), null))

    /** Aufruf ohne Antwortinhalt (204), z. B. DELETE oder POST /logout. */
    suspend fun send(method: String, path: String) {
        call(method, path, emptyMap(), if (method == "GET" || method == "DELETE") null else "")
    }

    @PublishedApi
    internal suspend fun call(method: String, path: String, query: Map<String, String?>, body: String?): String =
        withContext(Dispatchers.IO) {
            val url = (baseUrl().trimEnd('/') + "/api/v1" + path).toHttpUrlOrNull()
                ?.newBuilder()
                ?.apply { query.forEach { (key, value) -> if (value != null) addQueryParameter(key, value) } }
                ?.build()
                ?: throw IOException("Ungültige Server-Adresse")
            val request = Request.Builder()
                .url(url)
                .method(method, body?.toRequestBody(JSON_TYPE))
                .header("Accept", "application/json")
                .apply { token()?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, parseDetail(text, response.code))
                text
            }
        }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseDetail(body: String, status: Int): String =
            try {
                when (val detail = ApiJson.parseToJsonElement(body).jsonObject["detail"]) {
                    is JsonPrimitive -> detail.content
                    is JsonArray -> detail.joinToString("; ") { item ->
                        item.jsonObject["msg"]?.jsonPrimitive?.content ?: item.toString()
                    }
                    else -> "HTTP $status"
                }
            } catch (e: Exception) {
                if (body.isBlank()) "HTTP $status" else body.take(200)
            }
    }
}
```

- [ ] **Step 4: Test laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.HttpTransportTest'`
Expected: PASS (9 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: HTTP-Transport mit Bearer-Token und FastAPI-Fehlerabbildung"
```

---

### Task 5: Eltern-API (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/api/ParentApi.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/api/HttpParentApiTest.kt`

Alle Eltern-Endpunkte aus `server/app/routers/parent_*.py`. Die Schnittstelle `ParentApi` erlaubt den ViewModels Fakes in Tests.

| Methode | Aufruf |
|---|---|
| login / logout / me | `POST /parent/login`, `POST /parent/logout` (204), `GET /parent/me` |
| updateMe / pushInfo | `PATCH /parent/me`, `GET /parent/push` |
| parents / createParent | `GET /parent/parents`, `POST /parent/parents` |
| children / createChild / updateChild / deleteChild | `GET /parent/children`, `POST /parent/children`, `PATCH /parent/children/{id}`, `DELETE /parent/children/{id}` (204) |
| devices / unpairDevice / pairingCode | `GET /parent/children/{id}/devices`, `DELETE /parent/devices/{id}` (204), `POST /parent/children/{id}/pairing-code` |
| rules / createRule / updateRule / deleteRule | `GET /parent/rules?child_id=&global_only=`, `POST`, `PATCH /{id}`, `DELETE /{id}` (204) |
| requests / request / decide | `GET /parent/requests?child_id&status&since&until&limit`, `GET /{id}`, `POST /{id}/decision` |
| startReward / stopReward | `POST /parent/children/{id}/reward`, `DELETE /parent/children/{id}/reward` (liefert Kind) |
| history / stats | `GET /parent/children/{id}/history?limit&before`, `GET /parent/children/{id}/stats?days` |

- [ ] **Step 1: Failing test schreiben**

`android/core/src/test/kotlin/de/safetube/core/api/HttpParentApiTest.kt`:

```kotlin
package de.safetube.core.api

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpParentApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ParentApi

    private val childJson = """{"id": 1, "name": "Mia", "display_mode": "compact", "shorts_enabled": false,
        "reward_active": true, "reward_until": "2026-11-15T20:00:00Z", "reward_includes_blocked": false}"""
    private val ruleJson = """{"id": 5, "child_id": null, "target_type": "channel", "target_id": "UCx",
        "title": "Maus", "channel_name": "Maus", "effect": "allow", "note": "",
        "created_at": "2026-11-15T06:00:00Z", "updated_at": "2026-11-15T06:00:00Z"}"""
    private val requestJson = """{"id": 9, "child_id": 1, "child_name": "Mia", "target_type": "video",
        "target_id": "dQw4w9WgXcQ", "title": "T", "channel_id": "UCx", "channel_name": "C",
        "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "thumbnail_url": "", "child_message": "",
        "source_video_id": "", "source_video_title": "", "status": "approved", "decided_by_name": "Sandro",
        "decided_at": "2026-11-15T07:00:00Z", "reason": "", "created_at": "2026-11-15T06:00:00Z"}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = HttpParentApi(HttpTransport(OkHttpClient(), { server.url("/").toString() }, { "tok" }))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun reply(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun login() = runBlocking {
        reply("""{"token": "neu", "parent": {"id": 1, "name": "Sandro", "email": "s@x.de",
            "mail_new_request": true, "mail_alerts": true}}""")

        val result = api.login("s@x.de", "pw")

        assertEquals("neu", result.token)
        assertEquals("Sandro", result.parent.name)
        val request = server.takeRequest()
        assertEquals("/api/v1/parent/login", request.path)
        assertEquals("""{"email":"s@x.de","password":"pw"}""", request.body.readUtf8())
    }

    @Test
    fun `logout erwartet 204`() = runBlocking {
        reply("", 204)

        api.logout()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/parent/logout", request.path)
    }

    @Test
    fun `Regeln eines Kindes und nur globale`() = runBlocking {
        reply("[$ruleJson]")
        reply("[]")

        val rules = api.rules(childId = 1)
        api.rules(globalOnly = true)

        assertEquals(null, rules.single().childId)
        assertEquals("/api/v1/parent/rules?child_id=1", server.takeRequest().path)
        assertEquals("/api/v1/parent/rules?global_only=true", server.takeRequest().path)
    }

    @Test
    fun `Regel anlegen, ändern, löschen`() = runBlocking {
        reply(ruleJson, 201)
        reply(ruleJson)
        reply("", 204)

        api.createRule(RuleCreate(childId = null, targetType = TargetType.CHANNEL, targetId = "UCx", effect = Effect.ALLOW))
        api.updateRule(5, RuleUpdate(effect = Effect.BLOCK))
        api.deleteRule(5)

        val create = server.takeRequest()
        assertTrue(create.body.readUtf8().contains("\"child_id\":null"))
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/parent/rules/5", update.path)
        assertEquals("""{"effect":"block","note":null}""", update.body.readUtf8())
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/parent/rules/5", delete.path)
    }

    @Test
    fun `Anfragen mit Filter`() = runBlocking {
        reply("[$requestJson]")

        val list = api.requests(
            RequestFilter(childId = 1, status = RequestStatus.OPEN, since = Instant.parse("2026-11-01T00:00:00Z"), limit = 50),
        )

        assertEquals("Sandro", list.single().decidedByName)
        assertEquals(
            "/api/v1/parent/requests?child_id=1&status=open&since=2026-11-01T00%3A00%3A00Z&limit=50",
            server.takeRequest().path,
        )
    }

    @Test
    fun `Anfrage entscheiden`() = runBlocking {
        reply(requestJson)

        api.decide(9, Decision(DecisionAction.REJECT_AND_BLOCK, Scope.ALL, "zu gruselig"))

        val request = server.takeRequest()
        assertEquals("/api/v1/parent/requests/9/decision", request.path)
        assertEquals("""{"action":"reject_and_block","scope":"all","reason":"zu gruselig"}""", request.body.readUtf8())
    }

    @Test
    fun `Belohnungsmodus starten und beenden`() = runBlocking {
        reply(childJson)
        reply(childJson)

        val started = api.startReward(1, RewardStart(minutes = 90))
        api.stopReward(1)

        assertTrue(started.rewardActive)
        val start = server.takeRequest()
        assertEquals("/api/v1/parent/children/1/reward", start.path)
        assertEquals("""{"minutes":90,"until":null,"include_blocked":false}""", start.body.readUtf8())
        val stop = server.takeRequest()
        assertEquals("DELETE", stop.method)
        assertEquals("/api/v1/parent/children/1/reward", stop.path)
    }

    @Test
    fun `Verlauf blättert mit before, Statistik mit days`() = runBlocking {
        reply("[]")
        reply("""{"days": [], "top_channels": [], "total_seconds": 0, "reward_seconds": 0}""")

        api.history(1, limit = 50, before = Instant.parse("2026-11-15T10:00:00Z"))
        api.stats(1, days = 30)

        assertEquals("/api/v1/parent/children/1/history?limit=50&before=2026-11-15T10%3A00%3A00Z", server.takeRequest().path)
        assertEquals("/api/v1/parent/children/1/stats?days=30", server.takeRequest().path)
    }

    @Test
    fun `Kinder, Geräte, Kopplung`() = runBlocking {
        reply("[$childJson]")
        reply(childJson, 201)
        reply(childJson)
        reply("""[{"id": 4, "child_id": 1, "app_version": "0.1.0", "paired_at": "2026-11-15T06:00:00Z",
            "last_seen": "2026-11-15T07:00:00Z", "unpaired_at": null}]""")
        reply("", 204)
        reply("""{"code": "AB12CD34", "expires_at": "2026-11-15T06:10:00Z", "qr_payload": "{}"}""")
        reply("", 204)

        api.children()
        api.createChild("Antonio")
        api.updateChild(1, ChildUpdate(shortsEnabled = true))
        val devices = api.devices(1)
        api.unpairDevice(4)
        val code = api.pairingCode(1)
        api.deleteChild(1)

        assertEquals("/api/v1/parent/children", server.takeRequest().path)
        assertEquals("""{"name":"Antonio"}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"name":null,"display_mode":null,"shorts_enabled":true}""", server.takeRequest().body.readUtf8())
        assertEquals("/api/v1/parent/children/1/devices", server.takeRequest().path)
        assertEquals("/api/v1/parent/devices/4", server.takeRequest().path)
        val pairing = server.takeRequest()
        assertEquals("POST", pairing.method)
        assertEquals("/api/v1/parent/children/1/pairing-code", pairing.path)
        val deletion = server.takeRequest()
        assertEquals("DELETE", deletion.method)
        assertEquals("/api/v1/parent/children/1", deletion.path)
        assertEquals("0.1.0", devices.single().appVersion)
        assertEquals("AB12CD34", code.code)
    }

    @Test
    fun `Eltern und eigenes Konto`() = runBlocking {
        val parentJson = """{"id": 2, "name": "Anna", "email": "a@x.de", "mail_new_request": true, "mail_alerts": false}"""
        reply("[$parentJson]")
        reply(parentJson, 201)
        reply(parentJson)
        reply(parentJson)
        reply("""{"ntfy_url": "https://push.tube.isoletta.me", "ntfy_topic": "yts-1"}""")

        api.parents()
        api.createParent(ParentCreate("Anna", "a@x.de", "langespasswort"))
        api.me()
        api.updateMe(MeUpdate(mailAlerts = false))
        val push = api.pushInfo()

        assertEquals("/api/v1/parent/parents", server.takeRequest().path)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("/api/v1/parent/me", server.takeRequest().path)
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"name":null,"mail_new_request":null,"mail_alerts":false,"password":null}""", patch.body.readUtf8())
        assertEquals("yts-1", push.ntfyTopic)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.HttpParentApiTest'`
Expected: FAIL — `Unresolved reference: HttpParentApi`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/api/ParentApi.kt`:

```kotlin
package de.safetube.core.api

import java.time.Instant

/** Alle Aufrufe der Eltern-App an den Server (`/api/v1/parent/…`). */
interface ParentApi {
    suspend fun login(email: String, password: String): LoginResponse
    suspend fun logout()
    suspend fun me(): Parent
    suspend fun updateMe(update: MeUpdate): Parent
    suspend fun pushInfo(): PushInfo
    suspend fun parents(): List<Parent>
    suspend fun createParent(data: ParentCreate): Parent

    suspend fun children(): List<Child>
    suspend fun createChild(name: String): Child
    suspend fun updateChild(childId: Int, update: ChildUpdate): Child
    /** Löscht das Kind samt Geräten, eigenen Regeln, Anfragen und Schauzeit (Festlegung 16). */
    suspend fun deleteChild(childId: Int)
    suspend fun devices(childId: Int): List<Device>
    suspend fun unpairDevice(deviceId: Int)
    suspend fun pairingCode(childId: Int): PairingCode

    /** [childId] gesetzt: Regeln des Kindes plus die für alle; [globalOnly]: nur die für alle. */
    suspend fun rules(childId: Int? = null, globalOnly: Boolean = false): List<Rule>
    suspend fun createRule(rule: RuleCreate): Rule
    suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule
    suspend fun deleteRule(ruleId: Int)

    suspend fun requests(filter: RequestFilter = RequestFilter()): List<ContentRequest>
    suspend fun request(requestId: Int): ContentRequest
    suspend fun decide(requestId: Int, decision: Decision): ContentRequest

    suspend fun startReward(childId: Int, reward: RewardStart): Child
    suspend fun stopReward(childId: Int): Child

    suspend fun history(childId: Int, limit: Int = 50, before: Instant? = null): List<HistoryEntry>
    suspend fun stats(childId: Int, days: Int = 7): Stats
}

class HttpParentApi(private val http: HttpTransport) : ParentApi {

    override suspend fun login(email: String, password: String): LoginResponse =
        http.post("/parent/login", LoginRequest(email, password))

    override suspend fun logout() = http.send("POST", "/parent/logout")

    override suspend fun me(): Parent = http.get("/parent/me")

    override suspend fun updateMe(update: MeUpdate): Parent = http.patch("/parent/me", update)

    override suspend fun pushInfo(): PushInfo = http.get("/parent/push")

    override suspend fun parents(): List<Parent> = http.get("/parent/parents")

    override suspend fun createParent(data: ParentCreate): Parent = http.post("/parent/parents", data)

    override suspend fun children(): List<Child> = http.get("/parent/children")

    override suspend fun createChild(name: String): Child = http.post("/parent/children", ChildCreate(name))

    override suspend fun updateChild(childId: Int, update: ChildUpdate): Child =
        http.patch("/parent/children/$childId", update)

    override suspend fun deleteChild(childId: Int) = http.send("DELETE", "/parent/children/$childId")

    override suspend fun devices(childId: Int): List<Device> = http.get("/parent/children/$childId/devices")

    override suspend fun unpairDevice(deviceId: Int) = http.send("DELETE", "/parent/devices/$deviceId")

    override suspend fun pairingCode(childId: Int): PairingCode =
        http.postEmpty("/parent/children/$childId/pairing-code")

    override suspend fun rules(childId: Int?, globalOnly: Boolean): List<Rule> =
        http.get(
            "/parent/rules",
            mapOf(
                "child_id" to childId?.toString(),
                "global_only" to if (globalOnly) "true" else null,
            ),
        )

    override suspend fun createRule(rule: RuleCreate): Rule = http.post("/parent/rules", rule)

    override suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule = http.patch("/parent/rules/$ruleId", update)

    override suspend fun deleteRule(ruleId: Int) = http.send("DELETE", "/parent/rules/$ruleId")

    override suspend fun requests(filter: RequestFilter): List<ContentRequest> =
        http.get(
            "/parent/requests",
            mapOf(
                "child_id" to filter.childId?.toString(),
                "status" to filter.status?.wire,
                "since" to filter.since?.toString(),
                "until" to filter.until?.toString(),
                "limit" to filter.limit.toString(),
            ),
        )

    override suspend fun request(requestId: Int): ContentRequest = http.get("/parent/requests/$requestId")

    override suspend fun decide(requestId: Int, decision: Decision): ContentRequest =
        http.post("/parent/requests/$requestId/decision", decision)

    override suspend fun startReward(childId: Int, reward: RewardStart): Child =
        http.post("/parent/children/$childId/reward", reward)

    override suspend fun stopReward(childId: Int): Child = http.deleteFor("/parent/children/$childId/reward")

    override suspend fun history(childId: Int, limit: Int, before: Instant?): List<HistoryEntry> =
        http.get(
            "/parent/children/$childId/history",
            mapOf("limit" to limit.toString(), "before" to before?.toString()),
        )

    override suspend fun stats(childId: Int, days: Int): Stats =
        http.get("/parent/children/$childId/stats", mapOf("days" to days.toString()))
}
```

Hinweis zu den Abfrageparametern: `mapOf` behält die Einfügereihenfolge, `addQueryParameter` hängt in dieser Reihenfolge an und kodiert `:` als `%3A` — die erwarteten Pfade im Test hängen von beidem ab; der Server dekodiert das normal.

- [ ] **Step 4: Test laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.api.HttpParentApiTest'`
Expected: PASS (10 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: Eltern-API mit allen Endpunkten des Servers"
```

---

### Task 6: ntfy-Datenstrom (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/push/Ntfy.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/push/NtfyStream.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/push/NtfyParserTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/push/NtfyStreamTest.kt`

ntfy liefert unter `GET <server>/<topic>/json` eine Zeile JSON pro Ereignis (`open`, `keepalive` alle 45 s, `message`). `NtfyStream.signals()` hält die Verbindung, meldet `Connected`/`Disconnected`/`Message`, verbindet nach Fehlern mit wachsender Pause neu (1 s, 2 s, 4 s … höchstens 60 s) und setzt dabei `since=<letzte Nachrichten-ID>`, damit nichts verloren geht. Das Lese-Timeout (90 s) liegt über dem Keepalive-Abstand und erkennt so tote Verbindungen.

- [ ] **Step 1: Failing tests schreiben**

`android/core/src/test/kotlin/de/safetube/core/push/NtfyParserTest.kt`:

```kotlin
package de.safetube.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyParserTest {

    @Test
    fun `Nachricht mit Titel und Tags`() {
        val event = NtfyParser.parseLine(
            """{"id":"m1","time":1794719652,"event":"message","topic":"yts-1","title":"Mia bittet um Freigabe",
               "message":"Video: Maus","tags":["request:9"],"priority":4}""",
        )!!

        assertEquals("message", event.event)
        assertEquals("m1", event.id)
        assertEquals(listOf("request:9"), event.tags)
        assertEquals(4, event.priority)
    }

    @Test
    fun `fehlende Felder bekommen Standardwerte`() {
        val event = NtfyParser.parseLine("""{"id":"k","time":1,"event":"keepalive","topic":"yts-1"}""")!!

        assertEquals("", event.title)
        assertEquals(emptyList<String>(), event.tags)
        assertEquals(3, event.priority)
    }

    @Test
    fun `leere und kaputte Zeilen werden übersprungen`() {
        assertNull(NtfyParser.parseLine(""))
        assertNull(NtfyParser.parseLine("   "))
        assertNull(NtfyParser.parseLine("kein json"))
        assertNull(NtfyParser.parseLine("""{"id":"x"}"""))
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/push/NtfyStreamTest.kt`:

```kotlin
package de.safetube.core.push

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyStreamTest {

    private lateinit var server: MockWebServer

    private val open = """{"id":"o","time":1,"event":"open","topic":"yts-abc"}"""
    private val keepalive = """{"id":"k","time":2,"event":"keepalive","topic":"yts-abc"}"""
    private fun message(id: String) =
        """{"id":"$id","time":3,"event":"message","topic":"yts-abc","title":"T","message":"M","tags":["info"]}"""

    private fun lines(vararg items: String) = items.joinToString("\n", postfix = "\n")

    private fun stream() = NtfyStream(OkHttpClient(), server.url("/").toString(), "yts-abc", backoffMillis = { 0L })

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `liest Nachrichten, verbindet neu und holt mit since nach`() = runBlocking {
        server.enqueue(MockResponse().setBody(lines(open, keepalive, message("m1"))))
        server.enqueue(MockResponse().setBody(lines(open, message("m2"))))

        val signals = withTimeout(10_000) { stream().signals().take(6).toList() }

        assertEquals(PushSignal.Connected, signals[0])
        assertEquals("m1", (signals[1] as PushSignal.Message).event.id)
        assertTrue(signals[2] is PushSignal.Disconnected)
        assertEquals(PushSignal.Connected, signals[3])
        assertEquals("m2", (signals[4] as PushSignal.Message).event.id)
        assertTrue(signals[5] is PushSignal.Disconnected)
        assertEquals("/yts-abc/json", server.takeRequest().path)
        assertEquals("/yts-abc/json?since=m1", server.takeRequest().path)
    }

    @Test
    fun `HTTP-Fehler meldet Trennung und versucht es erneut`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(lines(open, message("m1"))))

        val signals = withTimeout(10_000) { stream().signals().take(3).toList() }

        assertEquals(PushSignal.Disconnected("HTTP 500"), signals[0])
        assertEquals(PushSignal.Connected, signals[1])
        assertEquals("m1", (signals[2] as PushSignal.Message).event.id)
    }

    @Test
    fun `gespeicherte ID wird schon beim ersten Verbinden benutzt`() = runBlocking {
        server.enqueue(MockResponse().setBody(lines(open)))

        withTimeout(10_000) { stream().signals(initialSince = "alt42").take(1).toList() }

        assertEquals("/yts-abc/json?since=alt42", server.takeRequest().path)
    }

    @Test
    fun `Pausen wachsen bis 60 Sekunden`() {
        assertEquals(1_000L, NtfyStream.defaultBackoff(1))
        assertEquals(2_000L, NtfyStream.defaultBackoff(2))
        assertEquals(32_000L, NtfyStream.defaultBackoff(6))
        assertEquals(60_000L, NtfyStream.defaultBackoff(7))
        assertEquals(60_000L, NtfyStream.defaultBackoff(50))
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.push.Ntfy*'`
Expected: FAIL — `Unresolved reference: NtfyParser` / `NtfyStream`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/push/Ntfy.kt`:

```kotlin
package de.safetube.core.push

import de.safetube.core.api.ApiJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Ein Ereignis aus dem ntfy-JSON-Strom. ntfy lässt leere Felder weg, daher Standardwerte. */
@Serializable
data class NtfyEvent(
    val id: String = "",
    val time: Long = 0,
    val event: String,
    val topic: String = "",
    val title: String = "",
    val message: String = "",
    val tags: List<String> = emptyList(),
    val priority: Int = 3,
)

object NtfyParser {
    /** Liefert null für leere oder unlesbare Zeilen — die Verbindung soll daran nicht scheitern. */
    fun parseLine(line: String): NtfyEvent? {
        if (line.isBlank()) return null
        return try {
            ApiJson.decodeFromString<NtfyEvent>(line)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
```

`android/core/src/main/kotlin/de/safetube/core/push/NtfyStream.kt`:

```kotlin
package de.safetube.core.push

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface PushSignal {
    data object Connected : PushSignal
    data class Disconnected(val reason: String) : PushSignal
    data class Message(val event: NtfyEvent) : PushSignal
}

/**
 * Dauerverbindung zu einem ntfy-Topic. Endet nie von selbst; beendet wird sie durch Abbruch
 * der sammelnden Coroutine.
 */
class NtfyStream(
    client: OkHttpClient,
    private val baseUrl: String,
    private val topic: String,
    private val backoffMillis: (failures: Int) -> Long = ::defaultBackoff,
) {
    // ntfy schickt alle 45 s ein keepalive; 90 s ohne Daten heißt: Verbindung ist tot.
    private val streamClient = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    fun signals(initialSince: String? = null): Flow<PushSignal> = channelFlow {
        var since = initialSince
        var failures = 0
        while (true) {
            val call = streamClient.newCall(buildRequest(since))
            val reader = launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val source = response.body!!.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val event = NtfyParser.parseLine(line) ?: continue
                            when (event.event) {
                                "open" -> {
                                    failures = 0
                                    send(PushSignal.Connected)
                                }
                                "message" -> {
                                    if (event.id.isNotEmpty()) since = event.id
                                    send(PushSignal.Message(event))
                                }
                            }
                        }
                    }
                    send(PushSignal.Disconnected("Verbindung beendet"))
                } catch (e: IOException) {
                    if (!call.isCanceled()) send(PushSignal.Disconnected(e.message ?: e.javaClass.simpleName))
                }
            }
            try {
                reader.join()
            } finally {
                call.cancel() // bei Abbruch: blockierendes Lesen sofort beenden
            }
            failures++
            delay(backoffMillis(failures))
        }
    }

    private fun buildRequest(since: String?): Request {
        val url = (baseUrl.trimEnd('/') + "/" + topic + "/json").toHttpUrl().newBuilder()
            .apply { if (since != null) addQueryParameter("since", since) }
            .build()
        return Request.Builder().url(url).build()
    }

    companion object {
        fun defaultBackoff(failures: Int): Long {
            val exponent = (failures - 1).coerceIn(0, 6)
            return minOf(60_000L, 1_000L shl exponent)
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.push.Ntfy*'`
Expected: PASS (7 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: ntfy-Datenstrom mit Neuverbinden und since"
```

---

### Task 7: Eltern-Push auswerten, gemeldete Anfragen merken (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/push/ParentPush.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/push/SeenIds.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/push/ParentPushTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/push/SeenIdsTest.kt`

Tags laut `server/app/events.py`: `request:<id>` (neue Anfrage), `info` + `decided:<id>` (anderes Elternteil hat entschieden), `info` (Belohnung abgelaufen, neues Gerät), `alert` (Gerät meldet sich nicht, Störung). `OpenRequestCheck` baut für den 15-Minuten-Abgleich Benachrichtigungen im selben Wortlaut wie der Server.

- [ ] **Step 1: Failing tests schreiben**

`android/core/src/test/kotlin/de/safetube/core/push/ParentPushTest.kt`:

```kotlin
package de.safetube.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParentPushTest {

    private fun msg(vararg tags: String, event: String = "message") =
        NtfyEvent(id = "x", event = event, title = "Titel", message = "Text", tags = tags.toList())

    @Test
    fun `neue Anfrage`() {
        assertEquals(ParentPush.NewRequest(9, "Titel", "Text"), ParentPush.from(msg("request:9")))
    }

    @Test
    fun `anderes Elternteil hat entschieden`() {
        assertEquals(ParentPush.DecidedElsewhere(9, "Titel", "Text"), ParentPush.from(msg("info", "decided:9")))
    }

    @Test
    fun `Info und Warnung`() {
        assertEquals(ParentPush.Info("Titel", "Text"), ParentPush.from(msg("info")))
        assertEquals(ParentPush.Alert("Titel", "Text"), ParentPush.from(msg("alert")))
    }

    @Test
    fun `Unbekanntes, kaputte IDs und andere Ereignisse ignorieren`() {
        assertNull(ParentPush.from(msg("sync")))
        assertNull(ParentPush.from(msg()))
        assertNull(ParentPush.from(msg("request:abc")))
        assertNull(ParentPush.from(msg("request:9", event = "keepalive")))
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/push/SeenIdsTest.kt`:

```kotlin
package de.safetube.core.push

import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.TargetType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenIdsTest {

    @Test
    fun `merkt sich IDs und meldet nur neue`() {
        val seen = SeenIds()

        assertTrue(seen.add(1))
        assertFalse(seen.add(1))
        assertTrue(1 in seen)
    }

    @Test
    fun `älteste fallen bei voller Liste heraus`() {
        val seen = SeenIds(capacity = 3)
        listOf(1, 2, 3, 4).forEach { seen.add(it) }

        assertFalse(1 in seen)
        assertEquals("2,3,4", seen.encode())
    }

    @Test
    fun `speichern und laden`() {
        val seen = SeenIds.decode("5, 6,kaputt,,7")

        assertEquals("5,6,7", seen.encode())
        assertEquals("", SeenIds.decode(null).encode())
    }

    private fun request(
        id: Int,
        type: TargetType = TargetType.VIDEO,
        message: String = "",
        liftsBlock: Boolean = false,
    ) = ContentRequest(
        id = id, childId = 1, childName = "Mia", targetType = type, targetId = "dQw4w9WgXcQ", title = "Maus",
        channelId = "UCx", channelName = "Die Maus", url = "", thumbnailUrl = "", childMessage = message,
        sourceVideoId = "", sourceVideoTitle = "", status = RequestStatus.OPEN, decidedByName = null,
        decidedAt = null, reason = "", createdAt = Instant.parse("2026-11-15T06:00:00Z"), liftsBlock = liftsBlock,
    )

    @Test
    fun `Abgleich meldet nur noch nicht gemeldete Anfragen und merkt sie sich`() {
        val seen = SeenIds.decode("1")

        val fresh = OpenRequestCheck.newOnes(listOf(request(1), request(2)), seen)

        assertEquals(listOf(2), fresh.map { it.id })
        assertTrue(2 in seen)
        assertEquals(emptyList<ContentRequest>(), OpenRequestCheck.newOnes(listOf(request(2)), seen))
    }

    @Test
    fun `Benachrichtigung im Wortlaut des Servers`() {
        val video = OpenRequestCheck.notificationFor(request(3, message = "bitte!"))
        val channel = OpenRequestCheck.notificationFor(request(4, type = TargetType.CHANNEL))

        assertEquals(ParentPush.NewRequest(3, "Mia bittet um Freigabe", "Video: Maus (Die Maus)\n„bitte!“"), video)
        assertEquals(ParentPush.NewRequest(4, "Mia bittet um Freigabe", "Kanal: Maus"), channel)
    }

    @Test
    fun `Aufhebung einer Sperre im Wortlaut des Servers`() {
        val lift = OpenRequestCheck.notificationFor(request(5, liftsBlock = true))

        assertEquals("Mia bittet um Aufhebung einer Sperre", lift.title)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.push.ParentPushTest' --tests 'de.safetube.core.push.SeenIdsTest'`
Expected: FAIL — `Unresolved reference: ParentPush` / `SeenIds`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/push/ParentPush.kt`:

```kotlin
package de.safetube.core.push

/** Was eine Push-Nachricht für die Eltern-App bedeutet. */
sealed interface ParentPush {
    val title: String
    val message: String

    data class NewRequest(val requestId: Int, override val title: String, override val message: String) : ParentPush
    data class DecidedElsewhere(val requestId: Int, override val title: String, override val message: String) : ParentPush
    data class Info(override val title: String, override val message: String) : ParentPush
    data class Alert(override val title: String, override val message: String) : ParentPush

    companion object {
        fun from(event: NtfyEvent): ParentPush? {
            if (event.event != "message") return null
            val tags = event.tags
            if (tags.any { it.startsWith("request:") }) {
                return idFrom(tags, "request:")?.let { NewRequest(it, event.title, event.message) }
            }
            if (tags.any { it.startsWith("decided:") }) {
                return idFrom(tags, "decided:")?.let { DecidedElsewhere(it, event.title, event.message) }
            }
            if ("alert" in tags) return Alert(event.title, event.message)
            if ("info" in tags) return Info(event.title, event.message)
            return null
        }

        private fun idFrom(tags: List<String>, prefix: String): Int? =
            tags.first { it.startsWith(prefix) }.removePrefix(prefix).toIntOrNull()
    }
}
```

`android/core/src/main/kotlin/de/safetube/core/push/SeenIds.kt`:

```kotlin
package de.safetube.core.push

import de.safetube.core.api.ContentRequest
import de.safetube.core.api.TargetType

/** IDs bereits gemeldeter Anfragen, begrenzt auf die neuesten [capacity]. */
class SeenIds(private val capacity: Int = 500, initial: Collection<Int> = emptyList()) {
    private val ids = LinkedHashSet<Int>()

    init {
        initial.forEach { add(it) }
    }

    /** true, wenn die ID neu war. */
    @Synchronized
    fun add(id: Int): Boolean {
        if (!ids.add(id)) return false
        while (ids.size > capacity) ids.remove(ids.first())
        return true
    }

    @Synchronized
    operator fun contains(id: Int): Boolean = id in ids

    @Synchronized
    fun encode(): String = ids.joinToString(",")

    companion object {
        fun decode(text: String?, capacity: Int = 500): SeenIds =
            SeenIds(capacity, text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() })
    }
}

/** 15-Minuten-Abgleich: welche offenen Anfragen müssen noch gemeldet werden? */
object OpenRequestCheck {

    /** Liefert die noch nicht gemeldeten Anfragen und trägt sie in [seen] ein. */
    fun newOnes(open: List<ContentRequest>, seen: SeenIds): List<ContentRequest> = open.filter { seen.add(it.id) }

    /** Gleicher Wortlaut wie server/app/events.py request_created(). */
    fun notificationFor(request: ContentRequest): ParentPush.NewRequest {
        val label = if (request.targetType == TargetType.VIDEO) "Video" else "Kanal"
        var message = "$label: ${request.title.ifEmpty { request.targetId }}"
        if (request.channelName.isNotEmpty() && request.targetType == TargetType.VIDEO) {
            message += " (${request.channelName})"
        }
        if (request.childMessage.isNotEmpty()) message += "\n„${request.childMessage}“"
        val asks = if (request.liftsBlock) "um Aufhebung einer Sperre" else "um Freigabe"
        return ParentPush.NewRequest(request.id, "${request.childName} bittet $asks", message)
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.push.ParentPushTest' --tests 'de.safetube.core.push.SeenIdsTest'`
Expected: PASS (10 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: Eltern-Push auswerten, gemeldete Anfragen merken"
```

---

### Task 8: Anzeigeformate und Belohnungsdauer (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/format/Formats.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/format/RewardDuration.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/format/FormatsTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/format/RewardDurationTest.kt`

Alle Zeiten werden in `Europe/Berlin` angezeigt (wie die Statistik-Tage des Servers). Wochentage aus einer festen Liste statt `DayOfWeek.getDisplayName` — dessen Ausgabe („Mo“ oder „Mo.“) hängt von JDK und Android-Version ab. Die Obergrenze von 30 Tagen entspricht `MAX_REWARD_MINUTES` in `server/app/schemas.py`.

- [ ] **Step 1: Failing tests schreiben**

`android/core/src/test/kotlin/de/safetube/core/format/FormatsTest.kt`:

```kotlin
package de.safetube.core.format

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun Dauer() {
        assertEquals("0 s", Formats.duration(0))
        assertEquals("59 s", Formats.duration(59))
        assertEquals("1 min", Formats.duration(60))
        assertEquals("59 min", Formats.duration(3599))
        assertEquals("1 h 05 min", Formats.duration(3900))
        assertEquals("26 h 00 min", Formats.duration(93600))
    }

    @Test
    fun `Datum und Uhrzeit in Berliner Zeit`() {
        // 15.11.2026 ist Winterzeit (UTC+1)
        assertEquals("15.11.2026, 07:12", Formats.dateTime(Instant.parse("2026-11-15T06:12:48Z")))
        assertEquals("07:12", Formats.time(Instant.parse("2026-11-15T06:12:48Z")))
        // Sommerzeit (UTC+2)
        assertEquals("01.07.2026, 14:00", Formats.dateTime(Instant.parse("2026-07-01T12:00:00Z")))
    }

    @Test
    fun Tagesbeschriftung() {
        assertEquals("So 15.11.", Formats.dayLabel("2026-11-15"))
        assertEquals("Mo 16.11.", Formats.dayLabel("2026-11-16"))
    }

    @Test
    fun Restzeit() {
        val now = Instant.parse("2026-11-15T10:00:00Z")
        assertEquals("abgelaufen", Formats.remaining(now, now))
        assertEquals("abgelaufen", Formats.remaining(now.minusSeconds(5), now))
        assertEquals("noch unter 1 min", Formats.remaining(now.plusSeconds(30), now))
        assertEquals("noch 4 min", Formats.remaining(now.plusSeconds(4 * 60 + 10), now))
        assertEquals("noch 1 h 05 min", Formats.remaining(now.plusSeconds(3900), now))
        assertEquals("noch 2 T 3 h", Formats.remaining(now.plusSeconds(2 * 86400 + 3 * 3600 + 59), now))
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/format/RewardDurationTest.kt`:

```kotlin
package de.safetube.core.format

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardDurationTest {

    @Test
    fun `Dauer in Minuten`() {
        assertEquals(90, RewardDuration.minutes(days = 0, hours = 1, minutes = 30))
        assertEquals(1440 * 2 + 5, RewardDuration.minutes(days = 2, hours = 0, minutes = 5))
        assertEquals(RewardDuration.MAX_MINUTES, RewardDuration.minutes(days = 30, hours = 0, minutes = 0))
    }

    @Test
    fun `ungültige Dauer`() {
        assertNull(RewardDuration.minutes(0, 0, 0))
        assertNull(RewardDuration.minutes(30, 0, 1))
        assertNull(RewardDuration.minutes(0, -1, 30))
    }

    @Test
    fun `Endzeitpunkt aus Berliner Datum und Uhrzeit`() {
        val now = Instant.parse("2026-11-15T10:00:00Z")

        val until = RewardDuration.until(LocalDate.of(2026, 11, 15), LocalTime.of(18, 30), now)

        assertEquals(Instant.parse("2026-11-15T17:30:00Z"), until)
    }

    @Test
    fun `Endzeitpunkt in der Vergangenheit oder zu weit weg`() {
        val now = Instant.parse("2026-11-15T10:00:00Z")

        assertNull(RewardDuration.until(LocalDate.of(2026, 11, 15), LocalTime.of(10, 0), now))
        assertNull(RewardDuration.until(LocalDate.of(2026, 12, 20), LocalTime.of(10, 0), now))
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.format.*'`
Expected: FAIL — `Unresolved reference: Formats` / `RewardDuration`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/format/Formats.kt`:

```kotlin
package de.safetube.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Texte für Zeiten und Dauern, immer in Berliner Zeit. */
object Formats {
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
    }

    fun dateTime(instant: Instant, zone: ZoneId = ZONE): String = DATE_TIME.format(instant.atZone(zone))

    fun time(instant: Instant, zone: ZoneId = ZONE): String = TIME.format(instant.atZone(zone))

    /** "2026-11-15" → "So 15.11." */
    fun dayLabel(isoDate: String): String {
        val date = LocalDate.parse(isoDate)
        return "%s %02d.%02d.".format(WEEKDAYS[date.dayOfWeek.value - 1], date.dayOfMonth, date.monthValue)
    }

    /** Restzeit bis [until], z. B. für den Belohnungsmodus. */
    fun remaining(until: Instant, now: Instant): String {
        val seconds = Duration.between(now, until).seconds
        if (seconds <= 0) return "abgelaufen"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> "noch $days T $hours h"
            hours > 0 -> "noch %d h %02d min".format(hours, minutes)
            minutes > 0 -> "noch $minutes min"
            else -> "noch unter 1 min"
        }
    }
}
```

`android/core/src/main/kotlin/de/safetube/core/format/RewardDuration.kt`:

```kotlin
package de.safetube.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Prüft die Eingaben für den Belohnungsmodus, bevor sie an den Server gehen. */
object RewardDuration {
    /** Wie MAX_REWARD_MINUTES in server/app/schemas.py. */
    const val MAX_MINUTES = 60 * 24 * 30

    /** Gesamtdauer in Minuten oder null, wenn sie nicht zwischen 1 Minute und 30 Tagen liegt. */
    fun minutes(days: Int, hours: Int, minutes: Int): Int? {
        if (days < 0 || hours < 0 || minutes < 0) return null
        val total = days.toLong() * 1440 + hours.toLong() * 60 + minutes
        return if (total in 1..MAX_MINUTES) total.toInt() else null
    }

    /** Endzeitpunkt aus Datum und Uhrzeit in [zone] oder null, wenn er nicht in der Zukunft (≤ 30 Tage) liegt. */
    fun until(date: LocalDate, time: LocalTime, now: Instant, zone: ZoneId = Formats.ZONE): Instant? {
        val instant = date.atTime(time).atZone(zone).toInstant()
        val latest = now.plus(Duration.ofMinutes(MAX_MINUTES.toLong()))
        return if (instant.isAfter(now) && !instant.isAfter(latest)) instant else null
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.format.*'`
Expected: PASS (8 Tests).

- [ ] **Step 5: Commit**

```bash
git add android/core/src
git commit -m "core: Anzeigeformate und Prüfung der Belohnungsdauer"
```

---

### Task 9: YouTube über NewPipeExtractor (`core`)

**Files:**
- Create: `android/core/src/main/kotlin/de/safetube/core/extractor/OkHttpDownloader.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/extractor/YouTubeModels.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/extractor/Mapping.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/extractor/StreamChoice.kt`
- Create: `android/core/src/main/kotlin/de/safetube/core/extractor/YouTubeSource.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/extractor/OkHttpDownloaderTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/extractor/MappingTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/extractor/StreamChoiceTest.kt`
- Test: `android/core/src/test/kotlin/de/safetube/core/live/LiveYouTubeTest.kt`

NewPipe-Klassen bleiben hinter eigenen Typen (`VideoItem`, `ChannelPage`, `StreamDetails`, `PlaybackSource`). Der Rest der App kennt NewPipeExtractor nicht — ein Versionswechsel betrifft nur dieses Paket. Kanal-IDs werden aus den Uploader-URLs gelesen (`/channel/UC…`, siehe Festlegung 12). Fehler werden zu `YouTubeException` mit fertigem deutschem Text.

Verwendete NewPipe-Schnittstellen (geprüft gegen v0.27.1): `Downloader.execute(Request): Response`, `NewPipe.init(Downloader, Localization, ContentCountry)`, `ServiceList.YouTube`, `SearchInfo.getInfo(service, searchQHFactory.fromQuery(q, filters, ""))`, `ChannelInfo.getInfo(service, url)` mit `tabs`/`avatars`/`id`, `ChannelTabInfo.getInfo(service, tab)`, `StreamInfo.getInfo(service, url)` mit `videoStreams`, `videoOnlyStreams`, `audioStreams`, `hlsUrl`, `uploaderUrl`.

- [ ] **Step 1: Failing tests schreiben**

`android/core/src/test/kotlin/de/safetube/core/extractor/OkHttpDownloaderTest.kt`:

```kotlin
package de.safetube.core.extractor

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class OkHttpDownloaderTest {

    private lateinit var server: MockWebServer
    private val downloader = OkHttpDownloader(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET mit eigenen Headern und Antwort-Headern`() {
        server.enqueue(MockResponse().setBody("hallo").addHeader("X-Antwort", "ja"))

        val response = downloader.execute(
            Request.newBuilder().get(server.url("/a").toString()).setHeader("X-Test", "eins").build(),
        )

        assertEquals(200, response.responseCode())
        assertEquals("hallo", response.responseBody())
        assertEquals("ja", response.getHeader("X-Antwort"))
        val request = server.takeRequest()
        assertEquals("eins", request.getHeader("X-Test"))
        assertTrue(request.getHeader("User-Agent")!!.contains("Firefox"))
    }

    @Test
    fun `POST mit Inhalt`() {
        server.enqueue(MockResponse().setBody("{}"))

        downloader.execute(Request.newBuilder().post(server.url("/b").toString(), "daten".toByteArray()).build())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("daten", request.body.readUtf8())
    }

    @Test
    fun `nach Weiterleitung steht die letzte URL in der Antwort`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ziel"))
        server.enqueue(MockResponse().setBody("angekommen"))

        val response = downloader.execute(Request.newBuilder().get(server.url("/start").toString()).build())

        assertTrue(response.latestUrl().endsWith("/ziel"))
    }

    @Test(expected = ReCaptchaException::class)
    fun `429 ist eine reCAPTCHA-Sperre`() {
        server.enqueue(MockResponse().setResponseCode(429))

        downloader.execute(Request.newBuilder().get(server.url("/c").toString()).build())
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/extractor/MappingTest.kt`:

```kotlin
package de.safetube.core.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class MappingTest {

    private val serviceId = ServiceList.YouTube.serviceId

    private fun image(url: String, level: Image.ResolutionLevel) = Image(url, -1, -1, level)

    @Test
    fun `Video aus Suchergebnis`() {
        val item = StreamInfoItem(serviceId, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Never Gonna", StreamType.VIDEO_STREAM)
            .apply {
                uploaderUrl = "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
                uploaderName = "Rick Astley"
                duration = 213
                textualUploadDate = "vor 15 Jahren"
                thumbnails = listOf(
                    image("https://i/low.jpg", Image.ResolutionLevel.LOW),
                    image("https://i/medium.jpg", Image.ResolutionLevel.MEDIUM),
                )
            }

        val video = Mapping.video(item)!!

        assertEquals("dQw4w9WgXcQ", video.videoId)
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", video.channelId)
        assertEquals("Rick Astley", video.channelName)
        assertEquals("https://i/medium.jpg", video.thumbnailUrl)
        assertEquals(213L, video.durationSeconds)
        assertEquals(false, video.isShort)
    }

    @Test
    fun `Shorts werden erkannt, fremde URLs verworfen`() {
        val short = StreamInfoItem(serviceId, "https://www.youtube.com/shorts/dQw4w9WgXcQ", "Kurz", StreamType.VIDEO_STREAM)
        val broken = StreamInfoItem(serviceId, "https://example.com/x", "Kaputt", StreamType.VIDEO_STREAM)

        assertTrue(Mapping.video(short)!!.isShort)
        assertNull(Mapping.video(broken))
    }

    @Test
    fun `Kanal aus Suchergebnis`() {
        val item = ChannelInfoItem(serviceId, "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", "Rick Astley")
            .apply { subscriberCount = 4_000_000 }

        val channel = Mapping.channel(item)

        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", channel.channelId)
        assertEquals("Rick Astley", channel.name)
        assertEquals(4_000_000L, channel.subscribers)
        assertNull(channel.avatarUrl)
    }

    @Test
    fun `Bildauswahl bevorzugt mittlere Auflösung`() {
        assertNull(Mapping.image(emptyList()))
        assertEquals("https://h", Mapping.image(listOf(image("https://h", Image.ResolutionLevel.HIGH))))
        assertEquals(
            "https://u",
            Mapping.image(listOf(image("https://u", Image.ResolutionLevel.UNKNOWN), image("https://x", Image.ResolutionLevel.UNKNOWN))),
        )
    }

    private fun video(id: String, resolution: String, videoOnly: Boolean, format: MediaFormat = MediaFormat.MPEG_4) =
        VideoStream.Builder()
            .setId(id)
            .setContent("https://v/$id", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(videoOnly)
            .setResolution(resolution)
            .build()

    private fun audio(id: String, bitrate: Int) =
        AudioStream.Builder()
            .setId(id)
            .setContent("https://a/$id", true)
            .setMediaFormat(MediaFormat.M4A)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(bitrate)
            .build()

    @Test
    fun `Stream-Kandidaten mit Höhe aus der Auflösung`() {
        val candidates = Mapping.candidates(
            videos = listOf(video("18", "360p", videoOnly = false)),
            videoOnly = listOf(video("136", "720p", videoOnly = true), video("299", "1080p60", videoOnly = true)),
            audio = listOf(audio("140", 128)),
            hlsUrl = "",
        )

        assertEquals(
            listOf(
                StreamCandidate("https://v/18", StreamKind.MUXED, 360, 0, "video/mp4"),
                StreamCandidate("https://v/136", StreamKind.VIDEO_ONLY, 720, 0, "video/mp4"),
                StreamCandidate("https://v/299", StreamKind.VIDEO_ONLY, 1080, 0, "video/mp4"),
                StreamCandidate("https://a/140", StreamKind.AUDIO, 0, 128, "audio/mp4"),
            ),
            candidates,
        )
    }

    @Test
    fun `HLS wird als eigener Kandidat geführt`() {
        val candidates = Mapping.candidates(emptyList(), emptyList(), emptyList(), "https://hls/master.m3u8")

        assertEquals(listOf(StreamCandidate("https://hls/master.m3u8", StreamKind.HLS, 0, 0, null)), candidates)
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/extractor/StreamChoiceTest.kt`:

```kotlin
package de.safetube.core.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamChoiceTest {

    private fun muxed(height: Int) = StreamCandidate("m$height", StreamKind.MUXED, height, 0, "video/mp4")
    private fun videoOnly(height: Int, mime: String = "video/mp4") = StreamCandidate("v$height$mime", StreamKind.VIDEO_ONLY, height, 0, mime)
    private fun audio(bitrate: Int, mime: String = "audio/mp4") = StreamCandidate("a$bitrate$mime", StreamKind.AUDIO, 0, bitrate, mime)

    @Test
    fun `kombinierter Stream hat Vorrang`() {
        val source = StreamChoice.pick(listOf(muxed(360), videoOnly(720), audio(128)))

        assertEquals(PlaybackSource.Progressive("m360"), source)
    }

    @Test
    fun `höchster kombinierter Stream bis 720p`() {
        assertEquals(PlaybackSource.Progressive("m720"), StreamChoice.pick(listOf(muxed(240), muxed(720), muxed(1080))))
        assertEquals(PlaybackSource.Progressive("m1080"), StreamChoice.pick(listOf(muxed(1080))))
    }

    @Test
    fun `ohne kombinierten Stream Bild und Ton zusammenführen`() {
        val source = StreamChoice.pick(
            listOf(videoOnly(1080), videoOnly(720, "video/webm"), videoOnly(720), audio(160, "audio/webm"), audio(128)),
        )

        assertEquals(PlaybackSource.Merged(videoUrl = "v720video/mp4", audioUrl = "a128audio/mp4"), source)
    }

    @Test
    fun `sonst HLS, sonst nichts`() {
        assertEquals(PlaybackSource.Hls("h"), StreamChoice.pick(listOf(StreamCandidate("h", StreamKind.HLS, 0, 0, null))))
        assertNull(StreamChoice.pick(listOf(videoOnly(720))))
        assertNull(StreamChoice.pick(emptyList()))
    }
}
```

`android/core/src/test/kotlin/de/safetube/core/live/LiveYouTubeTest.kt`:

```kotlin
package de.safetube.core.live

import de.safetube.core.extractor.NewPipeSource
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.StreamChoice
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gegen das echte YouTube. Läuft nur mit: ./gradlew :core:test -Plive */
class LiveYouTubeTest {

    private val source = NewPipeSource(OkHttpDownloader(OkHttpClient()))
    private val videoId = "dQw4w9WgXcQ"
    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"

    @Test
    fun suche() = runBlocking {
        val hits = source.search("Rick Astley Never Gonna Give You Up", SearchFilter.VIDEOS)

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it is SearchHit.Video })
    }

    @Test
    fun kanal() = runBlocking {
        val page = source.channel(channelId, maxVideos = 10)

        assertEquals(channelId, page.channelId)
        assertTrue(page.videos.isNotEmpty())
        assertTrue(page.videos.size <= 10)
    }

    @Test
    fun stream() = runBlocking {
        val details = source.stream(videoId)

        assertEquals(channelId, details.channelId)
        assertNotNull(StreamChoice.pick(details.candidates))
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.extractor.*'`
Expected: FAIL — `Unresolved reference: OkHttpDownloader` / `Mapping` / `StreamChoice`.

- [ ] **Step 3: Implementieren**

`android/core/src/main/kotlin/de/safetube/core/extractor/OkHttpDownloader.kt`:

```kotlin
package de.safetube.core.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** HTTP für NewPipeExtractor über OkHttp (gleiches Muster wie in der NewPipe-App). */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            request.httpMethod() == "POST" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
```

`android/core/src/main/kotlin/de/safetube/core/extractor/YouTubeModels.kt`:

```kotlin
package de.safetube.core.extractor

data class VideoItem(
    val videoId: String,
    val title: String,
    /** Kanal-ID `UC…`, falls aus der Uploader-URL lesbar. */
    val channelId: String?,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val isShort: Boolean,
    val uploadedText: String?,
)

data class ChannelItem(
    val channelId: String?,
    val name: String,
    val url: String,
    val avatarUrl: String?,
    val subscribers: Long,
)

data class ChannelPage(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val videos: List<VideoItem>,
)

enum class StreamKind { MUXED, VIDEO_ONLY, AUDIO, HLS }

data class StreamCandidate(
    val url: String,
    val kind: StreamKind,
    /** Bildhöhe in Pixeln, 0 = unbekannt bzw. Ton. */
    val height: Int,
    /** Durchschnittliche Bitrate (kbit/s) bei Ton, sonst 0. */
    val bitrate: Int,
    val mime: String?,
)

data class StreamDetails(
    val videoId: String,
    val title: String,
    val channelId: String?,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val candidates: List<StreamCandidate>,
)

/** Was der Player abspielen soll. */
sealed interface PlaybackSource {
    data class Progressive(val url: String) : PlaybackSource
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackSource
    data class Hls(val url: String) : PlaybackSource
}

sealed interface SearchHit {
    data class Video(val item: VideoItem) : SearchHit
    data class Channel(val item: ChannelItem) : SearchHit
}

enum class SearchFilter(val contentFilters: List<String>) {
    ALL(emptyList()),
    VIDEOS(listOf("videos")),
    CHANNELS(listOf("channels")),
}
```

`android/core/src/main/kotlin/de/safetube/core/extractor/Mapping.kt`:

```kotlin
package de.safetube.core.extractor

import de.safetube.core.youtube.YouTubeIds
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** NewPipe-Typen → eigene Typen. Einzige Stelle (neben YouTubeSource), die NewPipe-Klassen kennt. */
internal object Mapping {

    private val RESOLUTION_HEIGHT = Regex("^(\\d+)p")
    private val IMAGE_PREFERENCE = listOf(
        Image.ResolutionLevel.MEDIUM,
        Image.ResolutionLevel.HIGH,
        Image.ResolutionLevel.LOW,
    )

    fun video(item: StreamInfoItem): VideoItem? {
        val videoId = YouTubeIds.videoIdFromUrl(item.url) ?: return null
        return VideoItem(
            videoId = videoId,
            title = item.name.orEmpty(),
            channelId = YouTubeIds.channelIdFromUrl(item.uploaderUrl),
            channelName = item.uploaderName.orEmpty(),
            thumbnailUrl = image(item.thumbnails),
            durationSeconds = item.duration.coerceAtLeast(0),
            isShort = item.isShortFormContent || item.url.contains("/shorts/"),
            uploadedText = item.textualUploadDate,
        )
    }

    fun channel(item: ChannelInfoItem): ChannelItem = ChannelItem(
        channelId = YouTubeIds.channelIdFromUrl(item.url),
        name = item.name.orEmpty(),
        url = item.url,
        avatarUrl = image(item.thumbnails),
        subscribers = item.subscriberCount,
    )

    fun image(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        for (level in IMAGE_PREFERENCE) {
            images.firstOrNull { it.estimatedResolutionLevel == level }?.let { return it.url }
        }
        return images.first().url
    }

    fun candidates(
        videos: List<VideoStream>,
        videoOnly: List<VideoStream>,
        audio: List<AudioStream>,
        hlsUrl: String?,
    ): List<StreamCandidate> = buildList {
        videos.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.isVideoOnly }
            .forEach { add(StreamCandidate(it.content, StreamKind.MUXED, height(it), 0, it.format?.mimeType)) }
        videoOnly.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(StreamCandidate(it.content, StreamKind.VIDEO_ONLY, height(it), 0, it.format?.mimeType)) }
        val progressiveAudio = audio.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Bei mehreren Tonspuren (Synchronfassungen) die Originalspur nehmen.
        val original = progressiveAudio.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        original.ifEmpty { progressiveAudio }
            .forEach { add(StreamCandidate(it.content, StreamKind.AUDIO, 0, it.averageBitrate, it.format?.mimeType)) }
        if (!hlsUrl.isNullOrBlank()) add(StreamCandidate(hlsUrl, StreamKind.HLS, 0, 0, null))
    }

    private fun height(stream: VideoStream): Int =
        stream.height.takeIf { it > 0 }
            ?: RESOLUTION_HEIGHT.find(stream.resolution.orEmpty())?.groupValues?.get(1)?.toInt()
            ?: 0
}
```

`android/core/src/main/kotlin/de/safetube/core/extractor/StreamChoice.kt`:

```kotlin
package de.safetube.core.extractor

/**
 * Welche Streams spielt die Vorschau? (Festlegung 11 im Plan)
 * 1. kombinierter Stream (Bild+Ton), höchster bis [maxHeight], sonst der kleinste
 * 2. Video-only bis [maxHeight] + Ton zusammengeführt (mp4/m4a bevorzugt)
 * 3. HLS
 */
object StreamChoice {

    fun pick(candidates: List<StreamCandidate>, maxHeight: Int = 720): PlaybackSource? {
        val byQuality = compareByDescending<StreamCandidate> { it.height }.thenByDescending { it.mime == "video/mp4" }

        val muxed = candidates.filter { it.kind == StreamKind.MUXED }
        val bestMuxed = muxed.filter { it.height <= maxHeight }.sortedWith(byQuality).firstOrNull()
            ?: muxed.minByOrNull { it.height }
        if (bestMuxed != null) return PlaybackSource.Progressive(bestMuxed.url)

        val video = candidates.filter { it.kind == StreamKind.VIDEO_ONLY && it.height in 1..maxHeight }
            .sortedWith(byQuality).firstOrNull()
        val audio = candidates.filter { it.kind == StreamKind.AUDIO }
            .sortedWith(compareByDescending<StreamCandidate> { it.mime == "audio/mp4" }.thenByDescending { it.bitrate })
            .firstOrNull()
        if (video != null && audio != null) return PlaybackSource.Merged(video.url, audio.url)

        return candidates.firstOrNull { it.kind == StreamKind.HLS }?.let { PlaybackSource.Hls(it.url) }
    }
}
```

`android/core/src/main/kotlin/de/safetube/core/extractor/YouTubeSource.kt`:

```kotlin
package de.safetube.core.extractor

import de.safetube.core.api.YouTubeException
import de.safetube.core.youtube.YouTubeIds
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Zugriff auf YouTube ohne Filter (Eltern-App). Die Kinder-App setzt in Plan 3 das Gate davor. */
interface YouTubeSource {
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): List<SearchHit>

    /** [channelIdOrUrl]: Kanal-ID `UC…` oder eine beliebige Kanal-URL (auch `@handle`). */
    suspend fun channel(channelIdOrUrl: String, maxVideos: Int = 30): ChannelPage

    suspend fun stream(videoId: String): StreamDetails
}

/** NewPipe wird einmal pro Prozess initialisiert (deutsche Sprache, Land DE). */
object NewPipeSetup {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensure(downloader: Downloader) {
        if (initialized) return
        NewPipe.init(downloader, Localization("de", "DE"), ContentCountry("DE"))
        initialized = true
    }
}

class NewPipeSource(downloader: Downloader) : YouTubeSource {

    init {
        NewPipeSetup.ensure(downloader)
    }

    private val service get() = ServiceList.YouTube

    override suspend fun search(query: String, filter: SearchFilter): List<SearchHit> = io {
        val handler = service.searchQHFactory.fromQuery(query, filter.contentFilters, "")
        SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> Mapping.video(item)?.let(SearchHit::Video)
                is ChannelInfoItem -> SearchHit.Channel(Mapping.channel(item))
                else -> null
            }
        }
    }

    override suspend fun channel(channelIdOrUrl: String, maxVideos: Int): ChannelPage = io {
        val url = if (YouTubeIds.isChannelId(channelIdOrUrl)) YouTubeIds.channelUrl(channelIdOrUrl) else channelIdOrUrl
        val info = ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val videos = videosTab
            ?.let { ChannelTabInfo.getInfo(service, it).relatedItems }
            .orEmpty()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(Mapping::video)
        ChannelPage(
            channelId = info.id,
            name = info.name.orEmpty(),
            avatarUrl = Mapping.image(info.avatars),
            videos = videos.take(maxVideos),
        )
    }

    override suspend fun stream(videoId: String): StreamDetails = io {
        val info = StreamInfo.getInfo(service, YouTubeIds.videoUrl(videoId))
        StreamDetails(
            videoId = videoId,
            title = info.name.orEmpty(),
            channelId = YouTubeIds.channelIdFromUrl(info.uploaderUrl),
            channelName = info.uploaderName.orEmpty(),
            thumbnailUrl = Mapping.image(info.thumbnails),
            durationSeconds = info.duration,
            candidates = Mapping.candidates(info.videoStreams, info.videoOnlyStreams, info.audioStreams, info.hlsUrl),
        )
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: ReCaptchaException) {
            throw YouTubeException("YouTube verlangt gerade eine Bestätigung. Bitte später noch einmal versuchen.", e)
        } catch (e: ExtractionException) {
            throw YouTubeException("YouTube antwortet gerade nicht wie erwartet.", e)
        } catch (e: IOException) {
            throw YouTubeException("YouTube ist gerade nicht erreichbar.", e)
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün sehen**

Run: `cd android && ./gradlew --console=plain :core:test --tests 'de.safetube.core.extractor.*'`
Expected: PASS (14 Tests). Der Live-Test ist ausgeschlossen.

- [ ] **Step 5: Live-Test einmal von Hand**

Run: `cd android && ./gradlew --console=plain :core:test -Plive --tests 'de.safetube.core.live.*'`
Expected: PASS (3 Tests). Scheitert er mit `YouTubeException`, die Ursache (`--info`, Stacktrace) ansehen: Netz, reCAPTCHA (dann später erneut) oder eine Änderung bei YouTube, die eine neuere NewPipeExtractor-Version braucht. Der Live-Test blockiert den Plan nicht, sein Ergebnis gehört aber in den Zwischenstand an Jessica.

- [ ] **Step 6: Gesamte `core`-Suite und Commit**

Run: `cd android && ./gradlew --console=plain :core:test`
Expected: BUILD SUCCESSFUL, alle Tests aus Task 1–9 grün.

```bash
git add android/core/src
git commit -m "core: YouTube über NewPipeExtractor hinter eigenen Typen"
```

---

### Task 10: Android-Gerüst `core-android` und `parent`

**Files:**
- Create: `android/core-android/build.gradle.kts`, `android/core-android/src/main/AndroidManifest.xml`
- Create: `android/core-android/src/main/kotlin/de/safetube/android/ui/Theme.kt`
- Create: `android/parent/build.gradle.kts`, `android/parent/src/main/AndroidManifest.xml`
- Create: `android/parent/src/main/res/values/strings.xml`, `android/parent/src/main/res/drawable/ic_launcher.xml`, `android/parent/src/main/res/drawable/ic_notification.xml`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/ParentApp.kt`, `.../parent/MainActivity.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/SmokeTest.kt`

Ab hier wird auf dem Thinkbook gebaut (`android/tools/remote-build.sh …`, Festlegung 14). Der erste Lauf lädt Android-Plattform 35, Build-Tools und alle Bibliotheken und dauert entsprechend.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/SmokeTest.kt`:

```kotlin
package de.safetube.parent

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.safetube.android.ui.SafeTubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Compose mit unserem Theme läuft unter Robolectric`() {
        compose.setContent { SafeTubeTheme { Text("Hallo SafeTube") } }

        compose.onNodeWithText("Hallo SafeTube").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest`
Expected: FAIL — Gradle findet kein Build-Skript für `:parent` bzw. `Unresolved reference: SafeTubeTheme`.

- [ ] **Step 3: Module anlegen**

`android/core-android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.safetube.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.lifecycle.service)
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    coreLibraryDesugaring(libs.desugar.nio)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
```

`android/core-android/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

`android/core-android/src/main/kotlin/de/safetube/android/ui/Theme.kt`:

```kotlin
package de.safetube.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material 3 mit den Systemfarben des Geräts (ab Android 12 immer verfügbar, minSdk 31). */
@Composable
fun SafeTubeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
```

`android/parent/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.safetube.parent"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.safetube.parent"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_SERVER", "\"https://safetube.isoletta.me\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-android"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    coreLibraryDesugaring(libs.desugar.nio)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`android/parent/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".ParentApp"
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`android/parent/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SafeTube Eltern</string>
</resources>
```

`android/parent/src/main/res/drawable/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="#2E7D32"
        android:pathData="M8,4h32a4,4 0,0 1,4 4v32a4,4 0,0 1,-4 4H8a4,4 0,0 1,-4 -4V8a4,4 0,0 1,4 -4z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M19,15v18l14,-9z" />
</vector>
```

`android/parent/src/main/res/drawable/ic_notification.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M8,5v14l11,-7z" />
</vector>
```

`android/parent/src/main/kotlin/de/safetube/parent/ParentApp.kt`:

```kotlin
package de.safetube.parent

import android.app.Application

class ParentApp : Application()
```

`android/parent/src/main/kotlin/de/safetube/parent/MainActivity.kt`:

```kotlin
package de.safetube.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import de.safetube.android.ui.SafeTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeTubeTheme {
                Surface { Text("SafeTube Eltern") }
            }
        }
    }
}
```

- [ ] **Step 4: Test und Debug-Build laufen lassen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `SmokeTest` grün, APK unter `parent/build/outputs/apk/debug/parent-debug.apk` auf dem Thinkbook. Meldet der Build doppelte Dateien beim Packen (`More than one file was found with OS independent path 'META-INF/…'`), den genannten Pfad in `packaging.resources.excludes` ergänzen. Auf dem Pi muss `./gradlew :core:test` weiterhin grün sein (die Android-Module sind dort nicht eingebunden).

- [ ] **Step 5: Commit**

```bash
git add android/core-android android/parent
git commit -m "Android: Module core-android und parent mit Theme und Robolectric-Smoke-Test"
```

---

### Task 11: Sitzung, Anmeldung und Test-Hilfen (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/session/SessionStore.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/session/Auth.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/AppContainer.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/ParentApp.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/login/LoginViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/login/LoginScreen.kt`
- Create (Test-Hilfen): `android/parent/src/test/kotlin/de/safetube/parent/testing/Samples.kt`, `FakeParentApi.kt`, `MainDispatcherRule.kt`, `TestStores.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/session/AuthTest.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/login/LoginViewModelTest.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/login/LoginScreenTest.kt`

Nach dem Login holt die App sofort `/parent/push` (eigenes ntfy-Topic) und speichert alles zusammen. Abmelden löscht die Sitzung, behält aber die Server-Adresse als Vorschlag.

- [ ] **Step 1: Test-Hilfen anlegen** (werden von allen folgenden ViewModel-Tests benutzt)

`android/parent/src/test/kotlin/de/safetube/parent/testing/Samples.kt`:

```kotlin
package de.safetube.parent.testing

import de.safetube.core.api.Child
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.DayStat
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.Effect
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.Parent
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Rule
import de.safetube.core.api.Stats
import de.safetube.core.api.TargetType
import java.time.Instant

/** Beispieldaten für Tests. Zeitpunkt „jetzt“ ist fest. */
object Samples {
    val NOW: Instant = Instant.parse("2026-11-15T10:00:00Z")

    fun parent(id: Int = 1, name: String = "Sandro") = Parent(id, name, "$name@example.org".lowercase(), true, true)

    fun child(id: Int = 1, name: String = "Mia", rewardUntil: Instant? = null, includeBlocked: Boolean = false) = Child(
        id = id,
        name = name,
        displayMode = DisplayMode.COMPACT,
        shortsEnabled = false,
        rewardActive = rewardUntil != null && rewardUntil.isAfter(NOW),
        rewardUntil = rewardUntil,
        rewardIncludesBlocked = includeBlocked,
    )

    fun request(
        id: Int,
        childId: Int = 1,
        type: TargetType = TargetType.VIDEO,
        status: RequestStatus = RequestStatus.OPEN,
        createdAt: Instant = NOW.minusSeconds(60L * id),
        sourceVideoId: String = "",
        liftsBlock: Boolean = false,
        childMessage: String = "",
    ) = ContentRequest(
        id = id,
        childId = childId,
        childName = if (childId == 1) "Mia" else "Antonio",
        targetType = type,
        targetId = if (type == TargetType.VIDEO) "vid$id-aaaaa".take(11) else "UCchannel$id".padEnd(24, 'x'),
        title = "Titel $id",
        channelId = "UCchannel$id".padEnd(24, 'x'),
        channelName = "Kanal $id",
        url = "https://www.youtube.com/watch?v=vid$id",
        thumbnailUrl = "",
        childMessage = childMessage,
        sourceVideoId = sourceVideoId,
        sourceVideoTitle = if (sourceVideoId.isEmpty()) "" else "Ursprungsvideo",
        status = status,
        decidedByName = null,
        decidedAt = null,
        reason = "",
        createdAt = createdAt,
        liftsBlock = liftsBlock,
    )

    fun rule(
        id: Int,
        childId: Int?,
        type: TargetType,
        targetId: String,
        effect: Effect,
        title: String = "",
        channelName: String = "",
        note: String = "",
    ) = Rule(id, childId, type, targetId, title, channelName, effect, note, NOW, NOW)

    fun device(id: Int, childId: Int = 1, unpaired: Boolean = false) =
        Device(id, childId, "0.1.0", NOW.minusSeconds(86400), NOW.minusSeconds(600), if (unpaired) NOW else null)

    fun history(id: Int, startedAt: Instant = NOW.minusSeconds(600L * id), allowed: Boolean = true) = HistoryEntry(
        id = id,
        videoId = "vid$id",
        channelId = "UCchan$id",
        title = "Video $id",
        channelName = "Kanal $id",
        startedAt = startedAt,
        playedSeconds = 120,
        inRewardMode = false,
        allowedNow = allowed,
        reasonNow = if (allowed) "channel_allow" else "default",
    )

    fun stats(today: Int = 0, reward: Int = 0) = Stats(
        days = listOf(DayStat("2026-11-15", today, reward)),
        topChannels = emptyList(),
        totalSeconds = today,
        rewardSeconds = reward,
    )
}
```

`android/parent/src/test/kotlin/de/safetube/parent/testing/FakeParentApi.kt`:

```kotlin
package de.safetube.parent.testing

import de.safetube.core.api.Child
import de.safetube.core.api.ChildUpdate
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Device
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.LoginResponse
import de.safetube.core.api.MeUpdate
import de.safetube.core.api.PairingCode
import de.safetube.core.api.Parent
import de.safetube.core.api.ParentApi
import de.safetube.core.api.ParentCreate
import de.safetube.core.api.PushInfo
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.RewardStart
import de.safetube.core.api.Rule
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.RuleUpdate
import de.safetube.core.api.Stats
import java.time.Instant

/** Server im Speicher. [calls] protokolliert jeden Aufruf; [failure] wird beim nächsten Aufruf geworfen. */
class FakeParentApi : ParentApi {
    val calls = mutableListOf<String>()
    var failure: Exception? = null

    var loginResult = LoginResponse("token-1", Samples.parent())
    var pushInfo = PushInfo("https://push.example.org", "yts-test")
    var me: Parent = Samples.parent()
    val parentsList = mutableListOf(Samples.parent())
    val childrenList = mutableListOf<Child>()
    val devicesByChild = mutableMapOf<Int, MutableList<Device>>()
    val rulesList = mutableListOf<Rule>()
    val requestsList = mutableListOf<ContentRequest>()
    val historyByChild = mutableMapOf<Int, List<HistoryEntry>>()
    val statsByChild = mutableMapOf<Int, Stats>()
    var pairing = PairingCode("AB12CD34", Samples.NOW.plusSeconds(600), """{"server": "https://x", "code": "AB12CD34"}""")

    var lastDecision: Pair<Int, Decision>? = null
    var lastRequestFilter: RequestFilter? = null
    var lastReward: RewardStart? = null
    var lastRuleCreate: RuleCreate? = null
    var lastMeUpdate: MeUpdate? = null

    private var nextId = 1000

    private fun record(call: String) {
        calls += call
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun login(email: String, password: String): LoginResponse {
        record("login $email")
        return loginResult
    }

    override suspend fun logout() = record("logout")

    override suspend fun me(): Parent {
        record("me")
        return me
    }

    override suspend fun updateMe(update: MeUpdate): Parent {
        record("updateMe")
        lastMeUpdate = update
        me = me.copy(
            name = update.name ?: me.name,
            mailNewRequest = update.mailNewRequest ?: me.mailNewRequest,
            mailAlerts = update.mailAlerts ?: me.mailAlerts,
        )
        return me
    }

    override suspend fun pushInfo(): PushInfo {
        record("pushInfo")
        return pushInfo
    }

    override suspend fun parents(): List<Parent> {
        record("parents")
        return parentsList.toList()
    }

    override suspend fun createParent(data: ParentCreate): Parent {
        record("createParent ${data.email}")
        val parent = Parent(nextId++, data.name, data.email, true, true)
        parentsList += parent
        return parent
    }

    override suspend fun children(): List<Child> {
        record("children")
        return childrenList.toList()
    }

    override suspend fun createChild(name: String): Child {
        record("createChild $name")
        val child = Samples.child(nextId++, name)
        childrenList += child
        return child
    }

    override suspend fun updateChild(childId: Int, update: ChildUpdate): Child {
        record("updateChild $childId")
        return changeChild(childId) {
            it.copy(
                name = update.name ?: it.name,
                displayMode = update.displayMode ?: it.displayMode,
                shortsEnabled = update.shortsEnabled ?: it.shortsEnabled,
            )
        }
    }

    override suspend fun deleteChild(childId: Int) {
        record("deleteChild $childId")
        childrenList.removeAll { it.id == childId }
        devicesByChild.remove(childId)
    }

    override suspend fun devices(childId: Int): List<Device> {
        record("devices $childId")
        return devicesByChild[childId].orEmpty().toList()
    }

    override suspend fun unpairDevice(deviceId: Int) {
        record("unpair $deviceId")
        devicesByChild.values.forEach { list ->
            list.replaceAll { if (it.id == deviceId) it.copy(unpairedAt = Samples.NOW) else it }
        }
    }

    override suspend fun pairingCode(childId: Int): PairingCode {
        record("pairingCode $childId")
        return pairing
    }

    override suspend fun rules(childId: Int?, globalOnly: Boolean): List<Rule> {
        record("rules $childId $globalOnly")
        return when {
            globalOnly -> rulesList.filter { it.childId == null }
            childId != null -> rulesList.filter { it.childId == childId || it.childId == null }
            else -> rulesList.toList()
        }
    }

    override suspend fun createRule(rule: RuleCreate): Rule {
        record("createRule ${rule.childId} ${rule.targetType} ${rule.targetId} ${rule.effect}")
        lastRuleCreate = rule
        rulesList.removeAll { it.childId == rule.childId && it.targetType == rule.targetType && it.targetId == rule.targetId }
        val created = Samples.rule(nextId++, rule.childId, rule.targetType, rule.targetId, rule.effect, rule.title, rule.channelName, rule.note)
        rulesList += created
        return created
    }

    override suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule {
        record("updateRule $ruleId")
        val index = rulesList.indexOfFirst { it.id == ruleId }
        val old = rulesList[index]
        val changed = old.copy(effect = update.effect ?: old.effect, note = update.note ?: old.note)
        rulesList[index] = changed
        return changed
    }

    override suspend fun deleteRule(ruleId: Int) {
        record("deleteRule $ruleId")
        rulesList.removeAll { it.id == ruleId }
    }

    override suspend fun requests(filter: RequestFilter): List<ContentRequest> {
        record("requests")
        lastRequestFilter = filter
        return requestsList
            .filter { filter.childId == null || it.childId == filter.childId }
            .filter { filter.status == null || it.status == filter.status }
            .filter { filter.since == null || !it.createdAt.isBefore(filter.since) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun request(requestId: Int): ContentRequest {
        record("request $requestId")
        return requestsList.first { it.id == requestId }
    }

    override suspend fun decide(requestId: Int, decision: Decision): ContentRequest {
        record("decide $requestId ${decision.action} ${decision.scope}")
        lastDecision = requestId to decision
        val index = requestsList.indexOfFirst { it.id == requestId }
        val approved = decision.action == DecisionAction.APPROVE || decision.action == DecisionAction.APPROVE_VIDEO_ONLY
        val changed = requestsList[index].copy(
            status = if (approved) RequestStatus.APPROVED else RequestStatus.REJECTED,
            reason = decision.reason,
            decidedByName = me.name,
            decidedAt = Samples.NOW,
        )
        requestsList[index] = changed
        return changed
    }

    override suspend fun startReward(childId: Int, reward: RewardStart): Child {
        record("startReward $childId")
        lastReward = reward
        val until = reward.until ?: Samples.NOW.plusSeconds(60L * (reward.minutes ?: 0))
        return changeChild(childId) {
            it.copy(rewardActive = true, rewardUntil = until, rewardIncludesBlocked = reward.includeBlocked)
        }
    }

    override suspend fun stopReward(childId: Int): Child {
        record("stopReward $childId")
        return changeChild(childId) { it.copy(rewardActive = false, rewardUntil = null, rewardIncludesBlocked = false) }
    }

    override suspend fun history(childId: Int, limit: Int, before: Instant?): List<HistoryEntry> {
        record("history $childId ${before ?: "-"}")
        return historyByChild[childId].orEmpty()
            .filter { before == null || it.startedAt.isBefore(before) }
            .take(limit)
    }

    override suspend fun stats(childId: Int, days: Int): Stats {
        record("stats $childId $days")
        return statsByChild[childId] ?: Samples.stats()
    }

    private fun changeChild(childId: Int, change: (Child) -> Child): Child {
        val index = childrenList.indexOfFirst { it.id == childId }
        val changed = change(childrenList[index])
        childrenList[index] = changed
        return changed
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/testing/MainDispatcherRule.kt`:

```kotlin
package de.safetube.parent.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope läuft auf Dispatchers.Main — in JVM-Tests durch einen Test-Dispatcher ersetzt. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`android/parent/src/test/kotlin/de/safetube/parent/testing/TestStores.kt`:

```kotlin
package de.safetube.parent.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.safetube.parent.session.SessionStore
import org.junit.rules.TemporaryFolder

/** SessionStore auf einer Datei im Test-Verzeichnis (eine pro Test, DataStore erlaubt nur eine Instanz pro Datei). */
fun TemporaryFolder.sessionStore(): SessionStore =
    SessionStore(PreferenceDataStoreFactory.create { newFile("session.preferences_pb") })
```

- [ ] **Step 2: Failing tests schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/session/AuthTest.kt`:

```kotlin
package de.safetube.parent.session

import de.safetube.core.api.ApiException
import de.safetube.core.api.ContentRequest
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.Samples
import de.safetube.parent.testing.sessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeParentApi()
    private val usedServers = mutableListOf<Pair<String, String?>>()

    private fun auth(store: SessionStore) = Auth(store) { server, token ->
        usedServers += server to token
        api
    }

    @Test
    fun `Login speichert Sitzung mit Push-Topic`() = runBlocking {
        val store = tmp.sessionStore()

        val session = auth(store).login(" safetube.example.org/ ", " sandro@example.org ", "geheim123")

        assertEquals("https://safetube.example.org", session.serverUrl)
        assertEquals("token-1", session.token)
        assertEquals("yts-test", session.ntfyTopic)
        assertEquals(listOf("login sandro@example.org", "pushInfo"), api.calls)
        assertEquals(listOf("https://safetube.example.org" to null, "https://safetube.example.org" to "token-1"), usedServers)
        assertEquals(session, store.load())
        assertEquals(session, store.current)
    }

    @Test
    fun `gescheiterter Login speichert nichts`() = runBlocking {
        val store = tmp.sessionStore()
        api.failure = ApiException(401, "Anmeldung fehlgeschlagen")

        val result = runCatching { auth(store).login("https://s.example.org", "x@example.org", "falsch") }

        assertTrue(result.isFailure)
        assertNull(store.load())
    }

    @Test
    fun `Abmelden löscht die Sitzung, behält aber den Server`() = runBlocking {
        val store = tmp.sessionStore()
        val auth = auth(store)
        auth.login("https://s.example.org", "x@example.org", "geheim123")
        store.setLastNtfyId("m5")

        auth.logout(api)

        assertNull(store.load())
        assertNull(store.lastNtfyId())
        assertEquals("https://s.example.org", auth.lastServer())
        assertTrue("logout" in api.calls)
    }

    @Test
    fun `Abmelden klappt auch, wenn der Server nicht antwortet`() = runBlocking {
        val store = tmp.sessionStore()
        val auth = auth(store)
        auth.login("https://s.example.org", "x@example.org", "geheim123")
        api.failure = java.io.IOException("weg")

        auth.logout(api)

        assertNull(store.load())
    }

    @Test
    fun `gemeldete Anfragen werden atomar gemerkt`() = runBlocking {
        val store = tmp.sessionStore()
        val open: List<ContentRequest> = listOf(Samples.request(1), Samples.request(2))

        assertTrue(store.markSeen(1))
        assertFalse(store.markSeen(1))
        assertEquals(listOf(2), store.newRequests(open).map { it.id })
        assertEquals(emptyList<ContentRequest>(), store.newRequests(open))
    }

    @Test
    fun `Server-Adresse normalisieren`() {
        assertEquals("https://safetube.isoletta.me", Auth.normalizeServer("safetube.isoletta.me"))
        assertEquals("https://safetube.isoletta.me", Auth.normalizeServer(" https://safetube.isoletta.me/ "))
        assertEquals("http://192.168.0.2:8090", Auth.normalizeServer("http://192.168.0.2:8090"))
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/login/LoginViewModelTest.kt`:

```kotlin
package de.safetube.parent.login

import de.safetube.core.api.ApiException
import de.safetube.parent.session.Auth
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.sessionStore
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeParentApi()

    private fun viewModel(): LoginViewModel {
        val auth = Auth(tmp.sessionStore()) { _, _ -> api }
        return LoginViewModel(auth, defaultServer = "https://safetube.isoletta.me").also {
            runBlocking { it.ready.join() }
        }
    }

    @Test
    fun `Standard-Server ist vorbelegt`() {
        assertEquals("https://safetube.isoletta.me", viewModel().state.value.server)
    }

    @Test
    fun `leere Felder werden nicht abgeschickt`() {
        val vm = viewModel()

        vm.submit()

        assertEquals("Bitte E-Mail und Passwort eingeben.", vm.state.value.error)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `erfolgreicher Login`() = runBlocking {
        val vm = viewModel()
        vm.onEmail("sandro@example.org")
        vm.onPassword("geheim123")

        vm.submit()
        vm.busyJob?.join()

        assertTrue(vm.state.value.loggedIn)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `falsches Passwort und Server weg`() = runBlocking {
        val vm = viewModel()
        vm.onEmail("sandro@example.org")
        vm.onPassword("falsch")

        api.failure = ApiException(401, "Anmeldung fehlgeschlagen")
        vm.submit()
        vm.busyJob?.join()
        assertEquals("E-Mail oder Passwort falsch.", vm.state.value.error)

        api.failure = IOException("weg")
        vm.submit()
        vm.busyJob?.join()
        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/login/LoginScreenTest.kt`:

```kotlin
package de.safetube.parent.login

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.safetube.android.ui.SafeTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Eingaben und Anmelden gehen an das ViewModel`() {
        val typed = mutableListOf<String>()
        var submitted = 0
        compose.setContent {
            SafeTubeTheme {
                LoginContent(
                    state = LoginState(server = "https://s"),
                    onServer = { typed += "server:$it" },
                    onEmail = { typed += "email:$it" },
                    onPassword = { typed += "password:$it" },
                    onSubmit = { submitted++ },
                )
            }
        }

        compose.onNodeWithTag("email").performTextInput("a@b.de")
        compose.onNodeWithTag("password").performTextInput("pw")
        compose.onNodeWithText("Anmelden").performClick()

        assertEquals(listOf("email:a@b.de", "password:pw"), typed)
        assertEquals(1, submitted)
    }

    @Test
    fun `Fehler wird angezeigt, Knopf ist beim Anmelden gesperrt`() {
        compose.setContent {
            SafeTubeTheme {
                LoginContent(
                    state = LoginState(busy = true, error = "E-Mail oder Passwort falsch."),
                    onServer = {}, onEmail = {}, onPassword = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("E-Mail oder Passwort falsch.").assertExists()
        compose.onNodeWithText("Anmelden …").assertIsNotEnabled()
    }
}
```

- [ ] **Step 3: Tests laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: SessionStore` / `Auth` / `LoginViewModel`.

- [ ] **Step 4: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/session/SessionStore.kt`:

```kotlin
package de.safetube.parent.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.safetube.core.api.ContentRequest
import de.safetube.core.push.OpenRequestCheck
import de.safetube.core.push.SeenIds
import kotlinx.coroutines.flow.first

data class Session(
    val serverUrl: String,
    val token: String,
    val parentId: Int,
    val parentName: String,
    val ntfyUrl: String,
    val ntfyTopic: String,
)

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Sitzung und Push-Zustand im privaten App-Speicher (Festlegung 6). */
class SessionStore(private val store: DataStore<Preferences>) {

    /** Zuletzt geladene bzw. gespeicherte Sitzung, für synchrone Zugriffe (Token im HTTP-Transport). */
    @Volatile
    var current: Session? = null
        private set

    suspend fun load(): Session? = read(store.data.first()).also { current = it }

    suspend fun save(session: Session) {
        store.edit {
            it[SERVER] = session.serverUrl
            it[TOKEN] = session.token
            it[PARENT_ID] = session.parentId
            it[PARENT_NAME] = session.parentName
            it[NTFY_URL] = session.ntfyUrl
            it[NTFY_TOPIC] = session.ntfyTopic
        }
        current = session
    }

    /** Abmelden: alles weg bis auf die Server-Adresse (Vorschlag für den nächsten Login). */
    suspend fun clear() {
        store.edit { prefs ->
            val server = prefs[SERVER]
            prefs.clear()
            if (server != null) prefs[SERVER] = server
        }
        current = null
    }

    suspend fun lastServer(): String? = store.data.first()[SERVER]

    suspend fun lastNtfyId(): String? = store.data.first()[LAST_NTFY_ID]

    suspend fun setLastNtfyId(id: String) {
        store.edit { it[LAST_NTFY_ID] = id }
    }

    /** Merkt sich eine per Push gemeldete Anfrage; true, wenn sie neu war. */
    suspend fun markSeen(requestId: Int): Boolean {
        var fresh = false
        store.edit { prefs ->
            val seen = SeenIds.decode(prefs[SEEN])
            fresh = seen.add(requestId)
            prefs[SEEN] = seen.encode()
        }
        return fresh
    }

    /** 15-Minuten-Abgleich: liefert die noch nicht gemeldeten offenen Anfragen und merkt sie sich. */
    suspend fun newRequests(open: List<ContentRequest>): List<ContentRequest> {
        var fresh = emptyList<ContentRequest>()
        store.edit { prefs ->
            val seen = SeenIds.decode(prefs[SEEN])
            fresh = OpenRequestCheck.newOnes(open, seen)
            prefs[SEEN] = seen.encode()
        }
        return fresh
    }

    private fun read(prefs: Preferences): Session? {
        val token = prefs[TOKEN] ?: return null
        val server = prefs[SERVER] ?: return null
        return Session(
            serverUrl = server,
            token = token,
            parentId = prefs[PARENT_ID] ?: 0,
            parentName = prefs[PARENT_NAME].orEmpty(),
            ntfyUrl = prefs[NTFY_URL].orEmpty(),
            ntfyTopic = prefs[NTFY_TOPIC].orEmpty(),
        )
    }

    private companion object {
        val SERVER = stringPreferencesKey("server")
        val TOKEN = stringPreferencesKey("token")
        val PARENT_ID = intPreferencesKey("parent_id")
        val PARENT_NAME = stringPreferencesKey("parent_name")
        val NTFY_URL = stringPreferencesKey("ntfy_url")
        val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        val LAST_NTFY_ID = stringPreferencesKey("last_ntfy_id")
        val SEEN = stringPreferencesKey("seen_requests")
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/session/Auth.kt`:

```kotlin
package de.safetube.parent.session

import de.safetube.core.api.ParentApi
import de.safetube.core.api.attempt

/** Anmelden und Abmelden. [apiFor] baut einen API-Client für eine Server-Adresse und ein Token. */
class Auth(
    private val store: SessionStore,
    private val apiFor: (serverUrl: String, token: String?) -> ParentApi,
) {
    suspend fun login(server: String, email: String, password: String): Session {
        val url = normalizeServer(server)
        val result = apiFor(url, null).login(email.trim(), password)
        val push = apiFor(url, result.token).pushInfo()
        val session = Session(
            serverUrl = url,
            token = result.token,
            parentId = result.parent.id,
            parentName = result.parent.name,
            ntfyUrl = push.ntfyUrl,
            ntfyTopic = push.ntfyTopic,
        )
        store.save(session)
        return session
    }

    /** Meldet beim Server ab (Fehler egal) und löscht die lokale Sitzung. */
    suspend fun logout(api: ParentApi) {
        attempt { api.logout() }
        store.clear()
    }

    suspend fun lastServer(): String? = store.lastServer()

    companion object {
        fun normalizeServer(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/AppContainer.kt`:

```kotlin
package de.safetube.parent

import android.content.Context
import de.safetube.core.api.HttpParentApi
import de.safetube.core.api.HttpTransport
import de.safetube.core.api.ParentApi
import de.safetube.core.extractor.NewPipeSource
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.YouTubeSource
import de.safetube.parent.session.Auth
import de.safetube.parent.session.SessionStore
import de.safetube.parent.session.sessionDataStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Alle langlebigen Objekte der App, von Hand verdrahtet (kein DI-Framework). */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val defaultServer: String = BuildConfig.DEFAULT_SERVER
    val sessionStore = SessionStore(context.sessionDataStore)

    /** API mit der aktuellen Sitzung (Adresse und Token werden bei jedem Aufruf neu gelesen). */
    val api: ParentApi = apiFor({ sessionStore.current?.serverUrl ?: defaultServer }, { sessionStore.current?.token })

    val auth = Auth(sessionStore) { server, token -> apiFor({ server }, { token }) }

    val youtube: YouTubeSource by lazy { NewPipeSource(OkHttpDownloader(http)) }

    private fun apiFor(server: () -> String, token: () -> String?): ParentApi =
        HttpParentApi(HttpTransport(http, server, token))
}
```

`android/parent/src/main/kotlin/de/safetube/parent/ParentApp.kt` (ersetzt die Fassung aus Task 10):

```kotlin
package de.safetube.parent

import android.app.Application
import android.content.Context

class ParentApp : Application() {
    /** Erst beim ersten Zugriff gebaut — Robolectric-Tests, die ihn nicht brauchen, bleiben schnell. */
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer
    get() = (applicationContext as ParentApp).container
```

`android/parent/src/main/kotlin/de/safetube/parent/login/LoginViewModel.kt`:

```kotlin
package de.safetube.parent.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ApiException
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.parent.session.Auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val server: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val auth: Auth, defaultServer: String) : ViewModel() {
    private val _state = MutableStateFlow(LoginState(server = defaultServer))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** Fertig, sobald die zuletzt benutzte Server-Adresse geladen ist (für Tests). */
    val ready: Job = viewModelScope.launch {
        auth.lastServer()?.let { last -> _state.update { it.copy(server = last) } }
    }

    /** Laufender Anmeldeversuch (für Tests). */
    var busyJob: Job? = null
        private set

    fun onServer(value: String) = _state.update { it.copy(server = value, error = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Bitte E-Mail und Passwort eingeben.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        busyJob = viewModelScope.launch {
            attempt { auth.login(current.server, current.email, current.password) }
                .onSuccess { _state.update { it.copy(busy = false, loggedIn = true, password = "") } }
                .onFailure { e ->
                    val message = if (e is ApiException && e.status == 401) "E-Mail oder Passwort falsch." else e.userMessage()
                    _state.update { it.copy(busy = false, error = message) }
                }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/login/LoginScreen.kt`:

```kotlin
package de.safetube.parent.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.parent.container

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { LoginViewModel(container.auth, container.defaultServer) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) onLoggedIn() }
    LoginContent(state, vm::onServer, vm::onEmail, vm::onPassword, vm::submit)
}

@Composable
fun LoginContent(
    state: LoginState,
    onServer: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SafeTube Eltern", style = MaterialTheme.typography.headlineMedium)
            Text("Mit deinem Eltern-Konto anmelden.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text("E-Mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("email"),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().testTag("password"),
            )
            OutlinedTextField(
                value = state.server,
                onValueChange = onServer,
                label = { Text("Server") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("server"),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSubmit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Anmelden …" else "Anmelden")
            }
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen, grün sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest`
Expected: PASS (`SmokeTest`, `AuthTest` 6, `LoginViewModelTest` 4, `LoginScreenTest` 2).

- [ ] **Step 6: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Sitzung, Anmeldung und Test-Hilfen"
```

---

### Task 12: Navigation, gemeinsame Bausteine und Übersicht (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/nav/Routes.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/ui/Common.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/overview/OverviewViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/overview/OverviewScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/MainActivity.kt` (ganz ersetzen)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/overview/OverviewViewModelTest.kt`

**Muster für alle Listen-Bildschirme:** Das ViewModel lädt nicht im `init`, sondern der Bildschirm ruft `refresh()`/`load()` per `LifecycleResumeEffect` bei jedem Erscheinen auf — so sind die Daten nach dem Zurückkehren von einem Unterbildschirm aktuell. Tests rufen `refresh()`/`load()` selbst auf.

Übersicht (Spec 7): eine Karte pro Kind mit Schauzeit heute, offenen Anfragen und Belohnungsmodus; von dort geht es zu Verlauf, Statistik, Belohnung und Kind-Einstellungen. Unten eine Leiste mit den vier Hauptbereichen Übersicht, Anfragen, Regeln, Eltern. Die Routen der späteren Tasks werden jeweils dort in `ParentNavHost` eingetragen (Markierung `// Routen`).

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/overview/OverviewViewModelTest.kt`:

```kotlin
package de.safetube.parent.overview

import de.safetube.core.api.RequestStatus
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        requestsList += Samples.request(1, childId = 1)
        requestsList += Samples.request(2, childId = 1)
        requestsList += Samples.request(3, childId = 1, status = RequestStatus.APPROVED)
        statsByChild[1] = Samples.stats(today = 3900)
    }

    @Test
    fun `Karten mit Schauzeit heute und offenen Anfragen`() {
        val vm = OverviewViewModel(api).apply { refresh() }

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(listOf("Mia", "Antonio"), state.cards.map { it.child.name })
        assertEquals(3900L, state.cards[0].todaySeconds)
        assertEquals(2, state.cards[0].openRequests)
        assertEquals(0, state.cards[1].openRequests)
        assertEquals(RequestStatus.OPEN, api.lastRequestFilter?.status)
        assertTrue("stats 1 1" in api.calls)
    }

    @Test
    fun `Kind anlegen lädt neu`() {
        val vm = OverviewViewModel(api).apply { refresh() }

        vm.createChild("  Lena ")

        assertTrue("createChild Lena" in api.calls)
        assertEquals(3, vm.state.value.cards.size)
    }

    @Test
    fun `leerer Name wird ignoriert`() {
        val vm = OverviewViewModel(api).apply { refresh() }
        api.calls.clear()

        vm.createChild("   ")

        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `Fehler wird gemeldet`() {
        api.failure = IOException("weg")

        val vm = OverviewViewModel(api).apply { refresh() }

        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.overview.*'`
Expected: FAIL — `Unresolved reference: OverviewViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/nav/Routes.kt`:

```kotlin
package de.safetube.parent.nav

object Routes {
    const val LOGIN = "login"
    const val OVERVIEW = "overview"
    const val REQUESTS = "requests"
    const val REQUEST = "request/{requestId}"
    const val RULES = "rules"
    const val RULE_ADD = "rules/add"
    const val HISTORY = "history/{childId}"
    const val STATS = "stats/{childId}"
    const val REWARD = "reward/{childId}"
    const val CHILD = "child/{childId}"
    const val PARENTS = "parents"

    /** Bereiche mit unterer Leiste. */
    val TOP_LEVEL = setOf(OVERVIEW, REQUESTS, RULES, PARENTS)

    fun request(requestId: Int) = "request/$requestId"
    fun history(childId: Int) = "history/$childId"
    fun stats(childId: Int) = "stats/$childId"
    fun reward(childId: Int) = "reward/$childId"
    fun child(childId: Int) = "child/$childId"
}
```

`android/parent/src/main/kotlin/de/safetube/parent/ui/Common.kt`:

```kotlin
package de.safetube.parent.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.safetube.core.api.Child

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorMessage(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Nochmal versuchen") }
    }
}

/** Auswahl „alle“ oder ein Kind, als waagerechte Chip-Reihe. */
@Composable
fun ChildFilter(children: List<Child>, selected: Int?, allLabel: String, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        children.forEach { child ->
            FilterChip(selected = selected == child.id, onClick = { onSelect(child.id) }, label = { Text(child.name) })
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Dialog mit einem Textfeld; [allowEmpty] = auch leere Eingabe bestätigen (z. B. optionaler Grund). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    allowEmpty: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = allowEmpty || text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
```

`android/parent/src/main/kotlin/de/safetube/parent/overview/OverviewViewModel.kt`:

```kotlin
package de.safetube.parent.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildCard(val child: Child, val todaySeconds: Long, val openRequests: Int)

data class OverviewState(
    val loading: Boolean = true,
    val cards: List<ChildCard> = emptyList(),
    val error: String? = null,
)

class OverviewViewModel(private val api: ParentApi) : ViewModel() {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    /** Lädt alles neu; der Bildschirm ruft das bei jedem Erscheinen auf (LifecycleResumeEffect). */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = api.children()
                val open = api.requests(RequestFilter(status = RequestStatus.OPEN))
                children.map { child ->
                    ChildCard(
                        child = child,
                        todaySeconds = api.stats(child.id, days = 1).days.lastOrNull()?.seconds?.toLong() ?: 0L,
                        openRequests = open.count { it.childId == child.id },
                    )
                }
            }
                .onSuccess { cards -> _state.value = OverviewState(loading = false, cards = cards) }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun createChild(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            attempt { api.createChild(trimmed) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/overview/OverviewScreen.kt`:

```kotlin
package de.safetube.parent.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import de.safetube.parent.ui.TextInputDialog
import java.time.Instant

class OverviewActions(
    val onRequests: () -> Unit,
    val onHistory: (Int) -> Unit,
    val onStats: (Int) -> Unit,
    val onReward: (Int) -> Unit,
    val onChild: (Int) -> Unit,
)

@Composable
fun OverviewScreen(actions: OverviewActions, header: @Composable () -> Unit = {}) {
    val container = LocalContext.current.container
    val vm = viewModel { OverviewViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    OverviewContent(state, Instant.now(), actions, header, vm::refresh, vm::createChild)
}

@Composable
fun OverviewContent(
    state: OverviewState,
    now: Instant,
    actions: OverviewActions,
    header: @Composable () -> Unit,
    onRefresh: () -> Unit,
    onCreateChild: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = "Übersicht",
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Neu laden") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Kind anlegen") },
            )
        },
    ) { padding ->
        when {
            state.loading && state.cards.isEmpty() -> Loading(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { header() }
                state.error?.let { message -> item { ErrorMessage(message, onRefresh) } }
                if (state.cards.isEmpty() && state.error == null) {
                    item { Text("Noch keine Kinder angelegt. Unten rechts geht's los.") }
                }
                items(state.cards, key = { it.child.id }) { card -> ChildCardView(card, now, actions) }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Kind anlegen",
            label = "Name",
            confirm = "Anlegen",
            onConfirm = onCreateChild,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ChildCardView(card: ChildCard, now: Instant, actions: OverviewActions) {
    val child = card.child
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(child.name, style = MaterialTheme.typography.titleLarge)
            Text("Heute geschaut: ${Formats.duration(card.todaySeconds)}")
            if (card.openRequests > 0) {
                TextButton(onClick = actions.onRequests, contentPadding = PaddingValues(0.dp)) {
                    Text("Offene Anfragen: ${card.openRequests}")
                }
            } else {
                Text("Keine offenen Anfragen")
            }
            val until = child.rewardUntil
            if (child.rewardActive && until != null) {
                val extra = if (child.rewardIncludesBlocked) " (auch Gesperrtes)" else ""
                Text("Belohnungsmodus: ${Formats.remaining(until, now)}$extra", color = MaterialTheme.colorScheme.primary)
            }
            Row {
                TextButton(onClick = { actions.onHistory(child.id) }) { Text("Verlauf") }
                TextButton(onClick = { actions.onStats(child.id) }) { Text("Statistik") }
            }
            Row {
                TextButton(onClick = { actions.onReward(child.id) }) { Text("Belohnung") }
                TextButton(onClick = { actions.onChild(child.id) }) { Text("Einstellungen") }
            }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt`:

```kotlin
package de.safetube.parent.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.safetube.parent.login.LoginScreen
import de.safetube.parent.overview.OverviewActions
import de.safetube.parent.overview.OverviewScreen

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVEL_ITEMS = listOf(
    TopLevel(Routes.OVERVIEW, "Übersicht", Icons.Filled.Home),
    TopLevel(Routes.REQUESTS, "Anfragen", Icons.Filled.Notifications),
    TopLevel(Routes.RULES, "Regeln", Icons.Filled.Lock),
    TopLevel(Routes.PARENTS, "Eltern", Icons.Filled.Person),
)

/** Argument „childId“ bzw. „requestId“ als Int. */
internal val childIdArg = listOf(navArgument("childId") { type = NavType.IntType })
internal val requestIdArg = listOf(navArgument("requestId") { type = NavType.IntType })

@Composable
fun ParentNavHost(
    loggedIn: Boolean,
    openRequestId: Int?,
    onRequestOpened: () -> Unit,
    onLoggedIn: () -> Unit,
    onLoggedOut: () -> Unit,
    overviewHeader: @Composable () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(openRequestId, loggedIn) {
        if (loggedIn && openRequestId != null) {
            nav.navigate(Routes.request(openRequestId))
            onRequestOpened()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (route in Routes.TOP_LEVEL) BottomBar(nav, route) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (loggedIn) Routes.OVERVIEW else Routes.LOGIN,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = {
                    onLoggedIn()
                    nav.navigate(Routes.OVERVIEW) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            }
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    actions = OverviewActions(
                        onRequests = { nav.navigateTopLevel(Routes.REQUESTS) },
                        onHistory = { nav.navigate(Routes.history(it)) },
                        onStats = { nav.navigate(Routes.stats(it)) },
                        onReward = { nav.navigate(Routes.reward(it)) },
                        onChild = { nav.navigate(Routes.child(it)) },
                    ),
                    header = overviewHeader,
                )
            }
            // Routen
        }
    }
}

/** Nach dem Abmelden: alles vom Stapel, zurück zum Login. */
internal fun NavHostController.toLogin() {
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}

internal fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.OVERVIEW) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar {
        TOP_LEVEL_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = { nav.navigateTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}
```

Hinweis: `onLoggedOut` wird ab Task 21 (Eltern-Bildschirm, Abmelden) benutzt; bis dahin ist der Parameter vorhanden, aber ungenutzt (Kotlin-Warnung, kein Fehler).

`android/parent/src/main/kotlin/de/safetube/parent/MainActivity.kt` (ersetzt die Fassung aus Task 10):

```kotlin
package de.safetube.parent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.safetube.android.ui.SafeTubeTheme
import de.safetube.parent.nav.ParentNavHost
import de.safetube.parent.ui.Loading

class MainActivity : ComponentActivity() {

    /** Anfrage, die über eine Benachrichtigung geöffnet werden soll. */
    private val openRequest = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequest.value = requestIdFrom(intent)
        val appContainer = container
        setContent {
            SafeTubeTheme {
                var loggedIn by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { loggedIn = appContainer.sessionStore.load() != null }
                when (val state = loggedIn) {
                    null -> Loading()
                    else -> ParentNavHost(
                        loggedIn = state,
                        openRequestId = openRequest.value,
                        onRequestOpened = { openRequest.value = null },
                        onLoggedIn = { loggedIn = true },
                        onLoggedOut = { loggedIn = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = requestIdFrom(intent)
    }

    private fun requestIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_REQUEST_ID, -1)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }
}
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `OverviewViewModelTest` (4) grün, alle bisherigen Tests grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Navigation, gemeinsame Bausteine und Übersicht"
```

---

### Task 13: Anfragen-Liste (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/requests/RequestsViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/requests/RequestsScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `REQUESTS`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/requests/RequestsViewModelTest.kt`

Spec 7: offene Anfragen oben, darunter die History; Filter Kind / Status / Zeitraum. **Offene Anfragen werden unabhängig vom Zeitraum immer gezeigt** — sonst verschwände eine alte, noch unbeantwortete Anfrage aus dem Blick.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/requests/RequestsViewModelTest.kt`:

```kotlin
package de.safetube.parent.requests

import de.safetube.core.api.RequestStatus
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class RequestsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        // offen, aber 40 Tage alt
        requestsList += Samples.request(1, createdAt = Samples.NOW.minusSeconds(40L * 86400))
        requestsList += Samples.request(2, childId = 2)
        requestsList += Samples.request(3, status = RequestStatus.APPROVED)
        requestsList += Samples.request(4, status = RequestStatus.REJECTED, createdAt = Samples.NOW.minusSeconds(10L * 86400))
    }

    private fun viewModel() = RequestsViewModel(api, clock = { Samples.NOW }).apply { load() }

    @Test
    fun `offene oben unabhängig vom Zeitraum, darunter die letzten 30 Tage`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(listOf("Mia", "Antonio"), state.children.map { it.name })
        assertEquals(setOf(1, 2), state.open.map { it.id }.toSet())
        assertEquals(listOf(3, 4), state.history.map { it.id })
    }

    @Test
    fun `Zeitraum 7 Tage blendet ältere Entscheidungen aus`() {
        val vm = viewModel()

        vm.setPeriod(Period.WEEK)

        assertEquals(listOf(3), vm.state.value.history.map { it.id })
        assertEquals(2, vm.state.value.open.size)
    }

    @Test
    fun `Filter Kind`() {
        val vm = viewModel()

        vm.setChild(2)

        assertEquals(listOf(2), vm.state.value.open.map { it.id })
        assertEquals(emptyList<Int>(), vm.state.value.history.map { it.id })
        assertEquals(2, api.lastRequestFilter?.childId)
    }

    @Test
    fun `Filter Status`() {
        val vm = viewModel()

        vm.setStatus(StatusFilter.OPEN)
        assertEquals(2, vm.state.value.open.size)
        assertEquals(0, vm.state.value.history.size)

        vm.setStatus(StatusFilter.REJECTED)
        assertEquals(0, vm.state.value.open.size)
        assertEquals(listOf(4), vm.state.value.history.map { it.id })
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.requests.RequestsViewModelTest'`
Expected: FAIL — `Unresolved reference: RequestsViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/requests/RequestsViewModel.kt`:

```kotlin
package de.safetube.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Period(val label: String, val days: Long?) {
    WEEK("7 Tage", 7),
    MONTH("30 Tage", 30),
    ALL("Alle", null),
}

enum class StatusFilter(val label: String, val status: RequestStatus?) {
    ALL("Alle", null),
    OPEN("Offen", RequestStatus.OPEN),
    APPROVED("Freigegeben", RequestStatus.APPROVED),
    REJECTED("Abgelehnt", RequestStatus.REJECTED),
}

data class RequestsState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val childId: Int? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val period: Period = Period.MONTH,
    val open: List<ContentRequest> = emptyList(),
    val history: List<ContentRequest> = emptyList(),
    val error: String? = null,
)

class RequestsViewModel(
    private val api: ParentApi,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state.asStateFlow()

    fun setChild(childId: Int?) {
        _state.update { it.copy(childId = childId) }
        load()
    }

    fun setStatus(status: StatusFilter) {
        _state.update { it.copy(status = status) }
        load()
    }

    fun setPeriod(period: Period) {
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = current.children.ifEmpty { api.children() }
                val since = current.period.days?.let { clock().minus(Duration.ofDays(it)) }
                val showOpen = current.status == StatusFilter.ALL || current.status == StatusFilter.OPEN
                val open = if (showOpen) {
                    api.requests(RequestFilter(childId = current.childId, status = RequestStatus.OPEN))
                } else {
                    emptyList()
                }
                val history = if (current.status == StatusFilter.OPEN) {
                    emptyList()
                } else {
                    api.requests(RequestFilter(childId = current.childId, status = current.status.status, since = since))
                        .filter { it.status != RequestStatus.OPEN }
                }
                Triple(children, open, history)
            }
                .onSuccess { (children, open, history) ->
                    _state.update { it.copy(loading = false, children = children, open = open, history = history) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/requests/RequestsScreen.kt`:

```kotlin
package de.safetube.parent.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ChildFilter
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun RequestsScreen(onOpen: (Int) -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { RequestsViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    RequestsContent(state, onOpen, vm::setChild, vm::setStatus, vm::setPeriod, vm::load)
}

@Composable
fun RequestsContent(
    state: RequestsState,
    onOpen: (Int) -> Unit,
    onChild: (Int?) -> Unit,
    onStatus: (StatusFilter) -> Unit,
    onPeriod: (Period) -> Unit,
    onRefresh: () -> Unit,
) {
    ScreenScaffold(
        title = "Anfragen",
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Neu laden") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChildFilter(state.children, state.childId, "Alle Kinder", onChild)
            ChipRow(StatusFilter.entries, state.status, { it.label }, onStatus)
            ChipRow(Period.entries, state.period, { it.label }, onPeriod)
            when {
                state.loading && state.open.isEmpty() && state.history.isEmpty() -> Loading()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { message -> item { ErrorMessage(message, onRefresh) } }
                    if (state.status == StatusFilter.ALL || state.status == StatusFilter.OPEN) {
                        item { SectionTitle("Offen (${state.open.size})") }
                        if (state.open.isEmpty()) item { Text("Keine offenen Anfragen.", Modifier.padding(16.dp)) }
                        items(state.open, key = { "o${it.id}" }) { RequestRow(it, onOpen) }
                    }
                    if (state.status != StatusFilter.OPEN) {
                        item { SectionTitle("Entschieden") }
                        if (state.history.isEmpty()) item { Text("Nichts im gewählten Zeitraum.", Modifier.padding(16.dp)) }
                        items(state.history, key = { "h${it.id}" }) { RequestRow(it, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun RequestRow(request: ContentRequest, onOpen: (Int) -> Unit) {
    val kind = (if (request.targetType == TargetType.VIDEO) "Video" else "Kanal") +
        (if (request.liftsBlock) " (gesperrt)" else "")
    val status = when (request.status) {
        RequestStatus.OPEN -> "offen"
        RequestStatus.APPROVED -> "freigegeben" + (request.decidedByName?.let { " von $it" } ?: "")
        RequestStatus.REJECTED -> "abgelehnt" + (request.decidedByName?.let { " von $it" } ?: "")
    }
    ListItem(
        modifier = Modifier.clickable { onOpen(request.id) },
        leadingContent = if (request.thumbnailUrl.isNotBlank()) {
            {
                AsyncImage(
                    model = request.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(54.dp),
                )
            }
        } else {
            null
        },
        headlineContent = { Text(request.title.ifEmpty { request.targetId }) },
        supportingContent = { Text("$kind · ${request.childName} · ${Formats.dateTime(request.createdAt)} · $status") },
    )
    HorizontalDivider()
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.requests.RequestsScreen`):

```kotlin
            composable(Routes.REQUESTS) {
                RequestsScreen(onOpen = { nav.navigate(Routes.request(it)) })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `RequestsViewModelTest` (4) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Anfragen-Liste mit Filtern"
```

---

### Task 14: Vorschau-Player und Anfrage-Detail mit Entscheidungen

**Files:**
- Create: `android/core-android/src/main/kotlin/de/safetube/android/player/PreviewPlayer.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/requests/RequestDetailViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/requests/RequestDetailScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `REQUEST`)
- Create (Test-Hilfe): `android/parent/src/test/kotlin/de/safetube/parent/testing/FakeYouTubeSource.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/requests/RequestDetailViewModelTest.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/requests/RequestDetailScreenTest.kt`

Spec 7, Anfrage-Detail: Vorschaubild, Titel, Kanal, Nachricht des Kindes, Link; bei Kanal-Anfragen die letzten 10 Videos; eingebauter Vorschau-Player (ohne Filter) und [In YouTube öffnen]. Aktionen siehe Festlegung 10. Eine bereits entschiedene Anfrage lässt sich neu entscheiden (der Server legt dann die Regel neu an bzw. nimmt sie zurück).

- [ ] **Step 1: Test-Hilfe und failing tests schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/testing/FakeYouTubeSource.kt`:

```kotlin
package de.safetube.parent.testing

import de.safetube.core.extractor.ChannelPage
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.StreamCandidate
import de.safetube.core.extractor.StreamDetails
import de.safetube.core.extractor.StreamKind
import de.safetube.core.extractor.VideoItem
import de.safetube.core.extractor.YouTubeSource

class FakeYouTubeSource : YouTubeSource {
    val calls = mutableListOf<String>()
    var failure: Exception? = null
    val searchResults = mutableMapOf<String, List<SearchHit>>()
    /** Schlüssel: Kanal-ID oder URL, so wie sie übergeben wird. */
    val channels = mutableMapOf<String, ChannelPage>()
    val streams = mutableMapOf<String, StreamDetails>()

    private fun record(call: String) {
        calls += call
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun search(query: String, filter: SearchFilter): List<SearchHit> {
        record("search $query $filter")
        return searchResults[query].orEmpty()
    }

    override suspend fun channel(channelIdOrUrl: String, maxVideos: Int): ChannelPage {
        record("channel $channelIdOrUrl $maxVideos")
        val page = channels.getValue(channelIdOrUrl)
        return page.copy(videos = page.videos.take(maxVideos))
    }

    override suspend fun stream(videoId: String): StreamDetails {
        record("stream $videoId")
        return streams.getValue(videoId)
    }

    companion object {
        fun video(id: String, channelId: String? = "UCchan", title: String = "Video $id") =
            VideoItem(id, title, channelId, "Kanal", null, 60, false, null)

        fun stream(id: String, channelId: String = "UCchan", title: String = "Video $id") = StreamDetails(
            videoId = id,
            title = title,
            channelId = channelId,
            channelName = "Kanal",
            thumbnailUrl = null,
            durationSeconds = 60,
            candidates = listOf(StreamCandidate("https://v/$id", StreamKind.MUXED, 360, 0, "video/mp4")),
        )

        fun channel(id: String, name: String = "Kanal $id", videos: Int = 12) =
            ChannelPage(id, name, null, (1..videos).map { video("v$it", id) })
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/requests/RequestDetailViewModelTest.kt`:

```kotlin
package de.safetube.parent.requests

import de.safetube.core.api.ApiException
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Effect
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.api.YouTubeException
import de.safetube.core.extractor.PlaybackSource
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.FakeYouTubeSource
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RequestDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val videoRequest = Samples.request(1)
    private val channelRequest = Samples.request(2, type = TargetType.CHANNEL, sourceVideoId = "srcvideo123")
    private val api = FakeParentApi().apply {
        requestsList += videoRequest
        requestsList += channelRequest
    }
    private val youtube = FakeYouTubeSource().apply {
        channels[channelRequest.targetId] = FakeYouTubeSource.channel(channelRequest.targetId)
        streams[videoRequest.targetId] = FakeYouTubeSource.stream(videoRequest.targetId)
    }
    private val decided = mutableListOf<Int>()

    private fun viewModel(requestId: Int) = RequestDetailViewModel(api, youtube, requestId) { decided += it }

    @Test
    fun `Video-Anfrage lädt keine Kanalvideos`() {
        val vm = viewModel(1)

        assertEquals(videoRequest, vm.state.value.request)
        assertEquals(emptyList<String>(), youtube.calls)
    }

    @Test
    fun `Kanal-Anfrage zeigt die letzten 10 Videos`() {
        val vm = viewModel(2)

        assertEquals(10, vm.state.value.channelVideos.size)
        assertEquals(listOf("channel ${channelRequest.targetId} 10"), youtube.calls)
    }

    @Test
    fun `Freigeben für alle Kinder`() {
        val vm = viewModel(1)

        vm.setScope(Scope.ALL)
        vm.approve()

        val (id, decision) = api.lastDecision!!
        assertEquals(1, id)
        assertEquals(DecisionAction.APPROVE, decision.action)
        assertEquals(Scope.ALL, decision.scope)
        assertEquals(RequestStatus.APPROVED, vm.state.value.request!!.status)
        assertEquals("Freigegeben", vm.state.value.message)
        assertEquals(listOf(1), decided)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `nur das Ursprungsvideo einer Kanal-Anfrage freigeben`() {
        val vm = viewModel(2)

        vm.approveVideoOnly()

        assertEquals(DecisionAction.APPROVE_VIDEO_ONLY, api.lastDecision!!.second.action)
    }

    @Test
    fun `Video-Anfrage ablehnen und Kanal sperren`() {
        val vm = viewModel(1)

        vm.rejectAndBlockChannel("  zu gruselig ")

        val decision = api.lastDecision!!.second
        assertEquals(DecisionAction.REJECT, decision.action)
        assertEquals("zu gruselig", decision.reason)
        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.CHANNEL, rule.targetType)
        assertEquals(videoRequest.channelId, rule.targetId)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Abgelehnt, Kanal gesperrt", vm.state.value.message)
    }

    @Test
    fun `Kanal sperren für alle Kinder legt eine globale Regel an`() {
        val vm = viewModel(1)

        vm.setScope(Scope.ALL)
        vm.rejectAndBlockChannel("")

        assertNull(api.lastRuleCreate!!.childId)
    }

    @Test
    fun `Kanal-Anfrage ablehnen und sperren macht der Server`() {
        val vm = viewModel(2)

        vm.rejectAndBlockChannel("")

        assertEquals(DecisionAction.REJECT_AND_BLOCK, api.lastDecision!!.second.action)
        assertNull(api.lastRuleCreate)
    }

    @Test
    fun `Video-Anfrage ablehnen und Video sperren`() {
        val vm = viewModel(1)

        vm.rejectAndBlockTarget("")

        assertEquals(DecisionAction.REJECT_AND_BLOCK, api.lastDecision!!.second.action)
        assertEquals("Abgelehnt, Video gesperrt", vm.state.value.message)
    }

    @Test
    fun `Fehler beim Entscheiden wird gemeldet`() {
        val vm = viewModel(1)
        api.failure = ApiException(500, "kaputt")

        vm.approve()

        assertEquals("kaputt", vm.state.value.message)
        assertFalse(vm.state.value.busy)
        assertTrue(decided.isEmpty())
    }

    @Test
    fun `Vorschau wählt einen Stream, Fehler werden angezeigt`() {
        val vm = viewModel(1)

        vm.preview(videoRequest.targetId, "Titel")
        assertEquals(PlaybackSource.Progressive("https://v/${videoRequest.targetId}"), vm.state.value.preview!!.source)

        youtube.failure = YouTubeException("YouTube ist gerade nicht erreichbar.")
        vm.preview(videoRequest.targetId, "Titel")
        assertEquals("YouTube ist gerade nicht erreichbar.", vm.state.value.preview!!.error)

        vm.closePreview()
        assertNull(vm.state.value.preview)
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/requests/RequestDetailScreenTest.kt`:

```kotlin
package de.safetube.parent.requests

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.safetube.android.ui.SafeTubeTheme
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private val actions = RequestDetailActions(
        onBack = {},
        onScope = { clicks += "scope $it" },
        onApprove = { clicks += "approve" },
        onApproveVideoOnly = { clicks += "videoOnly" },
        onReject = { clicks += "reject $it" },
        onRejectBlockTarget = { clicks += "blockTarget $it" },
        onRejectBlockChannel = { clicks += "blockChannel $it" },
        onPreview = { id, _ -> clicks += "preview $id" },
        onClosePreview = {},
        onOpenYouTube = { clicks += "open $it" },
        onMessageShown = {},
    )

    @Test
    fun `Video-Anfrage bietet Video- und Kanalsperre`() {
        val request = Samples.request(1)
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Freigeben").performScrollTo().performClick()
        compose.onNodeWithText("Ablehnen + Video sperren").performScrollTo().assertExists()
        compose.onNodeWithText("Ablehnen + Kanal sperren").performScrollTo().assertExists()
        compose.onNodeWithText("Vorschau abspielen").performScrollTo().performClick()

        assertEquals(listOf("approve", "preview ${request.targetId}"), clicks)
    }

    @Test
    fun `Kanal-Anfrage mit Ursprungsvideo bietet nur dieses Video`() {
        val request = Samples.request(2, type = TargetType.CHANNEL, sourceVideoId = "srcvideo123")
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Nur das Video „Ursprungsvideo“ freigeben").performScrollTo().performClick()
        compose.onNodeWithText("Ablehnen + Video sperren").assertDoesNotExist()

        assertEquals(listOf("videoOnly"), clicks)
    }

    @Test
    fun `Aufhebung einer Sperre ist erkennbar und zeigt die Begründung`() {
        val request = Samples.request(1, liftsBlock = true, childMessage = "Brauche ich für Bio")
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Bittet um Aufhebung einer Sperre").assertExists()
        compose.onNodeWithText("„Brauche ich für Bio“").assertExists()
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.requests.RequestDetail*'`
Expected: FAIL — `Unresolved reference: RequestDetailViewModel` / `RequestDetailContent`.

- [ ] **Step 3: Implementieren**

`android/core-android/src/main/kotlin/de/safetube/android/player/PreviewPlayer.kt`:

```kotlin
package de.safetube.android.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.PlaybackSource

/** Einfacher Player für die Elternvorschau: spielt sofort los, gibt beim Verlassen alles frei. */
@Composable
fun PreviewPlayer(source: PlaybackSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(MediaSources.create(source))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}

@OptIn(UnstableApi::class)
object MediaSources {
    fun create(source: PlaybackSource): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val progressive = ProgressiveMediaSource.Factory(http)
        return when (source) {
            is PlaybackSource.Progressive -> progressive.createMediaSource(MediaItem.fromUri(source.url))
            is PlaybackSource.Merged -> MergingMediaSource(
                progressive.createMediaSource(MediaItem.fromUri(source.videoUrl)),
                progressive.createMediaSource(MediaItem.fromUri(source.audioUrl)),
            )
            is PlaybackSource.Hls -> HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(source.url))
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/requests/RequestDetailViewModel.kt`:

```kotlin
package de.safetube.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.extractor.PlaybackSource
import de.safetube.core.extractor.StreamChoice
import de.safetube.core.extractor.VideoItem
import de.safetube.core.extractor.YouTubeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Preview(
    val videoId: String,
    val title: String,
    val source: PlaybackSource? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

data class RequestDetailState(
    val loading: Boolean = true,
    val request: ContentRequest? = null,
    val error: String? = null,
    val scope: Scope = Scope.CHILD,
    val busy: Boolean = false,
    /** Kurze Rückmeldung (Snackbar), danach messageShown() aufrufen. */
    val message: String? = null,
    val channelVideos: List<VideoItem> = emptyList(),
    val channelVideosError: String? = null,
    val preview: Preview? = null,
)

class RequestDetailViewModel(
    private val api: ParentApi,
    private val youtube: YouTubeSource,
    private val requestId: Int,
    private val onDecided: (Int) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(RequestDetailState())
    val state: StateFlow<RequestDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.request(requestId) }
                .onSuccess { request ->
                    _state.update { it.copy(loading = false, request = request) }
                    if (request.targetType == TargetType.CHANNEL) loadChannelVideos(request.targetId)
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    private fun loadChannelVideos(channelId: String) {
        viewModelScope.launch {
            attempt { youtube.channel(channelId, maxVideos = 10).videos }
                .onSuccess { videos -> _state.update { it.copy(channelVideos = videos, channelVideosError = null) } }
                .onFailure { e -> _state.update { it.copy(channelVideosError = e.userMessage()) } }
        }
    }

    fun setScope(scope: Scope) = _state.update { it.copy(scope = scope) }

    fun approve() = decide(DecisionAction.APPROVE, "", "Freigegeben")

    fun approveVideoOnly() = decide(DecisionAction.APPROVE_VIDEO_ONLY, "", "Nur das Video freigegeben")

    fun reject(reason: String) = decide(DecisionAction.REJECT, reason, "Abgelehnt")

    /** Lehnt ab und sperrt genau das angefragte Ziel (Video bzw. Kanal). */
    fun rejectAndBlockTarget(reason: String) {
        val request = _state.value.request ?: return
        val what = if (request.targetType == TargetType.CHANNEL) "Kanal" else "Video"
        decide(DecisionAction.REJECT_AND_BLOCK, reason, "Abgelehnt, $what gesperrt")
    }

    /** Lehnt ab und sperrt den Kanal — bei Video-Anfragen über eine zusätzliche Regel (Festlegung 10). */
    fun rejectAndBlockChannel(reason: String) {
        val request = _state.value.request ?: return
        if (request.targetType == TargetType.CHANNEL) {
            rejectAndBlockTarget(reason)
            return
        }
        val scope = _state.value.scope
        perform("Abgelehnt, Kanal gesperrt") {
            val result = api.decide(request.id, Decision(DecisionAction.REJECT, scope, reason.trim()))
            api.createRule(
                RuleCreate(
                    childId = if (scope == Scope.ALL) null else request.childId,
                    targetType = TargetType.CHANNEL,
                    targetId = request.channelId,
                    effect = Effect.BLOCK,
                    title = request.channelName,
                    channelName = request.channelName,
                    note = "Gesperrt bei Anfrage #${request.id}",
                ),
            )
            result
        }
    }

    fun preview(videoId: String, title: String) {
        _state.update { it.copy(preview = Preview(videoId, title)) }
        viewModelScope.launch {
            attempt { StreamChoice.pick(youtube.stream(videoId).candidates) }
                .onSuccess { source ->
                    val error = if (source == null) "Für dieses Video wurde kein abspielbarer Stream gefunden." else null
                    _state.update { it.copy(preview = Preview(videoId, title, source, loading = false, error = error)) }
                }
                .onFailure { e ->
                    _state.update { it.copy(preview = Preview(videoId, title, loading = false, error = e.userMessage())) }
                }
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun decide(action: DecisionAction, reason: String, message: String) {
        val scope = _state.value.scope
        perform(message) { api.decide(requestId, Decision(action, scope, reason.trim())) }
    }

    private fun perform(message: String, block: suspend () -> ContentRequest) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { request ->
                    _state.update { it.copy(busy = false, request = request, message = message) }
                    onDecided(request.id)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/requests/RequestDetailScreen.kt`:

```kotlin
package de.safetube.parent.requests

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import de.safetube.android.player.PreviewPlayer
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import de.safetube.parent.ui.TextInputDialog

class RequestDetailActions(
    val onBack: () -> Unit,
    val onScope: (Scope) -> Unit,
    val onApprove: () -> Unit,
    val onApproveVideoOnly: () -> Unit,
    val onReject: (String) -> Unit,
    val onRejectBlockTarget: (String) -> Unit,
    val onRejectBlockChannel: (String) -> Unit,
    val onPreview: (videoId: String, title: String) -> Unit,
    val onClosePreview: () -> Unit,
    val onOpenYouTube: (String) -> Unit,
    val onMessageShown: () -> Unit,
)

@Composable
fun RequestDetailScreen(requestId: Int, onBack: () -> Unit, onDecided: (Int) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val vm = viewModel(key = "request-$requestId") {
        RequestDetailViewModel(container.api, container.youtube, requestId, onDecided)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    RequestDetailContent(
        state,
        RequestDetailActions(
            onBack = onBack,
            onScope = vm::setScope,
            onApprove = vm::approve,
            onApproveVideoOnly = vm::approveVideoOnly,
            onReject = vm::reject,
            onRejectBlockTarget = vm::rejectAndBlockTarget,
            onRejectBlockChannel = vm::rejectAndBlockChannel,
            onPreview = vm::preview,
            onClosePreview = vm::closePreview,
            onOpenYouTube = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onMessageShown = vm::messageShown,
        ),
    )
}

/** Welche Ablehnung im Dialog gerade bestätigt wird. */
private enum class RejectKind(val title: String) {
    PLAIN("Ablehnen"),
    BLOCK_TARGET("Ablehnen und sperren"),
    BLOCK_CHANNEL("Ablehnen und Kanal sperren"),
}

@Composable
fun RequestDetailContent(state: RequestDetailState, actions: RequestDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<RejectKind?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    ScreenScaffold(title = "Anfrage", onBack = actions.onBack, snackbar = snackbar) { padding ->
        val request = state.request
        when {
            state.loading && request == null -> Loading(Modifier.padding(padding))
            request == null -> ErrorMessage(state.error ?: "Anfrage nicht gefunden.", modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.preview?.let { preview -> item { PreviewBox(preview, actions.onClosePreview) } }
                item { Header(request) }
                item { DecisionBlock(request, state, actions) { rejecting = it } }
                if (request.targetType == TargetType.CHANNEL) {
                    item { Text("Letzte Videos des Kanals", style = MaterialTheme.typography.titleMedium) }
                    state.channelVideosError?.let { message -> item { ErrorMessage(message) } }
                    items(state.channelVideos, key = { it.videoId }) { video ->
                        ListItem(
                            modifier = Modifier.clickable { actions.onPreview(video.videoId, video.title) },
                            headlineContent = { Text(video.title) },
                            supportingContent = { Text(Formats.duration(video.durationSeconds) + (video.uploadedText?.let { " · $it" } ?: "")) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    rejecting?.let { kind ->
        TextInputDialog(
            title = kind.title,
            label = "Grund (optional, sieht das Kind)",
            confirm = kind.title,
            allowEmpty = true,
            onConfirm = { reason ->
                when (kind) {
                    RejectKind.PLAIN -> actions.onReject(reason)
                    RejectKind.BLOCK_TARGET -> actions.onRejectBlockTarget(reason)
                    RejectKind.BLOCK_CHANNEL -> actions.onRejectBlockChannel(reason)
                }
            },
            onDismiss = { rejecting = null },
        )
    }
}

@Composable
private fun PreviewBox(preview: Preview, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val source = preview.source
        when {
            preview.loading -> Row(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { CircularProgressIndicator(Modifier.padding(32.dp)) }
            source != null -> PreviewPlayer(source)
            else -> ErrorMessage(preview.error ?: "Vorschau nicht möglich.")
        }
        Row {
            Text(preview.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = onClose) { Text("Vorschau schließen") }
        }
    }
}

@Composable
private fun Header(request: ContentRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (request.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = request.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Text(request.title.ifEmpty { request.targetId }, style = MaterialTheme.typography.titleLarge)
        val kind = if (request.targetType == TargetType.VIDEO) "Video von ${request.channelName}" else "Ganzer Kanal"
        Text(kind)
        Text("Anfrage von ${request.childName} · ${Formats.dateTime(request.createdAt)}", style = MaterialTheme.typography.bodySmall)
        if (request.liftsBlock) {
            // Festlegung 15: Das Kind fragt zu etwas, das ausdrücklich gesperrt ist.
            Text(
                "Bittet um Aufhebung einer Sperre",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (request.childMessage.isNotBlank()) {
            Text("„${request.childMessage}“", style = MaterialTheme.typography.bodyLarge)
        }
        if (request.status != RequestStatus.OPEN) {
            val verb = if (request.status == RequestStatus.APPROVED) "Freigegeben" else "Abgelehnt"
            val by = request.decidedByName?.let { " von $it" } ?: ""
            val at = request.decidedAt?.let { " am ${Formats.dateTime(it)}" } ?: ""
            Text("$verb$by$at", color = MaterialTheme.colorScheme.primary)
            if (request.reason.isNotBlank()) Text("Grund: ${request.reason}")
        }
    }
}

@Composable
private fun DecisionBlock(
    request: ContentRequest,
    state: RequestDetailState,
    actions: RequestDetailActions,
    onReject: (RejectKind) -> Unit,
) {
    val enabled = !state.busy
    val previewId = if (request.targetType == TargetType.VIDEO) request.targetId else request.sourceVideoId
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (previewId.isNotBlank()) {
                OutlinedButton(onClick = { actions.onPreview(previewId, request.sourceVideoTitle.ifEmpty { request.title }) }) {
                    Text("Vorschau abspielen")
                }
            }
            if (request.url.isNotBlank()) {
                TextButton(onClick = { actions.onOpenYouTube(request.url) }) { Text("In YouTube öffnen") }
            }
        }
        Text(
            if (request.status == RequestStatus.OPEN) "Entscheiden" else "Neu entscheiden",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.scope == Scope.CHILD, onClick = { actions.onScope(Scope.CHILD) }, label = { Text("Nur ${request.childName}") })
            FilterChip(selected = state.scope == Scope.ALL, onClick = { actions.onScope(Scope.ALL) }, label = { Text("Alle Kinder") })
        }
        Button(onClick = actions.onApprove, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Freigeben") }
        if (request.targetType == TargetType.CHANNEL && request.sourceVideoId.isNotBlank()) {
            OutlinedButton(onClick = actions.onApproveVideoOnly, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Nur das Video „${request.sourceVideoTitle.ifEmpty { request.sourceVideoId }}“ freigeben")
            }
        }
        OutlinedButton(onClick = { onReject(RejectKind.PLAIN) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Ablehnen") }
        if (request.targetType == TargetType.VIDEO) {
            OutlinedButton(onClick = { onReject(RejectKind.BLOCK_TARGET) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Ablehnen + Video sperren")
            }
        }
        if (request.targetType == TargetType.CHANNEL || request.channelId.isNotBlank()) {
            OutlinedButton(onClick = { onReject(RejectKind.BLOCK_CHANNEL) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Ablehnen + Kanal sperren")
            }
        }
    }
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.requests.RequestDetailScreen`):

```kotlin
            composable(Routes.REQUEST, arguments = requestIdArg) { entry ->
                RequestDetailScreen(
                    requestId = entry.arguments!!.getInt("requestId"),
                    onBack = { nav.popBackStack() },
                    onDecided = { },
                )
            }
```

Task 22 ersetzt `onDecided = { }` durch das Entfernen der zugehörigen Benachrichtigung.

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `RequestDetailViewModelTest` (10) und `RequestDetailScreenTest` (3) grün.

- [ ] **Step 5: Commit**

```bash
git add android/core-android android/parent
git commit -m "Eltern-App: Anfrage-Detail mit Vorschau-Player und allen Entscheidungen"
```

---

### Task 15: Regeln ansehen, bearbeiten, löschen (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/rules/RulesViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/rules/RulesScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `RULES`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/rules/RulesViewModelTest.kt`

Spec 7: Liste pro Kind bzw. „alle Kinder“, Suche, Bearbeiten, Löschen. Auswahl oben: **Alle Regeln**, **Für alle Kinder** (`global_only`) oder ein Kind (dessen Regeln plus die für alle). Die Suche filtert lokal über Titel, Kanalname und ID.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/rules/RulesViewModelTest.kt`:

```kotlin
package de.safetube.parent.rules

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RulesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        rulesList += Samples.rule(1, null, TargetType.CHANNEL, "UCmaus", Effect.ALLOW, title = "Die Maus", channelName = "Die Maus")
        rulesList += Samples.rule(2, 1, TargetType.VIDEO, "vid1", Effect.BLOCK, title = "Gruselvideo", channelName = "Horror TV")
        rulesList += Samples.rule(3, 2, TargetType.CHANNEL, "UClego", Effect.ALLOW, title = "LEGO", channelName = "LEGO")
    }

    private fun viewModel() = RulesViewModel(api).apply { load() }

    @Test
    fun `alle Regeln mit Bereich`() {
        val vm = viewModel()

        assertEquals(listOf(1, 2, 3), vm.state.value.visible.map { it.id })
        assertEquals("alle Kinder", vm.state.value.scopeLabel(null))
        assertEquals("Mia", vm.state.value.scopeLabel(1))
    }

    @Test
    fun `Regeln eines Kindes enthalten die für alle`() {
        val vm = viewModel()

        vm.setScope(RuleScope.ForChild(1))

        assertEquals(listOf(1, 2), vm.state.value.visible.map { it.id })
        assertTrue("rules 1 false" in api.calls)
    }

    @Test
    fun `nur Regeln für alle Kinder`() {
        val vm = viewModel()

        vm.setScope(RuleScope.Global)

        assertEquals(listOf(1), vm.state.value.visible.map { it.id })
        assertTrue("rules null true" in api.calls)
    }

    @Test
    fun `Suche über Titel, Kanal und ID ohne Groß-Klein`() {
        val vm = viewModel()

        vm.setQuery("horror")
        assertEquals(listOf(2), vm.state.value.visible.map { it.id })
        vm.setQuery("uclego")
        assertEquals(listOf(3), vm.state.value.visible.map { it.id })
        vm.setQuery("")
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test
    fun `bearbeiten und löschen`() {
        val vm = viewModel()

        vm.update(api.rulesList.first { it.id == 2 }, Effect.ALLOW, "doch ok")
        assertEquals(Effect.ALLOW, vm.state.value.visible.first { it.id == 2 }.effect)
        assertEquals("doch ok", vm.state.value.visible.first { it.id == 2 }.note)

        vm.delete(api.rulesList.first { it.id == 3 })
        assertEquals(listOf(1, 2), vm.state.value.visible.map { it.id })
        assertEquals("Regel gelöscht", vm.state.value.message)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.rules.RulesViewModelTest'`
Expected: FAIL — `Unresolved reference: RulesViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/rules/RulesViewModel.kt`:

```kotlin
package de.safetube.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.Rule
import de.safetube.core.api.RuleUpdate
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RuleScope {
    data object All : RuleScope
    data object Global : RuleScope
    data class ForChild(val childId: Int) : RuleScope
}

data class RulesState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val scope: RuleScope = RuleScope.All,
    val query: String = "",
    val rules: List<Rule> = emptyList(),
    val error: String? = null,
    val message: String? = null,
) {
    val visible: List<Rule>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return rules
            return rules.filter { rule ->
                listOf(rule.title, rule.channelName, rule.targetId).any { it.lowercase().contains(q) }
            }
        }

    fun scopeLabel(childId: Int?): String =
        if (childId == null) "alle Kinder" else children.firstOrNull { it.id == childId }?.name ?: "Kind $childId"
}

class RulesViewModel(private val api: ParentApi) : ViewModel() {
    private val _state = MutableStateFlow(RulesState())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    fun setScope(scope: RuleScope) {
        _state.update { it.copy(scope = scope) }
        load()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun load() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = current.children.ifEmpty { api.children() }
                val rules = when (val scope = current.scope) {
                    RuleScope.All -> api.rules()
                    RuleScope.Global -> api.rules(globalOnly = true)
                    is RuleScope.ForChild -> api.rules(childId = scope.childId)
                }
                children to rules
            }
                .onSuccess { (children, rules) -> _state.update { it.copy(loading = false, children = children, rules = rules) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun update(rule: Rule, effect: Effect, note: String) {
        viewModelScope.launch {
            attempt { api.updateRule(rule.id, RuleUpdate(effect = effect, note = note.trim())) }
                .onSuccess { changed ->
                    _state.update { s -> s.copy(rules = s.rules.map { if (it.id == changed.id) changed else it }, message = "Gespeichert") }
                }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch {
            attempt { api.deleteRule(rule.id) }
                .onSuccess { _state.update { s -> s.copy(rules = s.rules.filterNot { it.id == rule.id }, message = "Regel gelöscht") } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/rules/RulesScreen.kt`:

```kotlin
package de.safetube.parent.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Effect
import de.safetube.core.api.Rule
import de.safetube.core.api.TargetType
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun RulesScreen(onAdd: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { RulesViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    RulesContent(state, onAdd, vm::setScope, vm::setQuery, vm::update, vm::delete, vm::messageShown, vm::load)
}

@Composable
fun RulesContent(
    state: RulesState,
    onAdd: () -> Unit,
    onScope: (RuleScope) -> Unit,
    onQuery: (String) -> Unit,
    onUpdate: (Rule, Effect, String) -> Unit,
    onDelete: (Rule) -> Unit,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Rule?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    ScreenScaffold(
        title = "Regeln",
        snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Neue Regel") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = state.scope == RuleScope.All, onClick = { onScope(RuleScope.All) }, label = { Text("Alle Regeln") })
                FilterChip(selected = state.scope == RuleScope.Global, onClick = { onScope(RuleScope.Global) }, label = { Text("Für alle Kinder") })
                state.children.forEach { child ->
                    FilterChip(
                        selected = state.scope == RuleScope.ForChild(child.id),
                        onClick = { onScope(RuleScope.ForChild(child.id)) },
                        label = { Text(child.name) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Suchen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading && state.rules.isEmpty() -> Loading()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { message -> item { ErrorMessage(message, onRetry) } }
                    if (state.visible.isEmpty() && state.error == null) {
                        item { Text("Keine Regeln.", Modifier.padding(16.dp)) }
                    }
                    items(state.visible, key = { it.id }) { rule ->
                        val kind = if (rule.targetType == TargetType.VIDEO) "Video" else "Kanal"
                        val effect = if (rule.effect == Effect.ALLOW) "erlaubt" else "gesperrt"
                        ListItem(
                            modifier = Modifier.clickable { editing = rule },
                            headlineContent = { Text(rule.title.ifEmpty { rule.targetId }) },
                            overlineContent = { Text("$kind · ${state.scopeLabel(rule.childId)}") },
                            supportingContent = {
                                Column {
                                    if (rule.targetType == TargetType.VIDEO && rule.channelName.isNotEmpty()) Text(rule.channelName)
                                    if (rule.note.isNotEmpty()) Text(rule.note, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                Text(
                                    effect,
                                    color = if (rule.effect == Effect.ALLOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    editing?.let { rule ->
        RuleEditDialog(rule, onSave = { effect, note -> onUpdate(rule, effect, note) }, onDelete = { onDelete(rule) }, onDismiss = { editing = null })
    }
}

@Composable
private fun RuleEditDialog(rule: Rule, onSave: (Effect, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var effect by remember { mutableStateOf(rule.effect) }
    var note by remember { mutableStateOf(rule.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.title.ifEmpty { rule.targetId }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = effect == Effect.ALLOW, onClick = { effect = Effect.ALLOW }, label = { Text("Erlauben") })
                    FilterChip(selected = effect == Effect.BLOCK, onClick = { effect = Effect.BLOCK }, label = { Text("Sperren") })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") })
                TextButton(onClick = { confirmDelete = true }) { Text("Regel löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(effect, note); onDismiss() }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Regel löschen?",
            text = "Danach gilt für dieses Ziel wieder die nächste passende Regel bzw. „gesperrt“.",
            confirm = "Löschen",
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.rules.RulesScreen`):

```kotlin
            composable(Routes.RULES) {
                RulesScreen(onAdd = { nav.navigate(Routes.RULE_ADD) })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `RulesViewModelTest` (5) grün. (Bis Task 16 führt „Neue Regel“ auf eine noch fehlende Route — Navigation zu einer unbekannten Route wirft zur Laufzeit; der Knopf wird erst nach Task 16 benutzt.)

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Regeln ansehen, bearbeiten und löschen"
```

---

### Task 16: Neue Regel per Link oder Suche (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/rules/AddRuleViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/rules/AddRuleScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `RULE_ADD`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/rules/AddRuleViewModelTest.kt`

Spec 7: neue Regel per eingefügtem Link oder In-App-Suche. Aus Link oder Treffer entsteht ein Entwurf (Typ, ID, Titel, Kanalname); dazu wählt man Erlauben/Sperren, Bereich (Kind oder alle) und optional eine Notiz. Kanal-Aliase (`@handle`) werden über den Extractor zur ID aufgelöst (Festlegung 12). Schlägt das Nachladen des Titels bei bekannter ID fehl, entsteht der Entwurf trotzdem (ohne Titel).

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/rules/AddRuleViewModelTest.kt`:

```kotlin
package de.safetube.parent.rules

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.core.api.YouTubeException
import de.safetube.core.extractor.ChannelItem
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.FakeYouTubeSource
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddRuleViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"
    private val api = FakeParentApi().apply { childrenList += Samples.child(1, "Mia") }
    private val youtube = FakeYouTubeSource().apply {
        streams["dQw4w9WgXcQ"] = FakeYouTubeSource.stream("dQw4w9WgXcQ", channelId, title = "Never Gonna")
        channels[channelId] = FakeYouTubeSource.channel(channelId, name = "Rick Astley")
        channels["https://www.youtube.com/@RickAstleyYT"] = FakeYouTubeSource.channel(channelId, name = "Rick Astley")
    }

    private fun viewModel() = AddRuleViewModel(api, youtube).apply { load() }

    @Test
    fun `Video-Link wird mit Titel zum Entwurf`() {
        val vm = viewModel()

        vm.onLink("https://youtu.be/dQw4w9WgXcQ")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.VIDEO, "dQw4w9WgXcQ", "Never Gonna", "Kanal"), vm.state.value.draft)
    }

    @Test
    fun `Kanal-Alias wird zur Kanal-ID aufgelöst`() {
        val vm = viewModel()

        vm.onLink("https://www.youtube.com/@RickAstleyYT")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.CHANNEL, channelId, "Rick Astley", "Rick Astley"), vm.state.value.draft)
    }

    @Test
    fun `unbekannter Link`() {
        val vm = viewModel()

        vm.onLink("https://example.com")
        vm.inspectLink()

        assertNull(vm.state.value.draft)
        assertEquals("Das ist kein YouTube-Link, den ich verstehe.", vm.state.value.error)
    }

    @Test
    fun `ohne Titel weiter, wenn YouTube gerade nicht antwortet`() {
        val vm = viewModel()
        youtube.failure = YouTubeException("YouTube ist gerade nicht erreichbar.")

        vm.onLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.VIDEO, "dQw4w9WgXcQ", "", ""), vm.state.value.draft)
        assertEquals("Titel konnte nicht geladen werden: YouTube ist gerade nicht erreichbar.", vm.state.value.error)
    }

    @Test
    fun `Suche und Kanal ohne ID aus dem Treffer auflösen`() {
        val hit = SearchHit.Channel(ChannelItem(null, "Rick Astley", "https://www.youtube.com/@RickAstleyYT", null, 0))
        youtube.searchResults["rick"] = listOf(hit)
        val vm = viewModel()

        vm.onSearchQuery("rick")
        vm.setSearchFilter(SearchFilter.CHANNELS)
        vm.search()
        assertEquals(listOf(hit), vm.state.value.results)
        assertTrue("search rick CHANNELS" in youtube.calls)

        vm.pick(hit)
        assertEquals(channelId, vm.state.value.draft!!.targetId)
    }

    @Test
    fun `speichern mit Bereich, Wirkung und Notiz`() {
        val vm = viewModel()
        vm.onLink("https://youtu.be/dQw4w9WgXcQ")
        vm.inspectLink()

        vm.setEffect(Effect.BLOCK)
        vm.setScopeChild(1)
        vm.onNote(" nervt ")
        vm.save()

        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.VIDEO, rule.targetType)
        assertEquals("dQw4w9WgXcQ", rule.targetId)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Never Gonna", rule.title)
        assertEquals("nervt", rule.note)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `ohne Entwurf wird nichts gespeichert`() {
        val vm = viewModel()

        vm.save()

        assertNull(api.lastRuleCreate)
        assertFalse(vm.state.value.saved)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.rules.AddRuleViewModelTest'`
Expected: FAIL — `Unresolved reference: AddRuleViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/rules/AddRuleViewModel.kt`:

```kotlin
package de.safetube.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.YouTubeSource
import de.safetube.core.youtube.YouTubeIds
import de.safetube.core.youtube.YouTubeLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleDraft(val targetType: TargetType, val targetId: String, val title: String, val channelName: String)

enum class AddMode { LINK, SEARCH }

data class AddRuleState(
    val children: List<Child> = emptyList(),
    val mode: AddMode = AddMode.LINK,
    val link: String = "",
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val results: List<SearchHit> = emptyList(),
    val working: Boolean = false,
    val draft: RuleDraft? = null,
    val effect: Effect = Effect.ALLOW,
    /** null = für alle Kinder. */
    val scopeChildId: Int? = null,
    val note: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class AddRuleViewModel(private val api: ParentApi, private val youtube: YouTubeSource) : ViewModel() {
    private val _state = MutableStateFlow(AddRuleState())
    val state: StateFlow<AddRuleState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            attempt { api.children() }
                .onSuccess { children -> _state.update { it.copy(children = children) } }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }

    fun setMode(mode: AddMode) = _state.update { it.copy(mode = mode, error = null) }
    fun onLink(value: String) = _state.update { it.copy(link = value, error = null) }
    fun onSearchQuery(value: String) = _state.update { it.copy(searchQuery = value) }
    fun setSearchFilter(filter: SearchFilter) = _state.update { it.copy(searchFilter = filter) }
    fun setEffect(effect: Effect) = _state.update { it.copy(effect = effect) }
    fun setScopeChild(childId: Int?) = _state.update { it.copy(scopeChildId = childId) }
    fun onNote(value: String) = _state.update { it.copy(note = value) }
    fun clearDraft() = _state.update { it.copy(draft = null) }

    fun inspectLink() {
        when (val link = YouTubeIds.parse(_state.value.link)) {
            null -> _state.update { it.copy(error = "Das ist kein YouTube-Link, den ich verstehe.", draft = null) }
            is YouTubeLink.Video -> resolve(fallback = RuleDraft(TargetType.VIDEO, link.videoId, "", "")) {
                val details = youtube.stream(link.videoId)
                RuleDraft(TargetType.VIDEO, link.videoId, details.title, details.channelName)
            }
            is YouTubeLink.Channel -> resolve(fallback = RuleDraft(TargetType.CHANNEL, link.channelId, "", "")) {
                val page = youtube.channel(link.channelId, maxVideos = 1)
                RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
            }
            is YouTubeLink.ChannelAlias -> resolve(fallback = null) {
                val page = youtube.channel(link.url, maxVideos = 1)
                RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
            }
        }
    }

    fun search() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            attempt { youtube.search(query, _state.value.searchFilter) }
                .onSuccess { hits -> _state.update { it.copy(working = false, results = hits) } }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.userMessage()) } }
        }
    }

    fun pick(hit: SearchHit) {
        when (hit) {
            is SearchHit.Video -> _state.update {
                it.copy(draft = RuleDraft(TargetType.VIDEO, hit.item.videoId, hit.item.title, hit.item.channelName), error = null)
            }
            is SearchHit.Channel -> {
                val id = hit.item.channelId
                if (id != null) {
                    _state.update { it.copy(draft = RuleDraft(TargetType.CHANNEL, id, hit.item.name, hit.item.name), error = null) }
                } else {
                    resolve(fallback = null) {
                        val page = youtube.channel(hit.item.url, maxVideos = 1)
                        RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
                    }
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            attempt {
                api.createRule(
                    RuleCreate(
                        childId = current.scopeChildId,
                        targetType = draft.targetType,
                        targetId = draft.targetId,
                        effect = current.effect,
                        title = draft.title,
                        channelName = draft.channelName,
                        note = current.note.trim(),
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.userMessage()) } }
        }
    }

    /** Lädt Details zum Entwurf; mit [fallback] entsteht bei Fehlern trotzdem ein Entwurf. */
    private fun resolve(fallback: RuleDraft?, block: suspend () -> RuleDraft) {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { draft -> _state.update { it.copy(working = false, draft = draft) } }
                .onFailure { e ->
                    if (fallback != null) {
                        _state.update {
                            it.copy(working = false, draft = fallback, error = "Titel konnte nicht geladen werden: ${e.userMessage()}")
                        }
                    } else {
                        _state.update { it.copy(working = false, draft = null, error = e.userMessage()) }
                    }
                }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/rules/AddRuleScreen.kt`:

```kotlin
package de.safetube.parent.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ChildFilter
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun AddRuleScreen(onDone: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { AddRuleViewModel(container.api, container.youtube).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    AddRuleContent(state, vm, onDone)
}

@Composable
private fun AddRuleContent(state: AddRuleState, vm: AddRuleViewModel, onBack: () -> Unit) {
    ScreenScaffold(title = "Neue Regel", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = state.mode.ordinal) {
                Tab(selected = state.mode == AddMode.LINK, onClick = { vm.setMode(AddMode.LINK) }, text = { Text("Link einfügen") })
                Tab(selected = state.mode == AddMode.SEARCH, onClick = { vm.setMode(AddMode.SEARCH) }, text = { Text("Suchen") })
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val draft = state.draft
                if (draft != null) {
                    item { DraftCard(state, draft, vm) }
                } else if (state.mode == AddMode.LINK) {
                    item {
                        OutlinedTextField(
                            value = state.link,
                            onValueChange = vm::onLink,
                            label = { Text("YouTube-Link (Video oder Kanal)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { vm.inspectLink() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { Button(onClick = vm::inspectLink, enabled = !state.working) { Text("Prüfen") } }
                } else {
                    item {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = vm::onSearchQuery,
                            label = { Text("Suchbegriff") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SearchFilter.ALL to "Alles", SearchFilter.VIDEOS to "Videos", SearchFilter.CHANNELS to "Kanäle")
                                .forEach { (filter, label) ->
                                    FilterChip(
                                        selected = state.searchFilter == filter,
                                        onClick = { vm.setSearchFilter(filter); vm.search() },
                                        label = { Text(label) },
                                    )
                                }
                        }
                    }
                    items(state.results) { hit -> SearchHitRow(hit) { vm.pick(hit) } }
                }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onPick: () -> Unit) {
    when (hit) {
        is SearchHit.Video -> ListItem(
            modifier = Modifier.clickable(onClick = onPick),
            overlineContent = { Text("Video" + if (hit.item.isShort) " (Short)" else "") },
            headlineContent = { Text(hit.item.title) },
            supportingContent = { Text("${hit.item.channelName} · ${Formats.duration(hit.item.durationSeconds)}") },
        )
        is SearchHit.Channel -> ListItem(
            modifier = Modifier.clickable(onClick = onPick),
            overlineContent = { Text("Kanal") },
            headlineContent = { Text(hit.item.name) },
        )
    }
    HorizontalDivider()
}

@Composable
private fun DraftCard(state: AddRuleState, draft: RuleDraft, vm: AddRuleViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (draft.targetType == TargetType.VIDEO) "Video" else "Kanal", style = MaterialTheme.typography.labelMedium)
            Text(draft.title.ifEmpty { draft.targetId }, style = MaterialTheme.typography.titleMedium)
            if (draft.targetType == TargetType.VIDEO && draft.channelName.isNotEmpty()) Text(draft.channelName)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.effect == Effect.ALLOW, onClick = { vm.setEffect(Effect.ALLOW) }, label = { Text("Erlauben") })
                FilterChip(selected = state.effect == Effect.BLOCK, onClick = { vm.setEffect(Effect.BLOCK) }, label = { Text("Sperren") })
            }
            Text("Gilt für", style = MaterialTheme.typography.labelMedium)
        }
        ChildFilter(state.children, state.scopeChildId, "Alle Kinder", vm::setScopeChild)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.note, onValueChange = vm::onNote, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::save, enabled = !state.saving) { Text("Regel speichern") }
                TextButton(onClick = vm::clearDraft) { Text("Anderes wählen") }
            }
        }
    }
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.rules.AddRuleScreen`):

```kotlin
            composable(Routes.RULE_ADD) {
                AddRuleScreen(onDone = { nav.popBackStack() })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `AddRuleViewModelTest` (7) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: neue Regel per Link oder Suche"
```

---

### Task 17: Verlauf (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/history/HistoryViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/history/HistoryScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `HISTORY`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/history/HistoryViewModelTest.kt`

Spec 7: Zeitleiste pro Kind; je Eintrag Video/Kanal freigeben oder sperren. Der Server liefert `allowed_now` (wie das Gate heute entscheiden würde). Geblättert wird mit `before = startedAt` des letzten Eintrags; eine volle Seite (50) heißt „es gibt vielleicht mehr“. Nach einer neuen Regel wird die erste Seite neu geladen, damit `allowed_now` stimmt.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/history/HistoryViewModelTest.kt`:

```kotlin
package de.safetube.parent.history

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        historyByChild[1] = listOf(Samples.history(1), Samples.history(2), Samples.history(3))
    }

    private fun viewModel() = HistoryViewModel(api, childId = 1, pageSize = 2).apply { load() }

    @Test
    fun `erste Seite und weiterblättern`() {
        val vm = viewModel()
        assertEquals("Mia", vm.state.value.childName)
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.id })
        assertTrue(vm.state.value.canLoadMore)

        vm.loadMore()

        assertEquals(listOf(1, 2, 3), vm.state.value.entries.map { it.id })
        assertFalse(vm.state.value.canLoadMore)
        assertTrue("history 1 ${Samples.history(2).startedAt}" in api.calls)
    }

    @Test
    fun `Video aus dem Verlauf sperren`() {
        val vm = viewModel()
        val entry = vm.state.value.entries.first()

        vm.setRule(entry, TargetType.VIDEO, Effect.BLOCK)

        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.VIDEO, rule.targetType)
        assertEquals(entry.videoId, rule.targetId)
        assertEquals(entry.title, rule.title)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Video gesperrt", vm.state.value.message)
    }

    @Test
    fun `Kanal aus dem Verlauf freigeben`() {
        val vm = viewModel()
        val entry = vm.state.value.entries.first()

        vm.setRule(entry, TargetType.CHANNEL, Effect.ALLOW)

        val rule = api.lastRuleCreate!!
        assertEquals(entry.channelId, rule.targetId)
        assertEquals(entry.channelName, rule.title)
        assertEquals("Kanal freigegeben", vm.state.value.message)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.history.*'`
Expected: FAIL — `Unresolved reference: HistoryViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/history/HistoryViewModel.kt`:

```kotlin
package de.safetube.parent.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Effect
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val loading: Boolean = true,
    val childName: String = "",
    val entries: List<HistoryEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class HistoryViewModel(
    private val api: ParentApi,
    private val childId: Int,
    private val pageSize: Int = 50,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val name = api.children().firstOrNull { it.id == childId }?.name.orEmpty()
                name to api.history(childId, limit = pageSize)
            }
                .onSuccess { (name, entries) ->
                    _state.update {
                        it.copy(loading = false, childName = name, entries = entries, canLoadMore = entries.size == pageSize)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        val last = current.entries.lastOrNull() ?: return
        if (current.loadingMore || !current.canLoadMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            attempt { api.history(childId, limit = pageSize, before = last.startedAt) }
                .onSuccess { more ->
                    _state.update {
                        it.copy(loadingMore = false, entries = it.entries + more, canLoadMore = more.size == pageSize)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingMore = false, message = e.userMessage()) } }
        }
    }

    fun setRule(entry: HistoryEntry, type: TargetType, effect: Effect) {
        val video = type == TargetType.VIDEO
        val targetId = if (video) entry.videoId else entry.channelId
        if (targetId.isBlank()) return
        viewModelScope.launch {
            attempt {
                api.createRule(
                    RuleCreate(
                        childId = childId,
                        targetType = type,
                        targetId = targetId,
                        effect = effect,
                        title = if (video) entry.title else entry.channelName,
                        channelName = entry.channelName,
                    ),
                )
            }
                .onSuccess {
                    val what = if (video) "Video" else "Kanal"
                    val verb = if (effect == Effect.ALLOW) "freigegeben" else "gesperrt"
                    _state.update { it.copy(message = "$what $verb") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/history/HistoryScreen.kt`:

```kotlin
package de.safetube.parent.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Effect
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun HistoryScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "history-$childId") { HistoryViewModel(container.api, childId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Verlauf ${state.childName}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        when {
            state.loading && state.entries.isEmpty() -> Loading(Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let { message -> item { ErrorMessage(message, vm::load) } }
                if (state.entries.isEmpty() && state.error == null) {
                    item { Text("Noch nichts geschaut.", Modifier.padding(16.dp)) }
                }
                items(state.entries, key = { it.id }) { entry -> HistoryRow(entry, vm::setRule) }
                if (state.canLoadMore) {
                    item {
                        TextButton(onClick = vm::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.loadingMore) "Lade …" else "Ältere laden")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onRule: (HistoryEntry, TargetType, Effect) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val details = buildList {
        add(Formats.dateTime(entry.startedAt))
        add(Formats.duration(entry.playedSeconds.toLong()))
        if (entry.inRewardMode) add("Belohnungsmodus")
    }.joinToString(" · ")
    ListItem(
        overlineContent = { Text(details) },
        headlineContent = { Text(entry.title.ifEmpty { entry.videoId }) },
        supportingContent = {
            Text(
                "${entry.channelName} · jetzt " + if (entry.allowedNow) "erlaubt" else "gesperrt",
                color = if (entry.allowedNow) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Aktionen") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Video freigeben") }, onClick = { menu = false; onRule(entry, TargetType.VIDEO, Effect.ALLOW) })
                    DropdownMenuItem(text = { Text("Video sperren") }, onClick = { menu = false; onRule(entry, TargetType.VIDEO, Effect.BLOCK) })
                    if (entry.channelId.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Kanal freigeben") }, onClick = { menu = false; onRule(entry, TargetType.CHANNEL, Effect.ALLOW) })
                        DropdownMenuItem(text = { Text("Kanal sperren") }, onClick = { menu = false; onRule(entry, TargetType.CHANNEL, Effect.BLOCK) })
                    }
                }
            }
        },
    )
    HorizontalDivider()
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.history.HistoryScreen`):

```kotlin
            composable(Routes.HISTORY, arguments = childIdArg) { entry ->
                HistoryScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `HistoryViewModelTest` (3) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Verlauf mit Freigeben/Sperren je Eintrag"
```

---

### Task 18: Statistik (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/stats/StatsViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/stats/StatsScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `STATS`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/stats/StatsViewModelTest.kt`

Spec 7: pro Tag/Woche, Top-Kanäle, Anteil Belohnungsmodus. Umschaltbar 7 oder 30 Tage; die Tage kommen vom Server schon in Berliner Zeit. Balken als einfache Compose-Boxen (keine Diagramm-Bibliothek).

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/stats/StatsViewModelTest.kt`:

```kotlin
package de.safetube.parent.stats

import de.safetube.core.api.ChannelStat
import de.safetube.core.api.DayStat
import de.safetube.core.api.Stats
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        statsByChild[1] = Stats(
            days = listOf(DayStat("2026-11-14", 1200, 0), DayStat("2026-11-15", 2400, 1200)),
            topChannels = listOf(ChannelStat("UCmaus", "Die Maus", 3000), ChannelStat("UClego", "LEGO", 600)),
            totalSeconds = 3600,
            rewardSeconds = 900,
        )
    }

    @Test
    fun `7 Tage mit Kennzahlen, umschalten auf 30`() {
        val vm = StatsViewModel(api, childId = 1).apply { load() }

        val state = vm.state.value
        assertEquals("Mia", state.childName)
        assertEquals(2400, state.maxDaySeconds)
        assertEquals(25, state.rewardPercent)
        assertEquals("Die Maus", state.stats!!.topChannels.first().channelName)
        assertTrue("stats 1 7" in api.calls)

        vm.setDays(30)

        assertEquals(30, vm.state.value.days)
        assertTrue("stats 1 30" in api.calls)
    }

    @Test
    fun `ohne Schauzeit kein Teilen durch null`() {
        api.statsByChild[1] = Samples.stats(today = 0)

        val vm = StatsViewModel(api, childId = 1).apply { load() }

        assertEquals(0, vm.state.value.rewardPercent)
        assertEquals(0, vm.state.value.maxDaySeconds)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.stats.*'`
Expected: FAIL — `Unresolved reference: StatsViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/stats/StatsViewModel.kt`:

```kotlin
package de.safetube.parent.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ParentApi
import de.safetube.core.api.Stats
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsState(
    val loading: Boolean = true,
    val childName: String = "",
    val days: Int = 7,
    val stats: Stats? = null,
    val error: String? = null,
) {
    val maxDaySeconds: Int get() = stats?.days?.maxOfOrNull { it.seconds } ?: 0

    val rewardPercent: Int
        get() {
            val total = stats?.totalSeconds ?: 0
            return if (total > 0) (stats!!.rewardSeconds * 100) / total else 0
        }
}

class StatsViewModel(private val api: ParentApi, private val childId: Int) : ViewModel() {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun setDays(days: Int) {
        _state.update { it.copy(days = days) }
        load()
    }

    fun load() {
        val days = _state.value.days
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val name = api.children().firstOrNull { it.id == childId }?.name.orEmpty()
                name to api.stats(childId, days)
            }
                .onSuccess { (name, stats) -> _state.update { it.copy(loading = false, childName = name, stats = stats) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/stats/StatsScreen.kt`:

```kotlin
package de.safetube.parent.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.DayStat
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun StatsScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "stats-$childId") { StatsViewModel(container.api, childId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    ScreenScaffold(title = "Statistik ${state.childName}".trim(), onBack = onBack) { padding ->
        val stats = state.stats
        when {
            state.loading && stats == null -> Loading(Modifier.padding(padding))
            stats == null -> ErrorMessage(state.error ?: "Keine Daten.", vm::load, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30).forEach { days ->
                            FilterChip(selected = state.days == days, onClick = { vm.setDays(days) }, label = { Text("$days Tage") })
                        }
                    }
                }
                item {
                    Text("Gesamt: ${Formats.duration(stats.totalSeconds.toLong())}", style = MaterialTheme.typography.titleMedium)
                    Text("davon im Belohnungsmodus: ${Formats.duration(stats.rewardSeconds.toLong())} (${state.rewardPercent} %)")
                }
                item { Text("Pro Tag", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(stats.days.reversed(), key = { it.date }) { day -> DayBar(day, state.maxDaySeconds) }
                item { Text("Top-Kanäle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                if (stats.topChannels.isEmpty()) item { Text("Noch keine.") }
                items(stats.topChannels, key = { it.channelId }) { channel ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(channel.channelName.ifEmpty { channel.channelId }, modifier = Modifier.weight(1f))
                        Text(Formats.duration(channel.seconds.toLong()))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBar(day: DayStat, max: Int) {
    val fraction = if (max > 0) day.seconds.toFloat() / max else 0f
    val rewardFraction = if (day.seconds > 0) day.rewardSeconds.toFloat() / day.seconds else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Formats.dayLabel(day.date), modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(16.dp)) {
            if (fraction > 0f) {
                Row(Modifier.fillMaxWidth(fraction).fillMaxHeight()) {
                    Box(Modifier.weight(1f - rewardFraction + 0.0001f).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    if (rewardFraction > 0f) {
                        Box(Modifier.weight(rewardFraction).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                    }
                }
            }
        }
        Text(Formats.duration(day.seconds.toLong()), modifier = Modifier.width(80.dp))
    }
}
```

Der helle Teil (`tertiary`) eines Balkens ist der Anteil im Belohnungsmodus. `+ 0.0001f` verhindert ein Gewicht von 0, wenn der ganze Tag im Belohnungsmodus lag (Compose verlangt Gewichte > 0).

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.stats.StatsScreen`):

```kotlin
            composable(Routes.STATS, arguments = childIdArg) { entry ->
                StatsScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `StatsViewModelTest` (2) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Statistik pro Tag, Top-Kanäle, Anteil Belohnungsmodus"
```

---

### Task 19: Belohnungsmodus (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/reward/RewardViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/reward/RewardScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `REWARD`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/reward/RewardViewModelTest.kt`

Spec 7: Dauer (Tage/Stunden/Minuten) oder Endzeit, Schalter „auch Gesperrtes“, [Sofort beenden]. Immer zeitlich begrenzt (höchstens 30 Tage), Ablauf nach Serveruhr. Endzeit wird in Berliner Zeit eingegeben.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/reward/RewardViewModelTest.kt`:

```kotlin
package de.safetube.parent.reward

import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RewardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply { childrenList += Samples.child(1, "Mia") }

    private fun viewModel() = RewardViewModel(api, childId = 1, clock = { Samples.NOW }).apply { load() }

    @Test
    fun `Vorbelegung eine Stunde, Endzeit in einer Stunde auf Viertelstunde gerundet`() {
        val state = viewModel().state.value

        assertEquals("Mia", state.child!!.name)
        assertEquals("1", state.hours)
        // NOW = 10:00 UTC = 11:00 Berlin → 12:00
        assertEquals(LocalDate.of(2026, 11, 15), state.date)
        assertEquals(LocalTime.of(12, 0), state.time)
    }

    @Test
    fun `Dauer starten`() {
        val vm = viewModel()
        vm.setDays("0")
        vm.setHours("1")
        vm.setMinutes("30")
        vm.setIncludeBlocked(true)

        vm.start()

        assertEquals(90, api.lastReward!!.minutes)
        assertNull(api.lastReward!!.until)
        assertTrue(api.lastReward!!.includeBlocked)
        assertTrue(vm.state.value.child!!.rewardActive)
        assertTrue(vm.state.value.message!!.startsWith("Belohnungsmodus läuft bis"))
    }

    @Test
    fun `ungültige Dauer geht nicht an den Server`() {
        val vm = viewModel()
        vm.setHours("0")

        vm.start()

        assertEquals("Bitte eine Dauer zwischen 1 Minute und 30 Tagen angeben.", vm.state.value.error)
        assertFalse("startReward 1" in api.calls)
    }

    @Test
    fun `Endzeit starten`() {
        val vm = viewModel()
        vm.setMode(RewardMode.UNTIL)
        vm.setDate(LocalDate.of(2026, 11, 15))
        vm.setTime(LocalTime.of(18, 30))

        vm.start()

        assertEquals(Instant.parse("2026-11-15T17:30:00Z"), api.lastReward!!.until)
        assertNull(api.lastReward!!.minutes)
    }

    @Test
    fun `Endzeit in der Vergangenheit`() {
        val vm = viewModel()
        vm.setMode(RewardMode.UNTIL)
        vm.setTime(LocalTime.of(9, 0))

        vm.start()

        assertEquals("Die Endzeit muss in der Zukunft liegen (höchstens 30 Tage).", vm.state.value.error)
    }

    @Test
    fun `nur Ziffern in den Dauerfeldern`() {
        val vm = viewModel()

        vm.setMinutes("4a5")

        assertEquals("45", vm.state.value.minutes)
    }

    @Test
    fun `sofort beenden`() {
        val vm = viewModel()
        vm.start()

        vm.stop()

        assertFalse(vm.state.value.child!!.rewardActive)
        assertEquals("Belohnungsmodus beendet", vm.state.value.message)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.reward.*'`
Expected: FAIL — `Unresolved reference: RewardViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/reward/RewardViewModel.kt`:

```kotlin
package de.safetube.parent.reward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RewardStart
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.format.Formats
import de.safetube.core.format.RewardDuration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RewardMode { DURATION, UNTIL }

data class RewardState(
    val loading: Boolean = true,
    val child: Child? = null,
    val mode: RewardMode = RewardMode.DURATION,
    val days: String = "0",
    val hours: String = "1",
    val minutes: String = "0",
    val date: LocalDate = LocalDate.now(Formats.ZONE),
    val time: LocalTime = LocalTime.NOON,
    val includeBlocked: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class RewardViewModel(
    private val api: ParentApi,
    private val childId: Int,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(defaultState())
    val state: StateFlow<RewardState> = _state.asStateFlow()

    /** Endzeit-Vorschlag: in einer Stunde, auf die nächste Viertelstunde aufgerundet (Berliner Zeit). */
    private fun defaultState(): RewardState {
        val inOneHour = clock().plus(1, ChronoUnit.HOURS).atZone(Formats.ZONE)
        val rounded = inOneHour.truncatedTo(ChronoUnit.HOURS)
            .plusMinutes(((inOneHour.minute + 14) / 15) * 15L)
        return RewardState(date = rounded.toLocalDate(), time = rounded.toLocalTime())
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.children().first { it.id == childId } }
                .onSuccess { child -> _state.update { it.copy(loading = false, child = child) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun setMode(mode: RewardMode) = _state.update { it.copy(mode = mode, error = null) }
    fun setDays(value: String) = _state.update { it.copy(days = digits(value), error = null) }
    fun setHours(value: String) = _state.update { it.copy(hours = digits(value), error = null) }
    fun setMinutes(value: String) = _state.update { it.copy(minutes = digits(value), error = null) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date, error = null) }
    fun setTime(time: LocalTime) = _state.update { it.copy(time = time, error = null) }
    fun setIncludeBlocked(value: Boolean) = _state.update { it.copy(includeBlocked = value) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun start() {
        val s = _state.value
        val reward = when (s.mode) {
            RewardMode.DURATION -> RewardDuration.minutes(s.days.toIntOrZero(), s.hours.toIntOrZero(), s.minutes.toIntOrZero())
                ?.let { RewardStart(minutes = it, includeBlocked = s.includeBlocked) }
                ?: return fail("Bitte eine Dauer zwischen 1 Minute und 30 Tagen angeben.")
            RewardMode.UNTIL -> RewardDuration.until(s.date, s.time, clock())
                ?.let { RewardStart(until = it, includeBlocked = s.includeBlocked) }
                ?: return fail("Die Endzeit muss in der Zukunft liegen (höchstens 30 Tage).")
        }
        perform { api.startReward(childId, reward) }
    }

    fun stop() = perform { api.stopReward(childId) }

    private fun perform(block: suspend () -> Child) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { child ->
                    val until = child.rewardUntil
                    val message = if (child.rewardActive && until != null) {
                        "Belohnungsmodus läuft bis ${Formats.dateTime(until)}"
                    } else {
                        "Belohnungsmodus beendet"
                    }
                    _state.update { it.copy(busy = false, child = child, message = message) }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.userMessage()) } }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private fun digits(value: String) = value.filter(Char::isDigit).take(4)

    private fun String.toIntOrZero() = toIntOrNull() ?: 0
}
```

Hinweis: In der Rundung ergibt `(minute + 14) / 15 * 15` bei Minute 0 genau 0 (volle Stunde bleibt), bei 1–15 → 15 usw.; 60 rollt über `plusMinutes` in die nächste Stunde.

`android/parent/src/main/kotlin/de/safetube/parent/reward/RewardScreen.kt`:

```kotlin
package de.safetube.parent.reward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun RewardScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "reward-$childId") { RewardViewModel(container.api, childId).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Belohnung ${state.child?.name.orEmpty()}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        if (state.loading && state.child == null) {
            Loading(Modifier.padding(padding))
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(state, vm::stop)
                Text("Neu starten", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.mode == RewardMode.DURATION, onClick = { vm.setMode(RewardMode.DURATION) }, label = { Text("Dauer") })
                    FilterChip(selected = state.mode == RewardMode.UNTIL, onClick = { vm.setMode(RewardMode.UNTIL) }, label = { Text("Bis Uhrzeit") })
                }
                if (state.mode == RewardMode.DURATION) DurationFields(state, vm) else UntilFields(state, vm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auch Gesperrtes erlauben")
                        Text("Sonst bleiben ausdrücklich gesperrte Videos und Kanäle gesperrt.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.includeBlocked, onCheckedChange = vm::setIncludeBlocked)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::start, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Belohnungsmodus starten") }
            }
        }
    }
}

@Composable
private fun StatusCard(state: RewardState, onStop: () -> Unit) {
    val child = state.child ?: return
    val until = child.rewardUntil
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (child.rewardActive && until != null) {
                Text("Läuft: ${Formats.remaining(until, Instant.now())}", style = MaterialTheme.typography.titleMedium)
                Text("bis ${Formats.dateTime(until)}" + if (child.rewardIncludesBlocked) " · auch Gesperrtes" else "")
                OutlinedButton(onClick = onStop, enabled = !state.busy) { Text("Sofort beenden") }
            } else {
                Text("Kein Belohnungsmodus aktiv.")
            }
        }
    }
}

@Composable
private fun DurationFields(state: RewardState, vm: RewardViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Tage", state.days, vm::setDays, Modifier.weight(1f))
        NumberField("Stunden", state.hours, vm::setHours, Modifier.weight(1f))
        NumberField("Minuten", state.minutes, vm::setMinutes, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilFields(state: RewardState, vm: RewardViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }) { Text("%02d.%02d.%d".format(state.date.dayOfMonth, state.date.monthValue, state.date.year)) }
        OutlinedButton(onClick = { pickTime = true }) { Text("%02d:%02d Uhr".format(state.time.hour, state.time.minute)) }
    }
    if (pickDate) {
        // DatePicker rechnet in UTC-Mitternacht.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        vm.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = dateState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = state.time.hour, initialMinute = state.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(timeState.hour, timeState.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Abbrechen") } },
        )
    }
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.reward.RewardScreen`):

```kotlin
            composable(Routes.REWARD, arguments = childIdArg) { entry ->
                RewardScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `RewardViewModelTest` (7) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Belohnungsmodus mit Dauer oder Endzeit"
```

---

### Task 20: Kind-Einstellungen, Kopplungs-QR-Code, Geräte (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/child/QrCode.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/child/ChildSettingsViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/child/ChildSettingsScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `CHILD`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/child/QrCodeTest.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/child/ChildSettingsViewModelTest.kt`

Spec 7: Anzeige `compact`/`full`, Shorts an/aus, Kopplungs-QR-Code, Geräte mit App-Version, Entkoppeln. Dazu **Kind löschen** (Festlegung 16) ganz unten, mit Rückfrage, die den Namen nennt und sagt, was alles verschwindet; danach zurück zur Übersicht. Der QR-Code enthält `qr_payload` vom Server (`{"server": …, "code": …}`); die Kinder-App (Plan 3) liest ihn. Der Code gilt 10 Minuten und wird zusätzlich als Text angezeigt (zum Abtippen).

- [ ] **Step 1: Failing tests schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/child/QrCodeTest.kt`:

```kotlin
package de.safetube.parent.child

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    @Test
    fun `QR-Code lässt sich zurücklesen`() {
        val payload = """{"server": "https://safetube.isoletta.me", "code": "AB12CD34"}"""

        val pixels = QrCode.pixels(payload, size = 300)

        assertEquals(300 * 300, pixels.size)
        val source = RGBLuminanceSource(300, 300, pixels)
        // PURE_BARCODE: Bild enthält nur den Code (wie von uns erzeugt), keine Perspektive suchen.
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true))
        assertEquals(payload, decoded.text)
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/child/ChildSettingsViewModelTest.kt`:

```kotlin
package de.safetube.parent.child

import de.safetube.core.api.DisplayMode
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChildSettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        devicesByChild[1] = mutableListOf(Samples.device(4), Samples.device(5, unpaired = true))
    }

    private fun viewModel() = ChildSettingsViewModel(api, childId = 1).apply { load() }

    @Test
    fun `lädt Kind und Geräte`() {
        val state = viewModel().state.value

        assertEquals("Mia", state.child!!.name)
        assertEquals(listOf(4, 5), state.devices.map { it.id })
    }

    @Test
    fun `Anzeige, Shorts und Name ändern`() {
        val vm = viewModel()

        vm.setDisplayMode(DisplayMode.FULL)
        vm.setShorts(true)
        vm.rename("  Mia Sophie ")
        vm.rename("   ")

        val child = vm.state.value.child!!
        assertEquals(DisplayMode.FULL, child.displayMode)
        assertTrue(child.shortsEnabled)
        assertEquals("Mia Sophie", child.name)
        assertEquals(3, api.calls.count { it == "updateChild 1" })
    }

    @Test
    fun `Kopplungscode erzeugen`() {
        val vm = viewModel()

        vm.createPairingCode()

        assertNotNull(vm.state.value.pairing)
        assertEquals("AB12CD34", vm.state.value.pairing!!.code)
    }

    @Test
    fun `Gerät entkoppeln`() {
        val vm = viewModel()

        vm.unpair(vm.state.value.devices.first { it.id == 4 })

        assertNotNull(vm.state.value.devices.first { it.id == 4 }.unpairedAt)
        assertEquals("Gerät entkoppelt", vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `Kind löschen`() {
        val vm = viewModel()

        vm.deleteChild()

        assertTrue(vm.state.value.deleted)
        assertTrue("deleteChild 1" in api.calls)
        assertTrue(api.childrenList.none { it.id == 1 })
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.child.*'`
Expected: FAIL — `Unresolved reference: QrCode` / `ChildSettingsViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/child/QrCode.kt`:

```kotlin
package de.safetube.parent.child

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCode {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** ARGB-Pixel eines quadratischen QR-Codes (reines Java, in JVM-Tests prüfbar). */
    fun pixels(text: String, size: Int = 512): IntArray {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        return IntArray(size * size) { index -> if (matrix[index % size, index / size]) BLACK else WHITE }
    }

    fun bitmap(text: String, size: Int = 512): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels(text, size), 0, size, 0, 0, size, size)
        return bitmap.asImageBitmap()
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/child/ChildSettingsViewModel.kt`:

```kotlin
package de.safetube.parent.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ChildUpdate
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.PairingCode
import de.safetube.core.api.ParentApi
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildSettingsState(
    val loading: Boolean = true,
    val child: Child? = null,
    val devices: List<Device> = emptyList(),
    val pairing: PairingCode? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Kind ist gelöscht, der Bildschirm schließt sich. */
    val deleted: Boolean = false,
)

class ChildSettingsViewModel(private val api: ParentApi, private val childId: Int) : ViewModel() {
    private val _state = MutableStateFlow(ChildSettingsState())
    val state: StateFlow<ChildSettingsState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.children().first { it.id == childId } to api.devices(childId) }
                .onSuccess { (child, devices) -> _state.update { it.copy(loading = false, child = child, devices = devices) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        change(ChildUpdate(name = trimmed), "Name gespeichert")
    }

    fun setDisplayMode(mode: DisplayMode) = change(ChildUpdate(displayMode = mode), "Anzeige geändert")

    fun setShorts(enabled: Boolean) = change(ChildUpdate(shortsEnabled = enabled), if (enabled) "Shorts an" else "Shorts aus")

    fun createPairingCode() {
        viewModelScope.launch {
            attempt { api.pairingCode(childId) }
                .onSuccess { code -> _state.update { it.copy(pairing = code) } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun closePairing() = _state.update { it.copy(pairing = null) }

    fun unpair(device: Device) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt {
                api.unpairDevice(device.id)
                api.devices(childId)
            }
                .onSuccess { devices -> _state.update { it.copy(busy = false, devices = devices, message = "Gerät entkoppelt") } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }

    fun deleteChild() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt { api.deleteChild(childId) }
                .onSuccess { _state.update { it.copy(busy = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun change(update: ChildUpdate, message: String) {
        viewModelScope.launch {
            attempt { api.updateChild(childId, update) }
                .onSuccess { child -> _state.update { it.copy(child = child, message = message) } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/child/ChildSettingsScreen.kt`:

```kotlin
package de.safetube.parent.child

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.PairingCode
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun ChildSettingsScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "child-$childId") { ChildSettingsViewModel(container.api, childId).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var unpairing by remember { mutableStateOf<Device?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Einstellungen ${state.child?.name.orEmpty()}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        val child = state.child
        if (child == null) {
            Loading(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var name by remember(child.name) { mutableStateOf(child.name) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.rename(name) }, enabled = name.trim() != child.name) { Text("Speichern") }
            }

            Text("Gesperrte Inhalte in Suche und Vorschlägen", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = child.displayMode == DisplayMode.COMPACT, onClick = { vm.setDisplayMode(DisplayMode.COMPACT) }, label = { Text("Kompakt") })
                FilterChip(selected = child.displayMode == DisplayMode.FULL, onClick = { vm.setDisplayMode(DisplayMode.FULL) }, label = { Text("Vollständig") })
            }
            Text(
                if (child.displayMode == DisplayMode.COMPACT) "Nur Titel und Kanal, kein Vorschaubild." else "Mit Vorschaubild, nur Abspielen ist gesperrt.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shorts", style = MaterialTheme.typography.titleMedium)
                    Text("Laufen wie normale Videos, ohne Endlos-Wischen.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = child.shortsEnabled, onCheckedChange = vm::setShorts)
            }

            HorizontalDivider()
            Text("Kinderhandy koppeln", style = MaterialTheme.typography.titleMedium)
            val pairing = state.pairing
            if (pairing == null) {
                Button(onClick = vm::createPairingCode) { Text("Kopplungscode erzeugen") }
            } else {
                PairingCard(pairing, vm::closePairing)
            }

            HorizontalDivider()
            Text("Geräte", style = MaterialTheme.typography.titleMedium)
            if (state.devices.isEmpty()) Text("Noch kein Gerät gekoppelt.")
            state.devices.forEach { device -> DeviceRow(device, enabled = !state.busy) { unpairing = device } }

            HorizontalDivider()
            OutlinedButton(onClick = { deleting = true }, enabled = !state.busy) {
                Text("${child.name} löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "${state.child?.name.orEmpty()} löschen?",
            text = "Gelöscht werden die eigenen Regeln, alle Anfragen, Verlauf und Schauzeit; gekoppelte Geräte " +
                "werden abgemeldet. Regeln für alle Kinder bleiben. Das lässt sich nicht rückgängig machen.",
            confirm = "Endgültig löschen",
            onConfirm = { vm.deleteChild() },
            onDismiss = { deleting = false },
        )
    }
    unpairing?.let { device ->
        ConfirmDialog(
            title = "Gerät entkoppeln?",
            text = "Die Kinder-App auf diesem Gerät zeigt danach nur noch „Bitte koppeln lassen“.",
            confirm = "Entkoppeln",
            onConfirm = { vm.unpair(device) },
            onDismiss = { unpairing = null },
        )
    }
}

@Composable
private fun PairingCard(pairing: PairingCode, onClose: () -> Unit) {
    val image = remember(pairing.qrPayload) { QrCode.bitmap(pairing.qrPayload) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(bitmap = image, contentDescription = "Kopplungs-QR-Code", modifier = Modifier.size(240.dp))
            Text(pairing.code, style = MaterialTheme.typography.headlineMedium)
            Text("Gültig bis ${Formats.time(pairing.expiresAt)} Uhr. In der Kinder-App scannen oder abtippen.")
            OutlinedButton(onClick = onClose) { Text("Fertig") }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, enabled: Boolean, onUnpair: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("App ${device.appVersion.ifEmpty { "?" }} · gekoppelt ${Formats.dateTime(device.pairedAt)}")
            val unpaired = device.unpairedAt
            if (unpaired != null) {
                Text("entkoppelt ${Formats.dateTime(unpaired)}", color = MaterialTheme.colorScheme.error)
            } else {
                Text("zuletzt gemeldet ${Formats.dateTime(device.lastSeen)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (device.unpairedAt == null) {
            TextButton(onClick = onUnpair, enabled = enabled) { Text("Entkoppeln") }
        }
    }
}
```

In `ParentNavHost.kt` unter `// Routen` einfügen (Import `de.safetube.parent.child.ChildSettingsScreen`):

```kotlin
            composable(Routes.CHILD, arguments = childIdArg) { entry ->
                ChildSettingsScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `QrCodeTest` (1) und `ChildSettingsViewModelTest` (5) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Kind-Einstellungen, Kopplungs-QR-Code und Geräte"
```

---

### Task 21: Eltern und eigenes Konto (`parent`)

**Files:**
- Create: `android/parent/src/main/kotlin/de/safetube/parent/parents/ParentsViewModel.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/parents/ParentsScreen.kt`
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/nav/ParentNavHost.kt` (Route `PARENTS`)
- Test: `android/parent/src/test/kotlin/de/safetube/parent/parents/ParentsViewModelTest.kt`

Spec 7: weitere gleichberechtigte Elternteile anlegen; eigene Mail-Einstellungen. Dazu Name und **Passwort ändern** (bisher nur per `curl`, siehe Plan 1) und Abmelden. Prüfungen wie der Server: Passwort mindestens 8 Zeichen.

- [ ] **Step 1: Failing test schreiben**

`android/parent/src/test/kotlin/de/safetube/parent/parents/ParentsViewModelTest.kt`:

```kotlin
package de.safetube.parent.parents

import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ParentsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi()
    private var loggedOut = 0

    private fun viewModel() = ParentsViewModel(api, logout = { loggedOut++ }).apply { load() }

    @Test
    fun `eigenes Konto und alle Eltern`() {
        val state = viewModel().state.value

        assertEquals("Sandro", state.me!!.name)
        assertEquals(1, state.parents.size)
    }

    @Test
    fun `Elternteil anlegen mit Prüfung`() {
        val vm = viewModel()

        vm.addParent("Anna", "anna-ohne-at", "langespasswort")
        assertEquals("Bitte eine gültige E-Mail-Adresse angeben.", vm.state.value.error)

        vm.addParent("Anna", "anna@example.org", "kurz")
        assertEquals("Das Passwort braucht mindestens 8 Zeichen.", vm.state.value.error)

        vm.addParent(" Anna ", " anna@example.org ", "langespasswort")
        assertNull(vm.state.value.error)
        assertTrue("createParent anna@example.org" in api.calls)
        assertEquals(2, vm.state.value.parents.size)
        assertEquals("Anna angelegt", vm.state.value.message)
    }

    @Test
    fun `Mail-Einstellungen`() {
        val vm = viewModel()

        vm.setMailAlerts(false)

        assertEquals(false, api.lastMeUpdate!!.mailAlerts)
        assertNull(api.lastMeUpdate!!.mailNewRequest)
        assertFalse(vm.state.value.me!!.mailAlerts)
    }

    @Test
    fun `Passwort ändern`() {
        val vm = viewModel()

        vm.changePassword("neuespasswort", "anderes")
        assertEquals("Die beiden Passwörter sind verschieden.", vm.state.value.error)
        assertNull(api.lastMeUpdate)

        vm.changePassword("neuespasswort", "neuespasswort")
        assertEquals("neuespasswort", api.lastMeUpdate!!.password)
        assertEquals("Passwort geändert", vm.state.value.message)
    }

    @Test
    fun abmelden() = runBlocking {
        val vm = viewModel()

        vm.logout()
        vm.logoutJob?.join()

        assertEquals(1, loggedOut)
        assertTrue(vm.state.value.loggedOut)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest --tests 'de.safetube.parent.parents.*'`
Expected: FAIL — `Unresolved reference: ParentsViewModel`.

- [ ] **Step 3: Implementieren**

`android/parent/src/main/kotlin/de/safetube/parent/parents/ParentsViewModel.kt`:

```kotlin
package de.safetube.parent.parents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.MeUpdate
import de.safetube.core.api.Parent
import de.safetube.core.api.ParentApi
import de.safetube.core.api.ParentCreate
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentsState(
    val loading: Boolean = true,
    val me: Parent? = null,
    val parents: List<Parent> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val loggedOut: Boolean = false,
)

/** [logout] meldet ab und löscht die Sitzung (Auth.logout), stoppt ab Task 22 auch den Push-Dienst. */
class ParentsViewModel(private val api: ParentApi, private val logout: suspend () -> Unit) : ViewModel() {
    private val _state = MutableStateFlow(ParentsState())
    val state: StateFlow<ParentsState> = _state.asStateFlow()

    var logoutJob: Job? = null
        private set

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.me() to api.parents() }
                .onSuccess { (me, parents) -> _state.update { it.copy(loading = false, me = me, parents = parents) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun addParent(name: String, email: String, password: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val problem = when {
            cleanName.isEmpty() -> "Bitte einen Namen angeben."
            !cleanEmail.contains('@') || cleanEmail.length < 3 -> "Bitte eine gültige E-Mail-Adresse angeben."
            password.length < 8 -> "Das Passwort braucht mindestens 8 Zeichen."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            attempt { api.createParent(ParentCreate(cleanName, cleanEmail, password)) }
                .onSuccess { parent ->
                    _state.update { it.copy(error = null, parents = it.parents + parent, message = "${parent.name} angelegt") }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateMe(MeUpdate(name = trimmed), "Name gespeichert")
    }

    fun setMailNewRequest(enabled: Boolean) = updateMe(MeUpdate(mailNewRequest = enabled), "Gespeichert")

    fun setMailAlerts(enabled: Boolean) = updateMe(MeUpdate(mailAlerts = enabled), "Gespeichert")

    fun changePassword(password: String, repeat: String) {
        val problem = when {
            password.length < 8 -> "Das Passwort braucht mindestens 8 Zeichen."
            password != repeat -> "Die beiden Passwörter sind verschieden."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        updateMe(MeUpdate(password = password), "Passwort geändert")
    }

    fun logout() {
        logoutJob = viewModelScope.launch {
            logout.invoke()
            _state.update { it.copy(loggedOut = true) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun updateMe(update: MeUpdate, message: String) {
        viewModelScope.launch {
            attempt { api.updateMe(update) }
                .onSuccess { me ->
                    _state.update { s ->
                        s.copy(
                            me = me,
                            parents = s.parents.map { if (it.id == me.id) me else it },
                            error = null,
                            message = message,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/parents/ParentsScreen.kt`:

```kotlin
package de.safetube.parent.parents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun ParentsScreen(onLoggedOut: () -> Unit, logout: suspend () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { ParentsViewModel(container.api, logout) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    var adding by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Eltern", snackbar = snackbar) { padding ->
        val me = state.me
        if (me == null) {
            Loading(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mein Konto", style = MaterialTheme.typography.titleMedium)
            var name by remember(me.name) { mutableStateOf(me.name) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.rename(name) }, enabled = name.trim() != me.name) { Text("Speichern") }
            }
            Text(me.email, style = MaterialTheme.typography.bodySmall)
            SwitchRow("Mail bei neuer Anfrage", me.mailNewRequest, vm::setMailNewRequest)
            SwitchRow("Mail bei Warnungen (Gerät, Störung)", me.mailAlerts, vm::setMailAlerts)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { changingPassword = true }) { Text("Passwort ändern") }
                TextButton(onClick = { confirmLogout = true }) { Text("Abmelden") }
            }

            HorizontalDivider()
            Text("Alle Eltern (gleichberechtigt)", style = MaterialTheme.typography.titleMedium)
            state.parents.forEach { parent ->
                Column {
                    Text(parent.name + if (parent.id == me.id) " (ich)" else "")
                    Text(parent.email, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { adding = true }) { Text("Elternteil hinzufügen") }
        }
    }

    if (adding) AddParentDialog(onAdd = vm::addParent, onDismiss = { adding = false })
    if (changingPassword) PasswordDialog(onChange = vm::changePassword, onDismiss = { changingPassword = false })
    if (confirmLogout) {
        ConfirmDialog(
            title = "Abmelden?",
            text = "Danach kommen auf diesem Handy keine Benachrichtigungen mehr an.",
            confirm = "Abmelden",
            onConfirm = vm::logout,
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AddParentDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elternteil hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Startpasswort (mind. 8 Zeichen)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, email, password); onDismiss() }) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PasswordDialog(onChange: (String, String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwort ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Neues Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Wiederholen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onChange(password, repeat); onDismiss() }) { Text("Ändern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
```

Prüffehler (z. B. „Passwörter verschieden“) erscheinen nach dem Schließen des Dialogs als roter Text unter den Mail-Schaltern.

In `ParentNavHost.kt` unter `// Routen` einfügen (Importe `de.safetube.parent.parents.ParentsScreen`, `de.safetube.parent.container`, `androidx.compose.ui.platform.LocalContext`):

```kotlin
            composable(Routes.PARENTS) {
                val container = LocalContext.current.container
                ParentsScreen(
                    onLoggedOut = {
                        onLoggedOut()
                        nav.toLogin()
                    },
                    logout = { container.auth.logout(container.api) },
                )
            }
```

- [ ] **Step 4: Tests und Build**

Run: `android/tools/remote-build.sh :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL, `ParentsViewModelTest` (5) grün.

- [ ] **Step 5: Commit**

```bash
git add android/parent
git commit -m "Eltern-App: Eltern anlegen, Mail-Einstellungen, Passwort, Abmelden"
```

---

### Task 22: Push-Empfang, Benachrichtigungen mit Aktionen, Abgleich, Akku

**Files:**
- Create: `android/core-android/src/main/kotlin/de/safetube/android/push/NtfyForegroundService.kt`
- Create: `android/core-android/src/main/kotlin/de/safetube/android/system/BatteryOptimization.kt`
- Test: `android/core-android/src/test/kotlin/de/safetube/android/system/BatteryOptimizationTest.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/ParentPushHandler.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/ParentNotifier.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/ParentPushService.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/DecisionReceiver.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/BootReceiver.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/OpenRequestsWorker.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/PushControl.kt`
- Create: `android/parent/src/main/kotlin/de/safetube/parent/push/PushSetupHints.kt`
- Modify: `android/parent/src/main/AndroidManifest.xml` (ganz ersetzen)
- Modify: `android/parent/src/main/kotlin/de/safetube/parent/AppContainer.kt`, `ParentApp.kt`, `MainActivity.kt`, `nav/ParentNavHost.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/push/ParentPushHandlerTest.kt`
- Test: `android/parent/src/test/kotlin/de/safetube/parent/push/ParentNotifierTest.kt`

Spec 7 (Benachrichtigungen) und 8 (Push über ntfy): Vordergrunddienst mit Dauerverbindung, ständige Benachrichtigung „verbunden“ (eigener Kanal, in Android ausblendbar), Bitte um Ausnahme von der Akku-Optimierung. Neue Anfrage → Benachrichtigung mit [Freigeben] [Ablehnen]; anderes Elternteil hat entschieden → Info, offene Benachrichtigung verschwindet; Belohnung abgelaufen / neues Gerät → Info; Gerät meldet sich nicht / Störung → Warnung. Doppelte Meldungen (Push und 15-Minuten-Abgleich) verhindert die gemeinsame Liste gemeldeter Anfragen (`SessionStore.markSeen`/`newRequests`). Die Bitte um Benachrichtigungs-Erlaubnis (Android 13+) und um die Akku-Ausnahme erscheinen als Hinweiskarten oben in der Übersicht.

- [ ] **Step 1: Failing tests schreiben**

`android/core-android/src/test/kotlin/de/safetube/android/system/BatteryOptimizationTest.kt`:

```kotlin
package de.safetube.android.system

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BatteryOptimizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `erkennt die Ausnahme`() {
        assertFalse(BatteryOptimization.isIgnoring(context))

        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)

        assertTrue(BatteryOptimization.isIgnoring(context))
    }

    @Test
    fun `Intent fragt direkt für diese App`() {
        val intent = BatteryOptimization.requestIntent(context)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/push/ParentPushHandlerTest.kt`:

```kotlin
package de.safetube.parent.push

import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.ParentPush
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentPushHandlerTest {

    private val shown = mutableListOf<String>()
    private val seen = mutableSetOf<Int>()

    private val notifications = object : ParentNotifications {
        override fun showRequest(push: ParentPush.NewRequest) { shown += "request ${push.requestId}" }
        override fun cancelRequest(requestId: Int) { shown += "cancel $requestId" }
        override fun showInfo(title: String, message: String) { shown += "info $title" }
        override fun showAlert(title: String, message: String) { shown += "alert $title" }
    }

    private val handler = ParentPushHandler(notifications) { id -> seen.add(id) }

    private fun event(vararg tags: String, title: String = "T") =
        NtfyEvent(id = "x", event = "message", title = title, message = "M", tags = tags.toList())

    @Test
    fun `neue Anfrage nur einmal melden`() = runBlocking {
        handler.handle(event("request:9"))
        handler.handle(event("request:9"))

        assertEquals(listOf("request 9"), shown)
    }

    @Test
    fun `Entscheidung anderswo entfernt die Anfrage und informiert`() = runBlocking {
        handler.handle(event("info", "decided:9", title = "Anna hat freigegeben"))

        assertEquals(listOf("cancel 9", "info Anna hat freigegeben"), shown)
    }

    @Test
    fun `Info, Warnung, sync`() = runBlocking {
        handler.handle(event("info", title = "Neues Gerät gekoppelt"))
        handler.handle(event("alert", title = "Gerät meldet sich nicht mehr"))
        handler.handle(event("sync"))

        assertEquals(listOf("info Neues Gerät gekoppelt", "alert Gerät meldet sich nicht mehr"), shown)
    }
}
```

`android/parent/src/test/kotlin/de/safetube/parent/push/ParentNotifierTest.kt`:

```kotlin
package de.safetube.parent.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.safetube.core.push.ParentPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ParentNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = ParentNotifier(context).apply { createChannels() }

    @Test
    fun `vier Kanäle, Verbindung ganz leise`() {
        val channels = manager.notificationChannels.associateBy { it.id }

        assertEquals(
            setOf(ParentNotifier.CH_REQUESTS, ParentNotifier.CH_INFO, ParentNotifier.CH_ALERTS, ParentNotifier.CH_CONNECTION),
            channels.keys,
        )
        assertEquals(NotificationManager.IMPORTANCE_MIN, channels.getValue(ParentNotifier.CH_CONNECTION).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channels.getValue(ParentNotifier.CH_REQUESTS).importance)
    }

    @Test
    fun `Anfrage mit Freigeben und Ablehnen, danach entfernen`() {
        notifier.showRequest(ParentPush.NewRequest(9, "Mia bittet um Freigabe", "Video: Maus"))

        val id = ParentNotifier.requestNotificationId(9)
        val shown = shadowOf(manager).getNotification(id)
        assertEquals("Mia bittet um Freigabe", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("Freigeben", "Ablehnen"), shown.actions.map { it.title.toString() })

        notifier.cancelRequest(9)
        assertNull(shadowOf(manager).getNotification(id))
    }

    @Test
    fun `Ergebnis einer Push-Aktion ersetzt die Anfrage ohne Aktionen`() {
        notifier.showRequest(ParentPush.NewRequest(9, "Mia bittet um Freigabe", "Video: Maus"))

        notifier.showDecisionResult(9, "Freigegeben ✓")

        val shown = shadowOf(manager).getNotification(ParentNotifier.requestNotificationId(9))
        assertEquals("Freigegeben ✓", shown.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(shown.actions == null || shown.actions.isEmpty())
    }

    @Test
    fun `Verbindungsbenachrichtigung ist dauerhaft`() {
        val notification = notifier.connection("Verbunden")

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ParentNotifier.CH_CONNECTION, notification.channelId)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Scheitern sehen**

Run: `android/tools/remote-build.sh :core-android:testDebugUnitTest :parent:testDebugUnitTest --tests 'de.safetube.parent.push.*'`
Expected: FAIL — `Unresolved reference: BatteryOptimization` / `ParentPushHandler` / `ParentNotifier`.

(Gradle wendet `--tests` auf alle genannten Test-Aufgaben an; für `:core-android` findet der Filter keine passenden Klassen. Schlägt der Lauf deshalb mit „No tests found for given includes“ fehl, beide Aufgaben einzeln aufrufen: zuerst `:core-android:testDebugUnitTest`, dann `:parent:testDebugUnitTest --tests '…push.*'`.)

- [ ] **Step 3: Implementieren — `core-android`**

`android/core-android/src/main/kotlin/de/safetube/android/system/BatteryOptimization.kt`:

```kotlin
package de.safetube.android.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Ausnahme von der Akku-Optimierung, damit die Push-Verbindung nicht schlafen gelegt wird (Spec 8). */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /** Öffnet den System-Dialog direkt für diese App (nicht für den Play Store gedacht, hier privat). */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
```

`android/core-android/src/main/kotlin/de/safetube/android/push/NtfyForegroundService.kt`:

```kotlin
package de.safetube.android.push

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.safetube.core.api.attempt
import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.NtfyStream
import de.safetube.core.push.PushSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class PushConnection(val baseUrl: String, val topic: String, val sinceId: String?)

/**
 * Vordergrunddienst mit Dauerverbindung zu einem ntfy-Topic (Spec 8). Eltern- und Kinder-App
 * leiten davon ab und entscheiden selbst, was eine Nachricht bedeutet.
 */
abstract class NtfyForegroundService : LifecycleService() {

    protected abstract val notificationId: Int
    protected abstract fun buildNotification(text: String): Notification

    /** null = nicht angemeldet bzw. kein Topic → der Dienst beendet sich. */
    protected abstract suspend fun connection(): PushConnection?
    protected abstract suspend fun onMessage(event: NtfyEvent)
    protected abstract suspend fun saveLastId(id: String)

    protected open val httpClient: OkHttpClient get() = defaultClient

    private val defaultClient by lazy { OkHttpClient() }
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = buildNotification("Verbinde …")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        if (job?.isActive != true) job = lifecycleScope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val connection = connection()
        if (connection == null) {
            stopSelf()
            return
        }
        NtfyStream(httpClient, connection.baseUrl, connection.topic).signals(connection.sinceId).collect { signal ->
            when (signal) {
                PushSignal.Connected -> update("Verbunden – Benachrichtigungen kommen sofort an")
                is PushSignal.Disconnected -> update("Keine Verbindung, versuche es weiter …")
                is PushSignal.Message -> {
                    attempt { onMessage(signal.event) }.onFailure { Log.w(TAG, "Push-Nachricht nicht verarbeitet", it) }
                    if (signal.event.id.isNotEmpty()) saveLastId(signal.event.id)
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // geprüft über areNotificationsEnabled()
    private fun update(text: String) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(notificationId, buildNotification(text))
    }

    private companion object {
        const val TAG = "NtfyService"
    }
}
```

- [ ] **Step 4: Implementieren — `parent`**

`android/parent/src/main/kotlin/de/safetube/parent/push/ParentPushHandler.kt`:

```kotlin
package de.safetube.parent.push

import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.ParentPush

/** Was die App mit Benachrichtigungen tun kann (Android-Teil in ParentNotifier, in Tests ein Fake). */
interface ParentNotifications {
    fun showRequest(push: ParentPush.NewRequest)
    fun cancelRequest(requestId: Int)
    fun showInfo(title: String, message: String)
    fun showAlert(title: String, message: String)
}

/** Übersetzt eine Push-Nachricht in Benachrichtigungen. [markSeen] liefert true, wenn die Anfrage neu ist. */
class ParentPushHandler(
    private val notifications: ParentNotifications,
    private val markSeen: suspend (Int) -> Boolean,
) {
    suspend fun handle(event: NtfyEvent) {
        when (val push = ParentPush.from(event) ?: return) {
            is ParentPush.NewRequest -> if (markSeen(push.requestId)) notifications.showRequest(push)
            is ParentPush.DecidedElsewhere -> {
                notifications.cancelRequest(push.requestId)
                notifications.showInfo(push.title, push.message)
            }
            is ParentPush.Info -> notifications.showInfo(push.title, push.message)
            is ParentPush.Alert -> notifications.showAlert(push.title, push.message)
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/ParentNotifier.kt`:

```kotlin
package de.safetube.parent.push

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.safetube.core.push.ParentPush
import de.safetube.parent.MainActivity
import de.safetube.parent.R
import java.util.concurrent.atomic.AtomicInteger

class ParentNotifier(private val context: Context) : ParentNotifications {
    private val manager = NotificationManagerCompat.from(context)
    private val nextId = AtomicInteger(INFO_BASE)

    fun createChannels() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CH_REQUESTS, "Anfragen", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Neue Freigabe-Anfragen der Kinder" },
                NotificationChannel(CH_INFO, "Infos", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Andere Eltern haben entschieden, Belohnung abgelaufen, neues Gerät" },
                NotificationChannel(CH_ALERTS, "Warnungen", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Gerät meldet sich nicht mehr, App gestört" },
                NotificationChannel(CH_CONNECTION, "Verbindung", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Ständige Benachrichtigung des Push-Dienstes – kann ausgeblendet werden" },
            ),
        )
    }

    override fun showRequest(push: ParentPush.NewRequest) {
        val notification = NotificationCompat.Builder(context, CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(push.title)
            .setContentText(push.message.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.message))
            .setContentIntent(openApp(push.requestId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Freigeben", decision(push.requestId, DecisionReceiver.ACTION_APPROVE))
            .addAction(0, "Ablehnen", decision(push.requestId, DecisionReceiver.ACTION_REJECT))
            .build()
        notify(requestNotificationId(push.requestId), notification)
    }

    override fun cancelRequest(requestId: Int) = manager.cancel(requestNotificationId(requestId))

    /** Ersetzt die Anfrage-Benachrichtigung nach einer Push-Aktion durch das Ergebnis (ohne Knöpfe). */
    fun showDecisionResult(requestId: Int, text: String) {
        val notification = NotificationCompat.Builder(context, CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Anfrage #$requestId")
            .setContentText(text)
            .setContentIntent(openApp(requestId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(requestNotificationId(requestId), notification)
    }

    override fun showInfo(title: String, message: String) = simple(CH_INFO, title, message)

    override fun showAlert(title: String, message: String) = simple(CH_ALERTS, title, message)

    fun connection(text: String): Notification =
        NotificationCompat.Builder(context, CH_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SafeTube Eltern")
            .setContentText(text)
            .setContentIntent(openApp(null))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun simple(channel: String, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp(null))
            .setAutoCancel(true)
            .build()
        notify(nextId.incrementAndGet(), notification)
    }

    private fun openApp(requestId: Int?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (requestId != null) intent.putExtra(MainActivity.EXTRA_REQUEST_ID, requestId)
        return PendingIntent.getActivity(
            context,
            requestId ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun decision(requestId: Int, action: String): PendingIntent {
        val intent = Intent(context, DecisionReceiver::class.java)
            .setAction(action)
            .putExtra(MainActivity.EXTRA_REQUEST_ID, requestId)
        val code = requestId * 2 + if (action == DecisionReceiver.ACTION_APPROVE) 0 else 1
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission") // geprüft über areNotificationsEnabled()
    private fun notify(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        const val CH_REQUESTS = "anfragen"
        const val CH_INFO = "infos"
        const val CH_ALERTS = "warnungen"
        const val CH_CONNECTION = "verbindung"
        const val CONNECTION_ID = 1
        private const val INFO_BASE = 10
        private const val REQUEST_BASE = 1_000_000

        fun requestNotificationId(requestId: Int) = REQUEST_BASE + requestId
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/ParentPushService.kt`:

```kotlin
package de.safetube.parent.push

import android.app.Notification
import de.safetube.android.push.NtfyForegroundService
import de.safetube.android.push.PushConnection
import de.safetube.core.push.NtfyEvent
import de.safetube.parent.container
import okhttp3.OkHttpClient

class ParentPushService : NtfyForegroundService() {
    private val app get() = applicationContext.container
    private val handler by lazy { ParentPushHandler(app.notifier) { app.sessionStore.markSeen(it) } }

    override val notificationId: Int = ParentNotifier.CONNECTION_ID
    override val httpClient: OkHttpClient get() = app.http

    override fun buildNotification(text: String): Notification = app.notifier.connection(text)

    override suspend fun connection(): PushConnection? {
        val session = app.sessionStore.load() ?: return null
        if (session.ntfyUrl.isEmpty() || session.ntfyTopic.isEmpty()) return null
        return PushConnection(session.ntfyUrl, session.ntfyTopic, app.sessionStore.lastNtfyId())
    }

    override suspend fun onMessage(event: NtfyEvent) = handler.handle(event)

    override suspend fun saveLastId(id: String) = app.sessionStore.setLastNtfyId(id)
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/DecisionReceiver.kt`:

```kotlin
package de.safetube.parent.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.parent.MainActivity
import de.safetube.parent.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** [Freigeben]/[Ablehnen] direkt aus der Benachrichtigung (Spec 7: Freigeben gilt nur für das anfragende Kind). */
class DecisionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(MainActivity.EXTRA_REQUEST_ID, -1).takeIf { it > 0 } ?: return
        val action = when (intent.action) {
            ACTION_APPROVE -> DecisionAction.APPROVE
            ACTION_REJECT -> DecisionAction.REJECT
            else -> return
        }
        val pending = goAsync()
        val app = context.container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.sessionStore.load() == null) {
                    app.notifier.showDecisionResult(requestId, "Nicht angemeldet – bitte in der App entscheiden.")
                    return@launch
                }
                attempt { app.api.decide(requestId, Decision(action, Scope.CHILD)) }
                    .onSuccess { request ->
                        val text = if (request.status == RequestStatus.APPROVED) "Freigegeben ✓" else "Abgelehnt"
                        app.notifier.showDecisionResult(requestId, text)
                    }
                    .onFailure { e -> app.notifier.showDecisionResult(requestId, "Hat nicht geklappt: ${e.userMessage()}") }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "de.safetube.parent.APPROVE"
        const val ACTION_REJECT = "de.safetube.parent.REJECT"
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/BootReceiver.kt`:

```kotlin
package de.safetube.parent.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.safetube.parent.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Nach Neustart oder App-Update den Push-Dienst wieder starten (beides ist vom Hintergrund-Startverbot ausgenommen). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.container.sessionStore.load() != null) PushControl.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/OpenRequestsWorker.kt`:

```kotlin
package de.safetube.parent.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.safetube.core.api.ApiException
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.push.OpenRequestCheck
import de.safetube.parent.container
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 15-Minuten-Abgleich (Festlegung 8): verpasste Anfragen nachmelden, Push-Dienst am Leben halten. */
class OpenRequestsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.container
        app.sessionStore.load() ?: return Result.success()
        return try {
            val open = app.api.requests(RequestFilter(status = RequestStatus.OPEN))
            app.sessionStore.newRequests(open).forEach { app.notifier.showRequest(OpenRequestCheck.notificationFor(it)) }
            PushControl.startService(applicationContext)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: ApiException) {
            if (e.status == 401) Result.success() else Result.retry()
        }
    }

    companion object {
        private const val NAME = "offene-anfragen"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OpenRequestsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/PushControl.kt`:

```kotlin
package de.safetube.parent.push

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object PushControl {
    private const val TAG = "PushControl"

    /** Nach Login, App-Start mit Sitzung und Neustart: Dienst und 15-Minuten-Abgleich. */
    fun start(context: Context) {
        OpenRequestsWorker.schedule(context)
        startService(context)
    }

    /** Darf scheitern, wenn Android den Start aus dem Hintergrund verbietet (ohne Akku-Ausnahme). */
    fun startService(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ParentPushService::class.java))
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Push-Dienst konnte nicht gestartet werden", e)
        }
    }

    fun stop(context: Context) {
        OpenRequestsWorker.cancel(context)
        context.stopService(Intent(context, ParentPushService::class.java))
    }
}
```

`android/parent/src/main/kotlin/de/safetube/parent/push/PushSetupHints.kt`:

```kotlin
package de.safetube.parent.push

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.safetube.android.system.BatteryOptimization

/** Hinweiskarten oben in der Übersicht, solange Benachrichtigungen oder die Akku-Ausnahme fehlen. */
@Composable
fun PushSetupHints() {
    val context = LocalContext.current
    fun notificationsAllowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(notificationsAllowed()) }
    var ignoringBattery by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed()
        ignoringBattery = BatteryOptimization.isIgnoring(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!allowed) {
            HintCard(
                text = "Ohne Erlaubnis für Benachrichtigungen erfährst du nichts von neuen Anfragen.",
                button = "Benachrichtigungen erlauben",
                onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        if (!ignoringBattery) {
            HintCard(
                text = "Damit Anfragen sofort ankommen, darf Android die App nicht schlafen legen.",
                button = "Akku-Optimierung ausschalten",
                onClick = { context.startActivity(BatteryOptimization.requestIntent(context)) },
            )
        }
    }
}

@Composable
private fun HintCard(text: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            Button(onClick = onClick) { Text(button) }
        }
    }
}
```

`android/parent/src/main/AndroidManifest.xml` (ersetzt die Fassung aus Task 10):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".ParentApp"
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".push.ParentPushService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Dauerverbindung zum eigenen Push-Server (ntfy) für Freigabe-Anfragen der Kinder" />
        </service>

        <receiver
            android:name=".push.DecisionReceiver"
            android:exported="false" />

        <receiver
            android:name=".push.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

`AppContainer.kt` ändern: Konstruktorparameter zur Eigenschaft machen und den Notifier ergänzen.

```kotlin
class AppContainer(private val context: Context) {
```

und unter `val youtube …` einfügen (Import `de.safetube.parent.push.ParentNotifier`):

```kotlin
    val notifier: ParentNotifier by lazy { ParentNotifier(context) }
```

`ParentApp.kt` ändern — Kanäle beim App-Start anlegen (billig, idempotent):

```kotlin
package de.safetube.parent

import android.app.Application
import android.content.Context
import de.safetube.parent.push.ParentNotifier

class ParentApp : Application() {
    /** Erst beim ersten Zugriff gebaut — Robolectric-Tests, die ihn nicht brauchen, bleiben schnell. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ParentNotifier(this).createChannels()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ParentApp).container
```

`MainActivity.kt` ändern — im `setContent`-Block die beiden Stellen ersetzen (Importe `de.safetube.parent.push.PushControl`, `de.safetube.parent.push.PushSetupHints`):

```kotlin
                LaunchedEffect(Unit) {
                    loggedIn = appContainer.sessionStore.load() != null
                    if (loggedIn == true) PushControl.start(this@MainActivity)
                }
```

```kotlin
                    else -> ParentNavHost(
                        loggedIn = state,
                        openRequestId = openRequest.value,
                        onRequestOpened = { openRequest.value = null },
                        onLoggedIn = {
                            loggedIn = true
                            PushControl.start(this@MainActivity)
                        },
                        onLoggedOut = {
                            PushControl.stop(this@MainActivity)
                            loggedIn = false
                        },
                        overviewHeader = { PushSetupHints() },
                    )
```

`ParentNavHost.kt` ändern — in der Route `REQUEST` die Benachrichtigung nach einer Entscheidung in der App entfernen:

```kotlin
            composable(Routes.REQUEST, arguments = requestIdArg) { entry ->
                val container = LocalContext.current.container
                RequestDetailScreen(
                    requestId = entry.arguments!!.getInt("requestId"),
                    onBack = { nav.popBackStack() },
                    onDecided = { container.notifier.cancelRequest(it) },
                )
            }
```

- [ ] **Step 5: Tests und Build**

Run: `android/tools/remote-build.sh :core-android:testDebugUnitTest :parent:testDebugUnitTest :parent:assembleDebug`
Expected: BUILD SUCCESSFUL; `BatteryOptimizationTest` (2), `ParentPushHandlerTest` (3), `ParentNotifierTest` (4) und alle früheren Tests grün.

- [ ] **Step 6: Commit**

```bash
git add android/core-android android/parent
git commit -m "Eltern-App: Push-Dienst, Benachrichtigungen mit Aktionen, 15-Minuten-Abgleich, Akku-Hinweis"
```

---

### Task 23: CI für Android (GitHub Actions)

**Files:**
- Create: `.github/workflows/android.yml`

Läuft bei Änderungen unter `android/` oder `shared/` (die Gate-Fälle betreffen auch `core`): `core`-Tests, alle Android-Unit-Tests (Robolectric), Debug-APK als Artefakt (14 Tage). Signierte Release-Builds und `/updates/` kommen in Plan 4. Die GitHub-Runner haben `ANDROID_HOME` gesetzt, damit bindet `settings.gradle.kts` die Android-Module ein.

- [ ] **Step 1: Workflow anlegen**

`.github/workflows/android.yml`:

```yaml
name: Android

on:
  push:
    paths: ["android/**", "shared/**", ".github/workflows/android.yml"]
  pull_request:
    paths: ["android/**", "shared/**"]

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Tests und Debug-Build
        run: ./gradlew --console=plain :core:test testDebugUnitTest :parent:assembleDebug
      - name: Testberichte bei Fehlern
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: android/**/build/reports/tests/
          retention-days: 7
      - uses: actions/upload-artifact@v4
        with:
          name: parent-debug-apk
          path: android/parent/build/outputs/apk/debug/*.apk
          retention-days: 14
```

- [ ] **Step 2: YAML lokal prüfen**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android.yml')); print('ok')"`
Expected: `ok` (falls `yaml` fehlt: `python3 -m pip install --user pyyaml` ist nicht nötig — dann genügt `ruby -ryaml -e 'YAML.load_file(".github/workflows/android.yml"); puts "ok"'` oder ein Blick auf die Einrückung; der eigentliche Test ist der Lauf auf GitHub).

- [ ] **Step 3: Commit und Push**

```bash
git add .github/workflows/android.yml
git commit -m "CI: Android-Tests und Debug-APK"
git push origin main
```

- [ ] **Step 4: Ergebnis von Jessica prüfen lassen**

Kein `gh` auf dem Pi: Jessica schaut unter `https://github.com/kindkains/youtube-safe/actions` nach, ob „Android“ und „Server-Tests“ grün sind, und meldet das Ergebnis (bei Rot: Link oder Log-Ausschnitt).

---

### Task 24: Abnahme auf dem Handy

**Files:**
- Create: `android/tools/fake-kid.sh`

Bis die Kinder-App (Plan 3) existiert, spielt ein kleines Skript ein Kinderhandy: koppeln, Anfragen stellen, Schauzeit melden. Damit lassen sich alle Abläufe der Eltern-App gegen den echten Server prüfen. **Das Installieren auf dem Handy und das Durchklicken macht Jessica** (bzw. Anna/Sandro); die Server-Seite (Skript) kann vom Pi aus laufen.

- [ ] **Step 1: Testskript anlegen**

`android/tools/fake-kid.sh`:

```bash
#!/usr/bin/env bash
# Spielt ein Kinderhandy, um die Eltern-App zu testen (bis die Kinder-App aus Plan 3 da ist).
#
#   fake-kid.sh pair <Kopplungscode>                     koppeln, Token wird gespeichert
#   fake-kid.sh request-video <Video-ID> [Nachricht]     Video-Anfrage stellen
#   fake-kid.sh request-channel <Kanal-ID> [Video-ID]    Kanal-Anfrage (optional mit Ursprungsvideo)
#   fake-kid.sh watch <Video-ID> <Kanal-ID> <Sekunden>   Schauzeit melden
#
# SERVER=https://… überschreibt den Standard-Server.
set -euo pipefail

SERVER="${SERVER:-https://safetube.isoletta.me}"
STATE="${XDG_CONFIG_HOME:-$HOME/.config}/safetube-fake-kid-token"
TOKEN="$( [ -f "$STATE" ] && cat "$STATE" || true )"

api() {
    local method=$1 path=$2 body=${3:-}
    local args=(-fsS -X "$method" "$SERVER/api/v1$path" -H "Content-Type: application/json")
    if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
    if [ -n "$body" ]; then args+=(--data "$body"); fi
    curl "${args[@]}"
}

json() { python3 -c 'import json,sys; print(json.dumps(dict(zip(sys.argv[1::2], sys.argv[2::2]))))' "$@"; }

case "${1:-}" in
    pair)
        TOKEN=""
        body=$(json code "${2:?Kopplungscode fehlt}" app_version fake-kid)
        mkdir -p "$(dirname "$STATE")"
        api POST /kid/pair "$body" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["token"]); print("gekoppelt mit", d["child_name"], file=sys.stderr)' > "$STATE"
        chmod 600 "$STATE"
        ;;
    request-video)
        body=$(json target_type video target_id "${2:?Video-ID fehlt}" title "Testvideo (fake-kid)" message "${3:-}")
        api POST /kid/requests "$body"; echo
        ;;
    request-channel)
        body=$(json target_type channel target_id "${2:?Kanal-ID fehlt}" title "Testkanal (fake-kid)" \
            source_video_id "${3:-}" source_video_title "${3:+Ursprungsvideo (fake-kid)}")
        api POST /kid/requests "$body"; echo
        ;;
    watch)
        body=$(python3 -c '
import json, sys, uuid, datetime
video, channel, seconds = sys.argv[1], sys.argv[2], int(sys.argv[3])
start = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
print(json.dumps({"entries": [{"client_id": uuid.uuid4().hex, "video_id": video, "channel_id": channel,
    "title": "Testvideo (fake-kid)", "channel_name": "Testkanal", "started_at": start, "played_seconds": seconds}]}))
' "${2:?Video-ID fehlt}" "${3:?Kanal-ID fehlt}" "${4:?Sekunden fehlen}")
        api POST /kid/watch "$body"; echo "gemeldet"
        ;;
    *)
        sed -n '2,10p' "$0"
        exit 2
        ;;
esac
```

```bash
chmod +x android/tools/fake-kid.sh
git add android/tools/fake-kid.sh
git commit -m "Werkzeug: fake-kid.sh spielt ein Kinderhandy für Tests der Eltern-App"
```

- [ ] **Step 2: APK bauen und bereitstellen**

Run: `android/tools/remote-build.sh :parent:assembleDebug`

Installation, eine der beiden Wege (mit Jessica abstimmen):
- **USB am Thinkbook:** Handy anschließen, USB-Debugging an, dann `ssh thinkbook "su - sandro -c '/home/sandro/youtube-safe/android-sdk/platform-tools/adb install -r /home/sandro/youtube-safe/repo/android/parent/build/outputs/apk/debug/parent-debug.apk'"`.
- **Artefakt aus GitHub Actions** (Task 23) herunterladen und auf dem Handy öffnen („Unbekannte Apps installieren“ erlauben).

Achtung: Thinkbook- und CI-Build sind mit **verschiedenen Debug-Schlüsseln** signiert; ein Wechsel zwischen beiden Quellen braucht vorher eine Deinstallation. Ein fester Release-Schlüssel kommt in Plan 4.

- [ ] **Step 3: Abnahme-Checkliste (mit Jessica)**

| # | Schritt | Erwartet |
|---|---|---|
| 1 | App starten, mit eigenem Konto anmelden | Übersicht; Hinweiskarten für Benachrichtigungen und Akku |
| 2 | Beide Hinweise bestätigen | Karten verschwinden; ständige Benachrichtigung „Verbunden …“ |
| 3 | Kind „Test“ anlegen (falls noch keins) | Karte „Test“, 0 s, keine Anfragen |
| 4 | Kind-Einstellungen → Kopplungscode erzeugen | QR-Code und 8-stelliger Code, „gültig bis …“ |
| 5 | Pi: `android/tools/fake-kid.sh pair <Code>` | „gekoppelt mit Test“; in der App: Info „Neues Gerät gekoppelt“, Gerät „App fake-kid“ in der Liste |
| 6 | Pi: `fake-kid.sh request-video dQw4w9WgXcQ "bitte"` | Benachrichtigung „Test bittet um Freigabe“ mit [Freigeben] [Ablehnen] innerhalb weniger Sekunden |
| 7 | In der Benachrichtigung [Freigeben] | Benachrichtigung wird zu „Freigegeben ✓“; in der App: Anfrage freigegeben, Regel „Video · Test · erlaubt“ |
| 8 | Zweites Elternteil-Handy (falls schon eingerichtet) | Info „<Name> hat freigegeben“ |
| 9 | `fake-kid.sh request-channel UCuAXFkgsw1L7xaCfnd5JJOw dQw4w9WgXcQ`, Benachrichtigung antippen | Detail öffnet sich; „Letzte Videos des Kanals“ (10); Vorschau eines Videos spielt; „Nur das Video … freigeben“ vorhanden |
| 10 | „Ablehnen + Kanal sperren“ mit Grund | Snackbar; Regel „Kanal · Test · gesperrt“ |
| 10a | `fake-kid.sh request-channel UCuAXFkgsw1L7xaCfnd5JJOw` erneut (Kanal ist jetzt gesperrt) | Benachrichtigung „Test bittet um Aufhebung einer Sperre“; in der Liste „Kanal (gesperrt)“, im Detail der Hinweis; Freigeben ersetzt die Sperre durch „erlaubt“ |
| 11 | Regeln → Neue Regel → Link `https://www.youtube.com/@RickAstleyYT` → Erlauben → für alle Kinder | Regel „Kanal · alle Kinder · erlaubt“ (ID `UC…`) |
| 12 | Regeln → Neue Regel → Suchen „Sendung mit der Maus“ → Kanal wählen → Speichern; danach bearbeiten (Sperren) und löschen | Liste aktualisiert sich jeweils |
| 13 | `fake-kid.sh watch dQw4w9WgXcQ UCuAXFkgsw1L7xaCfnd5JJOw 600` | Übersicht „Heute geschaut: 10 min“; Verlauf zeigt den Eintrag; Statistik 7 Tage mit Balken |
| 14 | Belohnung → Dauer 0/0/2 → starten; einige Minuten warten | Karte „Belohnungsmodus: noch 2 min“; kurz nach Ablauf (nächster Lauf des Server-Jobs) Info „Belohnungsmodus abgelaufen“ |
| 15 | App beenden (wegwischen), 20 Minuten später `fake-kid.sh request-video <andere ID>` | Benachrichtigung kommt trotzdem (Dienst läuft weiter bzw. 15-Minuten-Abgleich) |
| 16 | Handy neu starten, dann neue Anfrage | Benachrichtigung kommt (BootReceiver) |
| 17 | Eltern → Passwort ändern; abmelden; neu anmelden | Klappt mit neuem Passwort; nach Abmelden keine ständige Benachrichtigung mehr |
| 18 | Kind-Einstellungen → fake-kid-Gerät entkoppeln | Gerät „entkoppelt …“; `fake-kid.sh request-video …` scheitert mit 401 |
| 19 | Neues Testkind anlegen, koppeln (`fake-kid.sh pair …`), dann Kind-Einstellungen → „… löschen“ → bestätigen | Zurück in der Übersicht, Kind weg; seine Regeln und Anfragen weg, Regeln für alle Kinder noch da; `fake-kid.sh request-video …` scheitert mit 401 |

Ergebnisse (auch Abweichungen) in `CLAUDE.local.md` unter „Laufende Notizen“ festhalten. Fehler werden als eigene kleine Tasks nachgezogen (Test zuerst).

- [ ] **Step 4: Aufräumen**

Testkind und Testregeln in der App löschen (Kind-Einstellungen → „… löschen“, Regeln einzeln). Token-Datei des Skripts entfernen: `rm ~/.config/safetube-fake-kid-token`.

---

## Spec-Abdeckung (Selbstprüfung)

| Spec-Abschnitt | Task |
|---|---|
| 2 Architektur: `core` mit Server-Client, ntfy, Player, Extractor-Wrapper, Entscheidungslogik | 1–9 (`core`), 10, 14, 22 (`core-android`) — Self-Update ist Plan 4 |
| 4 Entscheidungslogik in Kotlin gegen `shared/gate-cases.json` | 2 |
| 5 Eltern-API vollständig | 4, 5 |
| 7 Übersicht | 12 |
| 7 Anfragen (offen oben, History, Filter) | 13 |
| 7 Anfrage-Detail (Vorschau, letzte 10 Videos, Player, In YouTube öffnen, alle Aktionen) | 14 |
| 7 Regeln (pro Kind / alle, Suche, Bearbeiten, Löschen, neu per Link oder Suche) | 15, 16 |
| 7 Verlauf mit Freigeben/Sperren je Eintrag | 17 |
| 7 Statistik (Tag/Woche, Top-Kanäle, Anteil Belohnung) | 18 |
| 7 Belohnungsmodus (Dauer/Endzeit, auch Gesperrtes, sofort beenden) | 19 |
| 7 Kind-Einstellungen (compact/full, Shorts, QR-Kopplung, Geräte, Entkoppeln) | 20 |
| 7 Eltern anlegen, eigene Mail-Einstellungen | 21 |
| 7 Benachrichtigungstabelle (Push-Teil, Aktionen Freigeben = nur dieses Kind) | 7, 22 |
| 8 Push über ntfy: Vordergrunddienst, Dauerverbindung, Akku-Ausnahme, ausblendbarer Kanal, nur IDs/kurze Texte | 6, 7, 22 |
| 10 Fehlerbehandlung: Push kommt nicht an → 15-Minuten-Abgleich; Fehlermeldungen statt Absturz | 4 (`userMessage`), 22 |
| 12 Tests: Gate-Tabelle in Kotlin, Unit-Tests, UI-Tests | 2, alle; UI per Robolectric statt Emulator (Festlegung 13) |
| 11 Betrieb | nicht betroffen (Server unverändert) |

Nicht in Plan 2 (bewusst): Kinder-App, Schauzeit-Zähler, SponsorBlock/RYD, Kommentare, Gate-Durchsetzung im Player (Plan 3); signierte Releases, Self-Update, täglicher Extractor-Check (Plan 4).

## Zwischenstände an Jessica

Nach Task 9 (`core` komplett, inkl. Ergebnis Live-Test), nach Task 16 (Anfragen und Regeln), nach Task 21 (alle Bildschirme) und nach Task 24 (Abnahme) — jeweils kurz, mit Teststand und offenen Punkten.
