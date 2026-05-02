# Contributing

Waypad Android is a Kotlin/Compose app for controlling the separate Linux `waypad-daemon` repository.

## Development Setup

Install Android Studio or command-line Android SDK, JDK 17 or newer, and Gradle 9.1 or newer.

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

## Design Direction

The app should feel premium, minimal, dark-first, high-contrast, and one-handed. Do not copy proprietary branding, assets, or trademarks.

## Engineering Rules

- Do not add plaintext token storage.
- Do not bypass host fingerprint validation.
- Keep protocol changes synchronized with `waypad-daemon/docs/PROTOCOL.md`.
- Prefer clear user-facing errors when the host reports unsupported Wayland capabilities.
- Keep the Android repo separate from the daemon repo.

## Testing

At minimum, run:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Manual integration testing needs a running daemon:

```bash
waypad-daemon pair-code
```
