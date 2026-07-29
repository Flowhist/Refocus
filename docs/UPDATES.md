# Refocus updates

Refocus can be distributed through GitHub Releases and tracked by Obtainium.

## Why signing matters

Android accepts an APK as an update only when its application ID and signing
certificate match the installed app. Refocus 0.1.0 was installed from a local
debug build, so the GitHub workflow must be given that same signing keystore
before its APK can update the existing installation without clearing data.

Do not commit a keystore or password to Git.

## One-time GitHub setup

Create these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64 text of the keystore used for 0.1.0.
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

The currently installed 0.1.0 used the default keystore from the original
Windows development account. Configure the secrets yourself so the private key
never appears in source control or chat.

## Publish a version

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push the changes.
3. Open **Actions > Release Android APK > Run workflow**.
4. Enter a new release tag, for example `v0.3.0`.
5. GitHub Actions runs unit tests, builds a signed APK, and attaches it to a
   GitHub Release.

## Obtainium setup

1. Install Obtainium on the phone.
2. Add the GitHub repository URL as an app source.
3. Allow Obtainium to install unknown apps when Android asks.
4. Keep prerelease updates disabled unless testing an unfinished build.

Obtainium detects the newest GitHub Release, downloads its APK, and hands it to
Android's package installer. Android still asks for final installation
confirmation.

For a private repository, configure GitHub authentication in Obtainium. A
public repository needs no phone-side GitHub token.
