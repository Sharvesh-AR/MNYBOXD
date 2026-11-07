# Spendly

**A calm, private expense tracker for Android. Every rupee, accounted for.**

Spendly records spending in seconds, by keypad or voice, and keeps accurate balances across
your bank accounts, cash, UPI, debit cards and credit cards. It works completely offline, has
no account or sign-in, and stores everything in an encrypted database on your phone.

Free. No ads. No tracking. No internet permission.

<p>
  <img src="docs/screenshots/01-home.png" width="200" alt="Home dashboard">
  <img src="docs/screenshots/06-quick-add.png" width="200" alt="Quick Add with custom keypad">
  <img src="docs/screenshots/02-activity.png" width="200" alt="Transaction history">
  <img src="docs/screenshots/03-accounts.png" width="200" alt="Accounts and net worth">
</p>
<p>
  <img src="docs/screenshots/04-budgets.png" width="200" alt="Budgets">
  <img src="docs/screenshots/05-insights.png" width="200" alt="Insights">
  <img src="docs/screenshots/07-transaction-detail.png" width="200" alt="Transaction detail">
  <img src="docs/screenshots/08-detail-light.png" width="200" alt="Light theme">
</p>

<sub>Screens are rendered from the real app UI with fictional sample data that exists only in debug
builds (`ScreenshotTest`).</sub>

## Features

**Fast entry**
* **Quick Add**: a custom amount keypad, recently used categories first, remembered account and
  payment method, note, date and time. One tap to save; double-taps can't create duplicates.
* **Voice entry**: say *"Spent 500 on petrol using PhonePe from SBI"*, *"120 rupees lunch"*,
  *"1500 shopping on ICICI credit card"* or *"80 coffee cash"*. A deterministic on-device parser
  extracts amount, description, category, payment method, app and account. **You always review
  the result before it is saved.**
* **Quick Settings tile** and **launcher shortcuts** (*Add expense*, *Voice expense*).

**Accurate money**
* All amounts are integer paise (`Long`); floating point is never used for money.
* Expenses, income, transfers and refunds, with correct balance effects:
  * UPI from a bank account reduces that bank once ("Google Pay → HDFC → ₹500" is one expense).
  * Debit card payments come out of the linked bank account.
  * Credit card purchases increase the card's outstanding; paying the bill is a transfer from
    your bank to the card.
  * Bank-to-bank transfers are neither spending nor income.
  * Refunds reverse the original expense (partial refunds supported, never more than spent).
* Database-level integrity: foreign keys, unique idempotency keys, triggers rejecting invalid
  amounts, and atomic transactions.

**Understand your spending**
* **Home**: spent this month, today, income, remaining budget, 7-day chart, accounts, top
  categories, recent activity.
* **Activity**: search notes, categories, accounts or amounts; filter by type, category,
  account and date; grouped by day with daily totals.
* **Accounts**: savings, current, cash, wallets, debit and credit cards; net worth, assets and
  card dues; credit limit usage; archive or delete.
* **Budgets**: monthly overall and per-category limits with 80% and 100% warnings (also shown
  right after saving an expense that crosses a threshold).
* **Insights**: monthly spent, income, saved and daily average; comparison with last month;
  daily, weekly and 6-month charts; category and payment-method breakdowns. All from your real
  records.

**Your data, your control**
* Encrypted database (SQLCipher, key protected by Android Keystore).
* Password-encrypted backups and restore (validated, previewed, confirmed, all-or-nothing).
* CSV export for spreadsheets (with a clear "this file is unencrypted" warning).
* Dark theme by default, light and system themes, optional screenshot blocking.

**Design and accessibility**
* Origin-aware motion: Quick Add grows out of the button you pressed; accounts and transactions
  morph into their detail screens; dialogs expand from the control that opened them and
  collapse back into it. Respects Android's *Remove animations* setting.
* Haptics on keypad, selection, save and errors.
* Screen reader labels for amounts, keypad, charts and rows; large touch targets; tabular
  figures for aligned amounts.

## Install on your phone

1. On your phone, open the repository's **Releases** page and download the latest
   `spendly-vX.Y.Z.apk`. (Optionally compare its SHA-256 with `SHA256SUMS.txt`.)
2. Open the downloaded file. If Android asks, allow your browser or file manager to
   **install unknown apps** (Settings → Apps → *your browser* → Install unknown apps).
3. Tap **Install**, then **Open**.
4. You can switch the "install unknown apps" permission off again afterwards.

Requires Android 8.0 (API 26) or newer.

To install from a computer instead: enable *Developer options → USB debugging*, connect the
phone and run `adb install app-release.apk`.

> **Back up regularly.** Spendly has no cloud sync. If you uninstall the app or reset your phone,
> your records are gone unless you made an encrypted backup (Settings → Backup & export).

## Build from source

Requirements: JDK 17 or 21, Android SDK with platform 37 (`platforms;android-37.0`) and
build-tools 36.0.0. The Gradle wrapper downloads Gradle 9.9.0 and verifies its checksum.

```bash
git clone <this repository>
cd spendly
echo "sdk.dir=$ANDROID_HOME" > local.properties     # if ANDROID_HOME isn't set for Gradle
./gradlew clean test lint lintRelease assembleRelease
```

* Debug build (installs side by side as "io.github.sharveshar.spendly.debug"):
  `./gradlew installDebug`
* Signed release APK: `app/build/outputs/apk/release/app-release.apk`
  (if signing isn't configured, Gradle produces `app-release-unsigned.apk` instead).
* Screenshots: `SPENDLY_SCREENSHOT_DIR=$PWD/docs/screenshots ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'`

### Release signing

Signing credentials are **never** stored in the repository. Gradle reads them from environment
variables or from your user-level `~/.gradle/gradle.properties`:

```properties
SPENDLY_KEYSTORE_FILE=/absolute/path/to/spendly-release.p12
SPENDLY_KEYSTORE_PASSWORD=...
SPENDLY_KEY_ALIAS=spendly
SPENDLY_KEY_PASSWORD=...
```

Create a keystore once (keep it **and** its password safe; you need the same key for every
future update):

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore ~/spendly-release.p12 \
  -alias spendly -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Spendly"
chmod 600 ~/spendly-release.p12
```

### Publish a release on GitHub

The workflow in `.github/workflows/android.yml` tests, lints and (when secrets exist) builds and
verifies a signed APK on every push. Pushing a tag `vX.Y.Z` that matches `versionName` publishes
a GitHub Release with the APK and its SHA-256.

1. Add repository secrets (Settings → Secrets and variables → Actions):
   * `SPENDLY_KEYSTORE_BASE64`: output of `base64 -w0 ~/spendly-release.p12`
   * `SPENDLY_KEYSTORE_PASSWORD`, `SPENDLY_KEY_ALIAS`, `SPENDLY_KEY_PASSWORD`
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit and push.
3. Tag and push: `git tag v1.0.0 && git push origin v1.0.0`.

Without signing secrets the workflow still tests and lints, but never creates a release.

Prefer to publish manually? Build locally, then
`gh release create v1.0.0 app/build/outputs/apk/release/app-release.apk --generate-notes`.

### Before publishing a release: device check

Automated tests run on the JVM (Robolectric). Before each public release, install the signed APK
on a real phone and check: first launch and onboarding; adding an expense, income, transfer and
refund; balances on Accounts; voice entry (allow and deny the microphone); creating and restoring
an encrypted backup; the Quick Settings tile; light/dark theme; and a large font size.

## Technology

Kotlin 2.4 · Jetpack Compose (Material 3) · MVVM with repositories · Room 2.8 on SQLCipher 4.20 ·
Coroutines/Flow · DataStore · Navigation Compose (type-safe) · kotlinx.serialization ·
Android `SpeechRecognizer` · Gradle Kotlin DSL with version catalog · AGP 9.5 · minSdk 26,
targetSdk 37.

```
app/src/main/java/io/github/sharveshar/spendly/
├── core/money/        Money: paise parsing/formatting, keypad input
├── domain/            Pure Kotlin rules: ledger & validation, insights & budgets, voice parser
├── data/              Room entities/DAOs, SQLCipher key manager, repositories, backup, prefs
├── ui/                Compose screens, components, motion, theme, navigation
├── MainActivity.kt    Single activity, intent allow-list, shortcuts
└── QuickAddTileService.kt
```

Testing: 92 automated tests covering balance rules (including a randomized property test that
checks SQL balances against a reference calculator), validation, paise precision, duplicate
prevention, voice parsing, insights and budgets, database constraints and triggers, migrations,
encrypted backup round-trip and tamper detection, CSV injection, the Quick Add form logic (voice pre-fill, edit, refund, process-death restore),
and Compose UI flows (navigation, Quick Add, validation, empty states, accessibility labels).

See [`DEPENDENCIES.md`](DEPENDENCIES.md) for every library, version and licence.

## Security and privacy

* No internet permission, no accounts, no analytics, no ads, no paid services, no API keys.
* Encrypted at rest; Android cloud backup disabled; you choose when and where to export.
* Never asks for passwords, PINs, OTPs, CVVs or card numbers.
* Voice uses your phone's speech service, which may work online on some devices. Spendly tells
  you before first use and never stores audio.

Details and limitations: [`SECURITY.md`](SECURITY.md). Latest review:
[`docs/SECURITY_AUDIT.md`](docs/SECURITY_AUDIT.md).

## Roadmap

* Biometric / PIN app lock
* Recurring transactions and bill reminders (local notifications)
* Custom date range filter and export ranges
* Multiple currencies
* Home-screen widget
* Translations (strings are currently English-only)
* Bundled premium typeface (currently the system font with tabular figures)

## Credits

Built with ♥ by **ARS** · [ar-sharvesh.vercel.app](https://ar-sharvesh.vercel.app)
