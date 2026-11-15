# Nightcorefloating.

This repository packages the NightcoreFloating Android project in `NightcoreFloating.zip`. GitHub Actions extracts the project, builds a debug APK, uploads an artifact, and creates a GitHub Release.

## Start a build

1. Open the repository's **Actions** tab.
2. Select **Build APK**.
3. Choose **Run workflow** for a manual build.
4. Wait for the workflow to finish.
5. Download the **NightcoreFloating-APK** artifact from the run summary.

A push to `main` also triggers the workflow when `NightcoreFloating.zip` or `.github/workflows/build-apk.yml` changes.

## Required archive layout

The source archive must contain an Android Gradle project with either:

- `NightcoreFloating/settings.gradle`, or
- `settings.gradle` at the archive root.

The workflow extracts the archive into `project/`, detects the project directory, and runs `gradle assembleDebug`. The application module must produce an APK under `app/build/outputs/apk/debug/`.

## CI toolchain

The checked-in workflow currently uses:

- Ubuntu GitHub-hosted runner.
- Eclipse Temurin Java 17.
- Android SDK platform 34.
- Android Build Tools 34.0.0.
- Gradle 8.2.1.

## Published outputs

- Actions artifact: `NightcoreFloating-APK`.
- Artifact retention: 30 days.
- GitHub Release tag: `v6.<run number>`.
- Release asset: generated debug APK.

The generated APK is a debug build intended for testing, not a production Play Store release.

## Troubleshooting

**Missing ZIP**  
Confirm that `NightcoreFloating.zip` exists in the repository root with the exact filename.

**Gradle project not found**  
Inspect the archive and ensure that `settings.gradle` is at the root or inside `NightcoreFloating/`.

**No APK artifact**  
Inspect the Gradle build step first. The upload step expects at least one APK in the debug output directory; it fails when no APK is generated.

**SDK setup fails**  
Review the SDK manager output in the Actions log and confirm the runner can install Android platform 34 and Build Tools 34.0.0.

## Release notes

The workflow generates release notes automatically. Review the release contents before sharing the APK with other users.
