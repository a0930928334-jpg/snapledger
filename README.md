# SnapLedger

A local-first personal finance app for Android and Windows. Bank-statement screenshots are turned into structured JSON by a general-purpose AI (ChatGPT or Claude); SnapLedger then validates, aligns overlapping screenshots, deduplicates, reconciles against end-of-day balances, auto-categorises and reports.

> Status: early development (milestone M0: project skeleton). Not usable yet.

## Principles

- **The AI only transcribes; the app decides.** Deduplication, reconciliation and final categorisation are deterministic and unit-tested.
- **Local-first.** Data stays on your device. The MVP Android app does not even request the `INTERNET` permission.
- **Money is never a float.** Amounts are stored as integer minor units plus a currency code.

## Tech stack

Kotlin Multiplatform · Compose Multiplatform (Material 3) · SQLDelight · kotlinx.serialization · Koin. Exact versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Module | Purpose |
|---|---|
| `core` | Pure Kotlin domain model and import engine (no UI, DB or platform APIs) |
| `data` | SQLDelight schema, repositories, encrypted backups |
| `ui` | Shared Compose Multiplatform screens |
| `androidApp` | Android entry point |
| `desktopApp` | Windows / desktop JVM entry point and packaging |

## Build

Requirements: JDK 21, Android SDK with platform 37.

```bash
./gradlew build                       # compile, test, ktlint, detekt, Android lint
./gradlew :desktopApp:run             # run the desktop app
./gradlew :androidApp:installDebug    # install on a connected device / emulator
```

Enable the secret-scanning pre-commit hook once per clone (requires [gitleaks](https://github.com/gitleaks/gitleaks)):

```bash
git config core.hooksPath .githooks
```

## Documentation

Design documents (in Chinese) are in [`docs/`](docs/README.md); architecture decisions are in [`docs/adr/`](docs/adr/README.md). The import format is defined by [`schemas/import-v1.schema.json`](schemas/import-v1.schema.json), with fictional examples in [`samples/`](samples/).

## License

[MIT](LICENSE)
