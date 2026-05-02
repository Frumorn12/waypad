# Security Policy

Waypad for Android stores host trust pins and daemon session tokens locally. Treat bug reports affecting pairing, encrypted transport, host identity validation, or token storage as security issues.

## Supported Versions

The app is pre-1.0. Security fixes target the main development branch until formal releases exist.

## Reporting Vulnerabilities

Open a private advisory if available, or contact the repository owner directly. Include:

- App commit or APK version.
- Android version and device model.
- Whether the issue affects discovery, pairing, encrypted transport, host pinning, token storage, or command authorization.
- Steps to reproduce.

## App Security Model

- The app does not trust UDP discovery by itself.
- Pairing establishes trust in the daemon host key and stores the host fingerprint.
- Subsequent connections refuse changed host fingerprints.
- Trusted host records, including daemon session tokens, are encrypted using Android Keystore backed AES-GCM.
- The app sends commands only through the encrypted Waypad channel.
- The app has no cloud backend and no account system.

## Known MVP Risks

- Manual pairing requires the user to compare the fingerprint shown by `waypad-daemon pair-code`.
- The protocol should receive external security review before broad distribution.
- Device compromise can expose an active trusted session token. Revoke lost devices on the host with `waypad-daemon devices revoke <device-id>`.
