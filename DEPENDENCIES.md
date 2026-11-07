# Dependency inventory

Spendly has **no paid, proprietary, advertising, analytics or network SDKs**. Every runtime
dependency is free and open source under a permissive licence. Nothing requires an API key.

## Direct dependencies

| Library | Version | Licence | Purpose |
|---|---|---|---|
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | Kotlin extensions, shortcuts |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | Compose host activity, permission/SAF launchers |
| AndroidX Lifecycle (runtime-compose, viewmodel-compose, viewmodel-savedstate) | 2.10.0 | Apache-2.0 | ViewModels, lifecycle-aware state, process-death restore |
| AndroidX Navigation Compose | 2.10.2 | Apache-2.0 | Type-safe navigation |
| AndroidX DataStore Preferences | 1.2.1 | Apache-2.0 (bundled protobuf: BSD-3-Clause) | Non-sensitive UI preferences |
| Jetpack Compose BOM (UI, Foundation, Animation, Material 3, Material Icons Extended) | 2026.09.00 | Apache-2.0 | UI toolkit, design system, icons |
| AndroidX Room (runtime, ktx; compiler via KSP) | 2.8.5 | Apache-2.0 | Database layer, migrations |
| AndroidX SQLite KTX | 2.6.2 | Apache-2.0 | SupportSQLite helpers |
| SQLCipher for Android (Community Edition) | 4.20.0 | BSD-style | Encrypted database (bundles SQLite 3.54.1 and LibTomCrypt, both public domain) |
| kotlinx.coroutines (android) | 1.11.0 | Apache-2.0 | Asynchronous work off the main thread |
| kotlinx.serialization (json) | 1.12.0 | Apache-2.0 | Backup format, navigation routes, saved form state |

## Build and test only (not shipped in the APK)

| Tool | Version | Licence | Purpose |
|---|---|---|---|
| Android Gradle Plugin | 9.5.1 | Apache-2.0 | Build |
| Kotlin Gradle Plugin / Compose compiler / serialization plugin | 2.4.21 | Apache-2.0 | Compilation |
| KSP | 2.3.13 | Apache-2.0 | Room annotation processing |
| Gradle | 9.9.0 | Apache-2.0 | Build system (wrapper checksum-pinned) |
| JUnit | 4.13.2 | EPL-1.0 | Unit tests |
| Robolectric | 4.17 | MIT | JVM tests of Android/Compose code |
| AndroidX Test core / ext-junit | 1.7.0 / 1.3.0 | Apache-2.0 | Test utilities |
| Room testing, Compose UI test | 2.8.5 / BOM | Apache-2.0 | Migration and UI tests |
| kotlinx-coroutines-test | 1.11.0 | Apache-2.0 | Coroutine tests |

## How this list is checked

* Versions were chosen from the official Google Maven and Maven Central metadata at build time
  (latest stable releases, no alpha/beta/RC).
* Licences below were read from each artifact's POM (`<licenses>`), not assumed.
* `gradle/verification-metadata.xml` pins SHA-256 checksums for every resolved artifact; the
  build fails if any artifact changes.
* Known vulnerabilities were checked against [OSV.dev](https://osv.dev) (see `docs/SECURITY_AUDIT.md`).
* Full licence texts ship inside the app (About → Open-source licences).

## Full resolved runtime classpath (release)

138 artifacts.

| Group | Artifact | Version | Licence |
|---|---|---|---|
| androidx.activity | activity | 1.13.0 | Apache-2.0 |
| androidx.activity | activity-compose | 1.13.0 | Apache-2.0 |
| androidx.activity | activity-ktx | 1.13.0 | Apache-2.0 |
| androidx.annotation | annotation | 1.10.0 | Apache-2.0 |
| androidx.annotation | annotation-experimental | 1.5.0 | Apache-2.0 |
| androidx.annotation | annotation-jvm | 1.10.0 | Apache-2.0 |
| androidx.arch.core | core-common | 2.2.0 | Apache-2.0 |
| androidx.arch.core | core-runtime | 2.2.0 | Apache-2.0 |
| androidx.autofill | autofill | 1.0.0 | Apache-2.0 |
| androidx.collection | collection | 1.5.0 | Apache-2.0 |
| androidx.collection | collection-jvm | 1.5.0 | Apache-2.0 |
| androidx.collection | collection-ktx | 1.5.0 | Apache-2.0 |
| androidx.compose | compose-bom | 2026.09.00 | Apache-2.0 |
| androidx.compose.animation | animation | 1.12.1 | Apache-2.0 |
| androidx.compose.animation | animation-android | 1.12.1 | Apache-2.0 |
| androidx.compose.animation | animation-core | 1.12.1 | Apache-2.0 |
| androidx.compose.animation | animation-core-android | 1.12.1 | Apache-2.0 |
| androidx.compose.foundation | foundation | 1.12.1 | Apache-2.0 |
| androidx.compose.foundation | foundation-android | 1.12.1 | Apache-2.0 |
| androidx.compose.foundation | foundation-layout | 1.12.1 | Apache-2.0 |
| androidx.compose.foundation | foundation-layout-android | 1.12.1 | Apache-2.0 |
| androidx.compose.material | material-icons-core | 1.7.8 | Apache-2.0 |
| androidx.compose.material | material-icons-core-android | 1.7.8 | Apache-2.0 |
| androidx.compose.material | material-icons-extended | 1.7.8 | Apache-2.0 |
| androidx.compose.material | material-icons-extended-android | 1.7.8 | Apache-2.0 |
| androidx.compose.material | material-ripple | 1.12.1 | Apache-2.0 |
| androidx.compose.material | material-ripple-android | 1.12.1 | Apache-2.0 |
| androidx.compose.material3 | material3 | 1.4.0 | Apache-2.0 |
| androidx.compose.material3 | material3-android | 1.4.0 | Apache-2.0 |
| androidx.compose.runtime | runtime | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-android | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-annotation | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-annotation-android | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-retain | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-retain-android | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-saveable | 1.12.1 | Apache-2.0 |
| androidx.compose.runtime | runtime-saveable-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-geometry | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-geometry-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-graphics | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-graphics-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-text | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-text-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-tooling-preview | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-tooling-preview-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-unit | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-unit-android | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-util | 1.12.1 | Apache-2.0 |
| androidx.compose.ui | ui-util-android | 1.12.1 | Apache-2.0 |
| androidx.concurrent | concurrent-futures | 1.1.0 | Apache-2.0 |
| androidx.core | core | 1.19.1 | Apache-2.0 |
| androidx.core | core-ktx | 1.19.1 | Apache-2.0 |
| androidx.core | core-viewtree | 1.0.0 | Apache-2.0 |
| androidx.customview | customview-poolingcontainer | 1.0.0 | Apache-2.0 |
| androidx.datastore | datastore | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-android | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-core | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-core-android | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-core-okio | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-core-okio-jvm | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-preferences | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-preferences-android | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-preferences-core | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-preferences-core-android | 1.2.1 | Apache-2.0 |
| androidx.datastore | datastore-preferences-external-protobuf | 1.2.1 | BSD-3-Clause |
| androidx.datastore | datastore-preferences-proto | 1.2.1 | Apache-2.0 |
| androidx.emoji2 | emoji2 | 1.4.0 | Apache-2.0 |
| androidx.graphics | graphics-path | 1.0.1 | Apache-2.0 |
| androidx.interpolator | interpolator | 1.0.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-common | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-common-java8 | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-common-jvm | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-livedata-core | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-process | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime-android | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime-compose | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime-compose-android | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime-ktx | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-runtime-ktx-android | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-android | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-compose | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-compose-android | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-ktx | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-savedstate | 2.11.0 | Apache-2.0 |
| androidx.lifecycle | lifecycle-viewmodel-savedstate-android | 2.11.0 | Apache-2.0 |
| androidx.navigation | navigation-common | 2.10.2 | Apache-2.0 |
| androidx.navigation | navigation-common-android | 2.10.2 | Apache-2.0 |
| androidx.navigation | navigation-compose | 2.10.2 | Apache-2.0 |
| androidx.navigation | navigation-compose-android | 2.10.2 | Apache-2.0 |
| androidx.navigation | navigation-runtime | 2.10.2 | Apache-2.0 |
| androidx.navigation | navigation-runtime-android | 2.10.2 | Apache-2.0 |
| androidx.navigationevent | navigationevent | 1.1.2 | Apache-2.0 |
| androidx.navigationevent | navigationevent-android | 1.1.2 | Apache-2.0 |
| androidx.navigationevent | navigationevent-compose | 1.1.2 | Apache-2.0 |
| androidx.navigationevent | navigationevent-compose-android | 1.1.2 | Apache-2.0 |
| androidx.profileinstaller | profileinstaller | 1.4.0 | Apache-2.0 |
| androidx.room | room-common | 2.8.5 | Apache-2.0 |
| androidx.room | room-common-jvm | 2.8.5 | Apache-2.0 |
| androidx.room | room-ktx | 2.8.5 | Apache-2.0 |
| androidx.room | room-runtime | 2.8.5 | Apache-2.0 |
| androidx.room | room-runtime-android | 2.8.5 | Apache-2.0 |
| androidx.savedstate | savedstate | 1.5.0 | Apache-2.0 |
| androidx.savedstate | savedstate-android | 1.5.0 | Apache-2.0 |
| androidx.savedstate | savedstate-compose | 1.5.0 | Apache-2.0 |
| androidx.savedstate | savedstate-compose-android | 1.5.0 | Apache-2.0 |
| androidx.savedstate | savedstate-ktx | 1.5.0 | Apache-2.0 |
| androidx.sqlite | sqlite | 2.7.0 | Apache-2.0 |
| androidx.sqlite | sqlite-android | 2.7.0 | Apache-2.0 |
| androidx.sqlite | sqlite-framework | 2.7.0 | Apache-2.0 |
| androidx.sqlite | sqlite-framework-android | 2.7.0 | Apache-2.0 |
| androidx.sqlite | sqlite-ktx | 2.7.0 | Apache-2.0 |
| androidx.startup | startup-runtime | 1.1.1 | Apache-2.0 |
| androidx.tracing | tracing | 1.2.0 | Apache-2.0 |
| androidx.versionedparcelable | versionedparcelable | 1.1.1 | Apache-2.0 |
| androidx.window | window | 1.5.0 | Apache-2.0 |
| androidx.window | window-core | 1.5.0 | Apache-2.0 |
| androidx.window | window-core-android | 1.5.0 | Apache-2.0 |
| com.google.guava | listenablefuture | 1.0 | Apache-2.0 (from guava-parent POM) |
| com.squareup.okio | okio | 3.9.1 | Apache-2.0 |
| com.squareup.okio | okio-jvm | 3.9.1 | Apache-2.0 |
| net.zetetic | sqlcipher-android | 4.20.0 | BSD-style (SQLCipher Community Edition License) |
| org.jetbrains | annotations | 23.0.0 | Apache-2.0 |
| org.jetbrains.kotlin | kotlin-stdlib | 2.4.21 | Apache-2.0 |
| org.jetbrains.kotlin | kotlin-stdlib-common | 2.4.21 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-coroutines-android | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-coroutines-bom | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-coroutines-core | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-coroutines-core-jvm | 1.11.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-serialization-bom | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-serialization-core | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-serialization-core-jvm | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-serialization-json | 1.12.0 | Apache-2.0 |
| org.jetbrains.kotlinx | kotlinx-serialization-json-jvm | 1.12.0 | Apache-2.0 |
| org.jspecify | jspecify | 1.0.0 | Apache-2.0 |
