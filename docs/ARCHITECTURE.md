# Architecture

Waypad Android is a single-module Kotlin/Compose app. It is intentionally small enough for MVP delivery while preserving clean boundaries around UI, storage, and networking.

## Layers

| Package | Responsibility |
| --- | --- |
| `core.model` | Shared UI and protocol-facing model types. |
| `core.network` | UDP discovery, handshake crypto, encrypted frames, command client. |
| `core.storage` | Android Keystore protected trusted-host storage. |
| `ui` | Compose theme and screens. |
| root package | Activity and ViewModel orchestration. |

## Screens

- Onboarding.
- Discovery and manual IP entry.
- Pairing code and fingerprint confirmation.
- Touchpad remote.
- Keyboard and shortcut controls.
- Media/system controls.
- Settings.
- Trusted hosts.
- Troubleshooting diagnostics.

## Networking

The app uses a long-lived TCP socket for low latency interactive control. It does not use HTTP polling. UDP discovery is optional; manual IP entry works when broadcast is blocked.

## Storage

Trusted hosts are serialized as JSON and encrypted before entering `SharedPreferences`. The AES key is generated inside `AndroidKeyStore` with GCM mode and randomized IVs.

## Wayland Reality

The Android app does not pretend input is always available. It displays the daemon capability model and exposes "Approve portal" so the Linux user can grant RemoteDesktop portal permission locally. If Hyprland or the portal stack cannot provide RemoteDesktop, the app remains connected but input commands show the daemon's unsupported reason.
