# Security audit — Spendly 1.0.0

* **Date:** 2026-11-08
* **Scope:** complete source tree, build configuration, CI workflow, and the signed release APK
  `app/build/outputs/apk/release/app-release.apk` (versionCode 1, versionName 1.0.0).
* **Framework:** OWASP MASVS v2 controls, with OWASP MASTG as the testing reference.
* **Nature:** an internal self-review using automated tools and manual inspection. **This is not
  an independent audit or certification**, and it does not claim the app is free of
  vulnerabilities.

> **Changes since this audit.** Later versions add an opt-in volume-button shortcut
> (`ButtonShortcutService`, an accessibility service limited to key-event filtering) and an
> optional, disabled-by-default "Add expense" launcher entry (`QuickAddLauncherActivity`).
> Neither is covered by the results below; see [SECURITY.md](../SECURITY.md#optional-accessibility-service-volume-button-shortcut)
> for their design. The permission set is unchanged (`RECORD_AUDIO` only).

## Checks performed and results

| # | Check | Tool / method | Result |
|---|---|---|---|
| 1 | Unit, database, backup and UI tests | `./gradlew test` (JUnit, Robolectric, Compose UI test) | 92 passed, 0 failed (+1 opt-in screenshot test skipped) |
| 2 | Static analysis | Android Lint, debug + release (`lint lintRelease`, `abortOnError`) | No issues after fixes (7 findings fixed, see below) |
| 3 | Known-vulnerable dependencies | OSV.dev batch API over all 138 resolved release runtime artifacts | 0 known vulnerabilities |
| 4 | Native code in SQLCipher | `strings` on `libsqlcipher.so` | SQLite 3.54.1 (built 2026-10-16), LibTomCrypt crypto provider |
| 5 | Dependency integrity | Gradle dependency verification (SHA-256, 512 components) + wrapper checksum | Build passes with verification enforced |
| 6 | Dependency freshness | Google Maven / Maven Central metadata | All direct dependencies on latest stable releases at audit time |
| 7 | Licences | POM `<licenses>` for every runtime artifact; SQLCipher licence page | All permissive (Apache-2.0, BSD-3-Clause, BSD-style); full texts bundled in app |
| 8 | Secret scanning | Regex sweep for keys/tokens/passwords/private keys; search for keystore files; `.gitignore` review | No secrets in tree (one obvious test-fixture password in `BackupTest`). Signing keystore lives outside the repo |
| 9 | Release APK signature | `apksigner verify --print-certs` | v2 + v3 signatures valid |
| 10 | Debuggable release | `aapt2 dump badging` / merged manifest | Not debuggable |
| 11 | Permissions | Merged manifest | `RECORD_AUDIO` only (+ AndroidX signature-level internal permission). No `INTERNET` |
| 12 | Exported components | Merged manifest | Launcher activity (action allow-list), QS tile (system-only permission), ProfileInstaller (DUMP-protected). Nothing else exported |
| 13 | Backup configuration | Manifest + `data_extraction_rules.xml` | `allowBackup=false`, `fullBackupContent=false`, all domains excluded from cloud backup and device transfer |
| 14 | Cleartext traffic | Network Security Config | Cleartext disabled; app has no network permission |
| 15 | SQL injection | Code review | Only Room-generated parameterised queries; `execSQL` used only with constant DDL/PRAGMA; `LIKE` input escaped; test with injection-style input |
| 16 | Insecure crypto / weak RNG | Code review | AES-256-GCM, PBKDF2-HMAC-SHA256 (600k), Android Keystore, `SecureRandom` only. No ECB, no static IVs, no hardcoded keys |
| 17 | Sensitive logging | Code review + R8 rule | No logging in app code; `Log.v/d/i` stripped in release |
| 18 | Unsafe file handling / imports | Code review + tests | SAF only; bounded reads; authenticated decryption; strict schema validation; atomic restore with rollback (tested) |
| 19 | WebView / dynamic code | Code search | None |
| 20 | PendingIntents / intents | Code review | One `PendingIntent`, `FLAG_IMMUTABLE`; intent extras never read |
| 21 | R8 / minification safety | `apkanalyzer` on release dex with mapping | Navigation, backup and form-state serializers, `SpendlyDatabase_Impl`, enum lookups and SQLCipher JNI methods retained |
| 22 | CI workflow | Manual review | Actions pinned to commit SHAs; `contents: read` by default; secrets used only in signing steps; keystore removed after use; no release without a signed APK |

### Lint findings fixed during the audit

| Finding | Fix |
|---|---|
| `UnusedBoxWithConstraintsScope` (amount display) | Replaced with `Box` |
| `UnusedContentLambdaTargetStateParameter` (Insights summary) | Removed incorrect `AnimatedContent` |
| `AutoboxingStateCreation` | `mutableIntStateOf` |
| `UseOfNonLambdaOffsetOverload` (segmented control) | Lambda `offset {}` |
| `ReportShortcutUsage` | `ShortcutManagerCompat.reportShortcutUsed` |
| `UseKtx` | `String.toUri()` |
| `LocalContextResourcesRead` (licence notices) | `LocalResources.current` |

### Other issues found and fixed during development review

* **Root UI surface missing:** default content colour fell back to black, making some text
  unreadable. Found by rendering screens; fixed with a themed root `Surface`.
* **Search debounce delayed the initial list** by 180 ms: empty queries now apply immediately.
* **Licence attribution incomplete:** SQLCipher's licence requires in-app reproduction; full
  texts are now bundled and shown in About.

## MASVS summary

| Group | Status | Notes |
|---|---|---|
| MASVS-STORAGE | Addressed | Encrypted DB; no sensitive data in prefs/logs/backups; exports are user-initiated |
| MASVS-CRYPTO | Addressed | Standard primitives, Keystore-protected keys, random nonces/salts |
| MASVS-AUTH | Partially (by design) | No remote auth. No local app lock yet (see L1) |
| MASVS-NETWORK | Addressed | No network access at all |
| MASVS-PLATFORM | Addressed | Minimal exported surface, immutable PendingIntent, no WebView, optional FLAG_SECURE |
| MASVS-CODE | Addressed | Current dependencies, no known CVEs, input validation, checksum-verified build |
| MASVS-RESILIENCE | Not in scope | No anti-tamper/root detection; not appropriate for a free offline app |
| MASVS-PRIVACY | Addressed | No data collection; voice processing disclosed; minimal permissions |

## Remaining findings (none critical or high)

| ID | Severity | Finding | Impact | Mitigation / plan |
|---|---|---|---|---|
| M1 | Medium | **Release APK not yet launched on a device or emulator.** Local disk space prevented running an emulator; behaviour was verified with JVM/Robolectric tests, rendered screens and static APK inspection. | A device-specific or minification-related runtime problem could remain undetected. | Install and smoke-test on a real phone before publishing (checklist in README). |
| M2 | Medium | Keystore key loss makes the local database unrecoverable. | Data loss after some resets or OEM Keystore bugs. | Recovery screen (no silent deletion), encrypted backups, guidance in About. |
| L1 | Low | No biometric/PIN app lock. | Someone with an unlocked phone can view records. | Rely on device lock; optional FLAG_SECURE; app lock on roadmap. |
| L2 | Low | Keystore key not bound to unlocked-device state (`setUnlockedDeviceRequired`), for OEM compatibility. | Malware running as the app while the phone is locked could unwrap the key. Requires prior code execution in the app's sandbox. | Revisit when OEM support is reliable. |
| L3 | Low | PBKDF2 (not memory-hard) for backup passwords. | Weak passwords on stolen backup files could be brute-forced. | 600k iterations, 8-char minimum, guidance to use a passphrase. |
| L4 | Low | CSV export is plaintext. | Exposure if the file is shared or stored carelessly. | Explicit warning before export; encrypted backup recommended. |
| L5 | Low | System speech service may process audio online. | Spoken phrases may reach the speech provider. | Disclosure before first use; on-device recognizer preferred; manual entry always available. |
| L6 | Low | Sensitive strings may persist in memory until GC. | Only exploitable with memory access on a compromised device. | Passwords use `CharArray` where possible. |
| L7 | Low | Unit tests run against the debug variant only. | Release-only code differences (none currently, other than `DebugTools`) aren't unit-tested. | Release APK statically inspected; lint runs on release. |
| I1 | Info | Exported `MainActivity` accepts two custom actions from any app. | Another app can open Spendly's Quick Add form. | No data is read from the intent and nothing is saved without the user. |

## Reproducing this audit

```bash
./gradlew clean test lint lintRelease assembleRelease
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | grep -E "permission|debuggable"
./gradlew :app:dependencies --configuration releaseRuntimeClasspath   # then query https://api.osv.dev/v1/querybatch
```
