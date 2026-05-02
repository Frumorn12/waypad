# Architecture

Waypad Android is a single-module Kotlin/Compose app. It is intentionally small enough for MVP delivery while preserving clean boundaries around UI, storage, and networking.

## Layers

| Package | Responsibility |
| --- | --- |
| `core.model` | Shared UI and protocol-facing model types. |
| `core.externalinput` | Android `InputDevice`, `KeyEvent`, and `MotionEvent` classification and normalization. |
| `core.network` | UDP discovery, handshake crypto, encrypted frames, command client, and screen frame stream client. |
| `core.screen` | Aspect-ratio-aware coordinate mapping from phone surface to host source coordinates. |
| `core.storage` | Android Keystore protected trusted-host storage. |
| `ui` | Compose theme and screens. |
| root package | Activity and ViewModel orchestration. |

## Screens

- Onboarding.
- Discovery and manual IP entry.
- Pairing code and fingerprint confirmation.
- Touchpad remote.
- Remote screen viewer and controller.
- Keyboard and shortcut controls.
- Media/system controls.
- Settings.
- Trusted hosts.
- Troubleshooting diagnostics.

## Networking

The app uses a long-lived TCP socket for low latency interactive control. It does not use HTTP polling. UDP discovery is optional; manual IP entry works when broadcast is blocked.

Remote screen mode negotiates a stream over the encrypted control channel, then opens a second short-token-protected TCP frame stream back to the daemon's stable control port. The stream socket starts with a single `stream_connect` JSON line and then switches to `WAYPAD_STREAM_V1` JPEG frames. Using the existing control port avoids the random high-port failures that show up on phones as `connection closed`, `broken pipe`, or connect timeouts when a LAN firewall blocks dynamic stream ports. The Android UI decodes frames into a Compose `Image` and keeps input delivery on the existing coalesced command queue.

External Android input devices are normalized before they reach networking code. `InputDevice` source flags classify keyboard, mouse, touchpad, gamepad, and joystick devices. `KeyEvent` values are mapped to XKB keysyms for the host. Mouse movement, buttons, and wheel events become `external_input` protocol events and are coalesced through the same backpressure-aware input queue as touch gestures. Controller buttons and axes are normalized with deadzones, but the app checks host capability before forwarding because generic gamepad injection is not available on the current Wayland backends.

The current MVP does not bundle WebRTC. That avoids adding a partial signaling/media stack before the daemon has stable source selection and portal capability negotiation. The stream protocol is intentionally isolated so WebRTC/H.264 can replace it later.

## Storage

Trusted hosts are serialized as JSON and encrypted before entering `SharedPreferences`. The AES key is generated inside `AndroidKeyStore` with GCM mode and randomized IVs.

## Wayland Reality

The Android app does not pretend input is always available. It displays the daemon capability model and exposes "Approve portal" so the Linux user can grant RemoteDesktop portal permission locally. If Hyprland or the portal stack cannot provide RemoteDesktop, the app remains connected but input commands show the daemon's unsupported reason.

Remote screen taps are mapped through `ScreenViewport`, which accounts for contain-fit scaling and letterboxing. Touches in black bars are ignored instead of being sent to the wrong desktop coordinate.

Remote screen mode temporarily switches the activity orientation policy to sensor-driven rotation so landscape desktop sharing is usable from the phone. Other app screens return to portrait. Fullscreen is UI state only: entering or exiting fullscreen hides/shows system bars and rearranges controls, but it does not intentionally stop the active stream session. Stream reconnect and manual stop remain explicit ViewModel operations.
