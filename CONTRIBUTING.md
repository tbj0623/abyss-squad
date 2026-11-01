# Contributing

Thank you for helping improve Abyss Squad.

## Local build

- JDK 17 and Android SDK 35.
- Android Gradle project with a pure Java core (`core/src`) and Android client (`app/src`).
- Windows: run `gradlew.bat assembleDebug`. Unix: run `./gradlew assembleDebug`.
- Core regressions on Windows: `pwsh -File scripts/test.ps1`.
- The public repository intentionally contains **no release signing keys**. For local testing, use the debug APK.

## Changes

- Explain gameplay-facing changes and why they improve player decisions, readability or stability.
- Add a regression test for a bug fix when practical.
- Preserve deterministic simulation: use `RunRandom`, and serialize any new state that affects future gameplay in `Codec` with a compatible version branch.
- Do not submit third-party game artwork, music, copied text, or credentials without a clear license.

The project is released under the MIT License; contributions are accepted under that license.
