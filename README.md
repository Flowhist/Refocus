# Refocus

Android 16 personal focus monitor for vivo X80.

The confirmed behavior is documented in [SPEC.md](SPEC.md).

## Open in Android Studio

1. Install the current stable Android Studio with Android SDK Platform 36.1 and JDK 17.
2. Open this directory as a Gradle project.
3. Let Android Studio create/download the Gradle 8.13 wrapper if prompted.
4. Build the `app` debug variant.

The project uses Android Gradle Plugin 8.13.0 and compiles against Android API 36.1.

## Updates

Versions are built and published from the GitHub Actions page for installation
through Obtainium. Signing and first-time setup are documented in
[docs/UPDATES.md](docs/UPDATES.md).

## Current scoring rules

- Leaving a monitored app before the planned duration, or during the five-second grace period, opens a completion prompt.
- “Completed” scores `+1`; “Not completed” scores `0`.
- Remaining in the monitored app after the grace period scores `-1` once.
- Locking the phone pauses the session.
- Switching to another app ends the current session.
- Split screen, floating window, and picture-in-picture still count as using the monitored app.

## Privacy

Refocus stores data locally and observes package names only. It does not inspect
accessibility node text or transmit usage records. While a prompt is visible,
Refocus may hold a temporary screen image in memory to render the frosted-glass
background. It is recycled when the prompt closes and is never saved or sent.
