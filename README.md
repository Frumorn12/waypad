# Waypad Android

Waypad Android is the mobile client for Waypad, a secure Wayland-focused remote-control system for Linux desktops. It discovers and pairs with `waypad-daemon`, stores trusted hosts, and provides a polished touchpad, remote screen, keyboard, media, volume, brightness, and diagnostics interface.

The design is dark-first, high-contrast, minimal, and optimized for modern one-handed Android phones. It is inspired by premium minimalist hardware aesthetics without copying any proprietary branding or assets.

## Status

This is an MVP Android client. It is buildable as a debug APK and implements the core pairing and control path. The paired Linux daemon remains the authority for capabilities; if Wayland portal input is unavailable on the host, the app shows that limitation or the daemon-provided Hyprland IPC fallback instead of pretending every compositor behaves like X11.

## Features

- Onboarding, discovery, manual connect, pairing, and trusted-host management.
- Encrypted TCP protocol compatible with `waypad-daemon`.
- Host key fingerprint validation and pinning.
- Android Keystore protected trusted-host storage.
- Low-latency remote touchpad with coalesced pointer movement, tap, double tap, hold-drag, drag lock, buttons, and two-finger scroll gestures.
- Remote screen mode with source selection, live JPEG frame display, aspect-ratio-aware touch mapping, tap/click, hold-drag, scroll, right click, and quick text input.
- External Android mouse, keyboard, and controller forwarding while connected to Pad or Screen mode, subject to host capabilities.
- Live keyboard text input and shortcut buttons.
- Media, volume, brightness, lock, and suspend controls gated by daemon capabilities.
- Diagnostics screen for Wayland portal limitations.
- Processed Waypad brand artwork, adaptive launcher icon, and Material 3 Compose UI.

## Repository Layout

```text
app/
  src/main/java/dev/waypad/android/
    MainActivity.kt
    WaypadViewModel.kt
    core/model/
    core/externalinput/
    core/network/
    core/screen/
    core/storage/
    ui/
docs/
  ARCHITECTURE.md
  PROTOCOL.md
  TROUBLESHOOTING.md
```

## Requirements

- Android Studio or Android SDK command-line tools.
- JDK 17 or newer.
- Gradle 9.1 or newer.
- Android SDK platform 36.
- A running Linux `waypad-daemon`.

Install SDK platform from command line:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

## Build Debug APK

From this repository:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
gradle :app:testDebugUnitTest
```

## Install on a Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pairing Walkthrough

1. Start the daemon on Linux:

```bash
systemctl --user start waypad-daemon
```

2. Generate a pairing code on the Linux host:

```bash
waypad-daemon pair-code
```

3. Open Waypad on Android and tap "Discover hosts".

4. Select the host or use manual IP entry.

5. Enter the 6 digit code.

6. For manual pairing, compare the fingerprint printed by the daemon with the app.

7. After connecting, tap "Approve portal" only when the backend is `wayland-portal`, then approve the portal dialog on the Linux host. On `hyprland-ipc`, input is ready after pairing.

## Wayland and Hyprland Notes

Waypad is not an X11 automation wrapper. The daemon uses Wayland portal capability detection. On Hyprland, remote input depends on the availability and behavior of `xdg-desktop-portal-hyprland` and `org.freedesktop.portal.RemoteDesktop`.

Recommended host packages on Arch/CachyOS:

```bash
sudo pacman -S xdg-desktop-portal xdg-desktop-portal-hyprland wireplumber playerctl brightnessctl wl-clipboard
```

If the host reports `hyprland-ipc`, the daemon is using the Hyprland fallback because RemoteDesktop is unavailable. Pointer movement, drag, click, scroll, shortcuts, and live text are available. Normal ASCII text is injected as key events; unsupported characters fall back to clipboard paste and temporarily replace the host clipboard. If the host reports `noop`, open Diagnostics and run `waypad-daemon doctor` on Linux.

Remote Screen mode depends on the daemon's `capture` capability. Standard Wayland capture uses XDG Desktop Portal ScreenCast plus PipeWire. On Hyprland, the daemon can also expose monitor sources through an isolated `grim` fallback. Multi-monitor hosts show selectable sources; portal chooser sources may open a local compositor permission dialog on the PC.

External USB/Bluetooth devices connected to the Android phone are classified with Android `InputDevice` sources. Mouse, touchpad, and keyboard events are forwarded through the daemon's active pointer/keyboard backend. Controller/gamepad devices are detected, normalized, and forwarded when the host daemon reports `external_input.controller = true`; on Linux this requires the daemon user to have access to `/dev/uinput` so the host can expose a virtual gamepad to desktop apps and browser Gamepad APIs.

## Security Notes

- UDP discovery is not trusted as proof of identity.
- The daemon signs the handshake with its long-term host key.
- The app pins the host fingerprint after pairing.
- Changed host fingerprints are rejected.
- Session tokens are encrypted at rest using Android Keystore backed AES-GCM.
- There is no cloud account, relay, or internet exposure in MVP.
- The current remote screen media stream is token-protected over LAN but is not separately encrypted like the control channel.

## Troubleshooting

No hosts discovered:

```bash
journalctl --user -u waypad-daemon -f
```

Use manual IP if UDP broadcast is blocked.

Touchpad does nothing:

```bash
waypad-daemon doctor
systemctl --user status xdg-desktop-portal xdg-desktop-portal-hyprland
```

Remote Screen shows no sources:

```bash
waypad-daemon doctor
systemctl --user status pipewire wireplumber xdg-desktop-portal
```

Pairing rejected:

```bash
waypad-daemon pair-code
```

Host key changed:

Remove the trusted host in the app and re-pair only if you intentionally rotated or recreated the daemon host identity.

## Development

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Manual integration needs both repos:

```bash
cd ../waypad-deamon
cargo run -- serve
```

In another terminal:

```bash
cargo run -- pair-code
```

## Roadmap

- QR pairing payload containing IP, port, code, and fingerprint.
- Better reconnect/backoff behavior.
- WebRTC/H.264 stream transport after the source/input model stabilizes.
- Customizable shortcuts.
- Android quick settings tile.
- Tablet layout.
- Protocol integration tests against a fake daemon.

## License

No open-source license has been selected yet. See `LICENSE`.
