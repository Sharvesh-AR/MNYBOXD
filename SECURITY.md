# Security

Spendly is a personal finance app, so it is designed around one rule: **your financial records
stay on your phone, encrypted, and nothing leaves it unless you explicitly export it.**

This document explains how that is achieved, what Spendly does *not* protect against, and how to
report a problem. It is not a certification: Spendly has had no external audit or penetration
test, and no software is free of vulnerabilities. The latest internal review is in
[`docs/SECURITY_AUDIT.md`](docs/SECURITY_AUDIT.md).

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

* Use GitHub's private reporting: the repository's **Security** tab → **Report a vulnerability**.
* Include the app version (Settings → About), Android version, device, and steps to reproduce.
* Do not include real financial data, backup files or backup passwords in a report.

You can expect an acknowledgement within 7 days. Fixes are released as a new signed APK on the
GitHub Releases page, with the issue described in the release notes once users have had time to
update.

## Architecture at a glance

| Area | Approach |
|---|---|
| Network | **No `INTERNET` permission.** The app cannot make network requests. A restrictive Network Security Config (cleartext disabled, system CAs only) is kept as defence in depth. |
| Accounts / sign-in | None. No server, no cloud sync, no bank or UPI integration. Spendly never moves money. |
| Database | Room on **SQLCipher 4.20.0** (AES-256, per-page HMAC). The whole database file is encrypted. |
| Database key | 256 random bits from `SecureRandom`, wrapped with an **AES-256-GCM key held in Android Keystore** (non-exportable; hardware-backed where the device supports it). Only the wrapped key is written to disk, in `noBackupFilesDir`. Never stored in SharedPreferences, DataStore or source code; never logged. |
| Preferences | Jetpack DataStore holds only non-sensitive UI settings (theme, last used account id/payment method, onboarding flags). |
| Backups | Android Auto Backup and device-to-device transfer are **disabled** for all app data. Users create password-encrypted backup files explicitly. |
| Exports | Encrypted backup (`.spendly`) or plain CSV (with an explicit warning). Files are written only to locations the user picks with the Storage Access Framework. |
| Logging | The app never logs. R8 strips `Log.v/d/i` calls from libraries in release builds. No crash-reporting or analytics SDK is included. |
| Debug tooling | Sample-data tools exist only in the `debug` source set and are not compiled into release builds. Release builds are not debuggable. |

## Data storage and encryption

### What is stored

Accounts (name, optional institution, type, opening balance, optional credit limit, colour),
categories, budgets and transactions (amount, date, category, account, payment method/app,
optional note). Money is stored as integer **paise** (`Long`), never floating point.

### What is never stored or requested

Passwords, UPI PINs, OTPs, CVVs, card numbers (full or partial), bank logins, or any identity
information. The account form explicitly tells users not to enter such data.

### Database encryption details

* SQLCipher is opened with a raw 256-bit key (`x'…'` form), so no weak passphrase or
  key-derivation shortcut is involved.
* The Keystore wrapping key uses `AES/GCM/NoPadding` with Keystore-generated random IVs and
  associated data that binds the ciphertext to its purpose.
* The wrapped key file is written atomically (temp file + `fsync` + rename).
* If the Keystore key becomes unavailable (some device resets do this) the data cannot be
  decrypted. Spendly **never deletes data automatically**: it shows a recovery screen and only
  erases after the user types `ERASE`. Restoring an encrypted backup is the recovery path.
* Integrity rules are also enforced inside SQLite: foreign keys (`RESTRICT`), a unique
  idempotency key per transaction (prevents duplicate saves), and triggers that reject
  non-positive or out-of-range amounts, malformed transfers and debit-card-funded rows.
* All queries are Room-generated with bound parameters; search text is escaped for `LIKE`.
  No SQL is built by string concatenation.

### Encrypted backup format

```
"SPENDLYB" | version | kdf id | PBKDF2 iterations (u32) | salt (16 B) | nonce (12 B) | AES-256-GCM ciphertext + tag
```

* Key derivation: PBKDF2-HMAC-SHA256, 600,000 iterations, fresh random salt per export.
* Encryption: AES-256-GCM with a fresh random 96-bit nonce; the entire header is authenticated
  as associated data, so any modification of header or payload is detected.
* Payload: GZIP-compressed JSON of plain data classes (no polymorphism, no class names), so
  decoding cannot instantiate arbitrary types.
* Import is bounded (64 MB file, 256 MB decompressed, iteration count 100k–10M) to resist
  malformed or hostile files, and is a single file, so there is no archive extraction or path
  traversal surface.
* Every record is validated (schema version, ids, references, enums, amounts, transaction shape,
  refund totals) **before** anything is written. The user then sees a summary and must confirm
  "Replace my data". The replacement runs in one database transaction and rolls back fully on
  any error.
* Passwords are held as `CharArray` and zeroed after use where the platform allows.

Only standard platform cryptography (`javax.crypto`, Android Keystore) and SQLCipher are used.
No custom algorithms.

## Permissions

| Permission | Why | When |
|---|---|---|
| `RECORD_AUDIO` | Voice expense entry via Android's `SpeechRecognizer`. | Requested only when the user first taps the microphone, after an in-app explanation. Everything works if it is denied. |

Library-declared: `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is a signature-level permission
added by AndroidX Core to protect its own runtime receivers; it grants nothing to other apps.

Spendly does **not** request internet, location, contacts, SMS, phone, storage, notifications,
overlay, device-admin or any background permission.

### Optional accessibility service (volume-button shortcut)

**Only in the `volumeShortcut` build** (`Spendly-volume-shortcut.apk`, source in
`app/src/volumeShortcut/`). The standard build (`Spendly.apk`) declares no accessibility service.
Google Play Protect's
[enhanced fraud protection](https://developers.google.com/android/play-protect/warning-dev-guidance)
blocks installing any app that declares an accessibility service, a notification listener,
`RECEIVE_SMS` or `READ_SMS` from a browser, messaging app or file manager in the markets where it
is active, India among them ("App blocked to protect your device"). The check looks only at the
declaration, not at what the service can do. `ManifestPolicyTest` checks each build's merged
manifest so the standard build can't pick up any of the four by accident, including from a library.

`ButtonShortcutService` lets the user open Quick Add from any screen by pressing a volume button
two or three times. It is **off by default** and does nothing until the user switches it on in
Android's Accessibility settings, after an in-app explanation. Android requires an accessibility
service for this: no other public API lets an app see button presses outside its own screens.

Its configuration (`res/xml/button_shortcut_service.xml`) is as narrow as Android allows:

* **No accessibility events** (`accessibilityEventTypes` unset) and
  **`canRetrieveWindowContent="false"`**: it cannot read what is on screen or which app is open.
* The only capability is **key-event filtering**. The service reads the key code, action, repeat
  count and timestamp of each key press, compares them with the chosen volume-button pattern and
  stores nothing. Every other key is ignored. Android can't limit a key filter to particular
  keys, so with a hardware keyboard connected, its key presses also pass through `onKeyEvent`.
  They are discarded straight away: nothing about them is kept, logged or forwarded.
* It **never consumes** an event (`onKeyEvent` always returns `false`), so volume buttons, camera
  shutters and other key uses keep working unchanged and without delay. When the pattern matches,
  it restores the media and ringer volume to their levels from before the first press.
* It does nothing while the phone is ringing or in a call, and Android delivers no key events to
  it while the screen is off. On the lock screen, Quick Add opens only after the user unlocks
  (`requestDismissKeyguard`); Spendly never shows over the keyguard.
* `isAccessibilityTool="false"`: it is a convenience feature, not an assistive tool.
* With no `INTERNET` permission, nothing it sees could leave the device anyway.

Turning it off: Android Settings → Accessibility → *Spendly volume shortcut*.

### Exported components

| Component | Exported | Protection |
|---|---|---|
| `MainActivity` | Yes (launcher) | Only two fixed custom actions are honoured (open Quick Add / voice entry). Extras and data are ignored. Nothing is ever saved without user confirmation. |
| `QuickAddTileService` | Yes | Requires `BIND_QUICK_SETTINGS_TILE` (system only). Uses an immutable `PendingIntent`. |
| `ButtonShortcutService` (volumeShortcut build only) | Yes | Requires `BIND_ACCESSIBILITY_SERVICE` (system only). Inactive unless the user enables it in Accessibility settings. See above. |
| `QuickAddLauncherActivity` | Yes (launcher), **disabled by default** | Optional "Add expense" app-list entry the user can switch on in Settings. No UI; ignores its intent and opens Quick Add. Own empty task affinity. |
| `androidx.profileinstaller.ProfileInstallReceiver` | Yes (library) | Requires `android.permission.DUMP` (system/shell only). |
| Room `MultiInstanceInvalidationService`, AndroidX Startup provider | No | Not exported. |

## Voice input and privacy

* Speech is turned into text by the device's speech recognition service through the public
  `SpeechRecognizer` API. Spendly prefers the **on-device recognizer** (Android 12+) when
  available.
* Depending on the device and its settings, the system speech service (for example Google's)
  **may process audio online**. Spendly states this before first use and never assumes
  on-device processing.
* Spendly never records, stores or uploads audio itself; audio buffers are ignored.
* The recognized text is parsed **locally** by a deterministic parser. No AI or cloud API is
  used. The result only pre-fills the form; the user always reviews and taps Save.

## Screen privacy

Settings → Privacy → *Hide app contents* sets `FLAG_SECURE`, which blocks screenshots and hides
Spendly's contents in the recent-apps switcher. It is off by default.

## External links

The About and Settings screens show a credit link to the developer's website. Tapping it hands
the URL to the user's browser with a standard `ACTION_VIEW` intent; Spendly itself sends nothing.

## Dependency audit process

1. Use only free, maintained, permissively licensed libraries (see [`DEPENDENCIES.md`](DEPENDENCIES.md)).
   No ads, analytics, crash reporting, or network SDKs.
2. Pick the latest **stable** versions from official repositories, not blindly the newest
   pre-release, and verify compatibility by building and running the full test suite.
3. `gradle/verification-metadata.xml` pins SHA-256 checksums for every artifact Gradle
   resolves; the Gradle wrapper distribution is pinned by checksum too.
4. Check resolved runtime dependencies against [OSV.dev](https://osv.dev) before each release:
   generate the classpath with
   `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and query the OSV batch
   API (or run `osv-scanner`). Native code bundled in SQLCipher is checked separately
   (SQLite version via the library's strings).
5. Run Android Lint on debug and release builds of both flavors (`./gradlew lintStandardDebug
   lintVolumeShortcutDebug lintStandardRelease lintVolumeShortcutRelease`); the build fails on lint errors.
6. Inspect both release APKs: signature (`apksigner verify`), merged manifest, permissions,
   exported components, and that they are not debuggable. `Spendly.apk` must declare no
   `BIND_ACCESSIBILITY_SERVICE` or `BIND_NOTIFICATION_LISTENER_SERVICE` service and no SMS permission.
7. CI pins every GitHub Action to a full commit SHA and runs with read-only permissions, except
   the tag-triggered release job.

When updating dependencies, regenerate checksums with
`./gradlew --write-verification-metadata sha256 clean test lintStandardDebug lintVolumeShortcutDebug lintStandardRelease lintVolumeShortcutRelease assembleRelease bundleRelease`
and review the diff before committing.

## Known limitations

* **No app lock.** Anyone who can unlock your phone can open Spendly. Use a strong device lock;
  biometric app lock is on the roadmap.
* **Key loss means data loss.** If Android Keystore loses the key (some factory resets or OEM
  issues) or the app is uninstalled, local data is unrecoverable. Keep encrypted backups.
* **Backup strength depends on your password.** PBKDF2 (600k iterations) slows guessing but is
  not memory-hard. Use a long, unique passphrase.
* **CSV exports are plain text.** Treat them like a bank statement.
* **Speech services may be online.** See *Voice input and privacy*.
* **Rooted or compromised devices** are out of scope: malware with root can read app memory and
  may misuse Keystore keys while the device is unlocked.
* **Memory hygiene is best-effort.** Kotlin/JVM strings (notes, typed passwords in text fields)
  cannot be reliably wiped from memory.
* **No anti-tamper or obfuscation guarantees.** R8 shrinking is enabled for size, not as a
  security control.
