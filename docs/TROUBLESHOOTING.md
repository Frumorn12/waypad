# Troubleshooting

## The App Finds No Hosts

Use manual connect with the Linux host IP address and port `47771`.

Check the daemon:

```bash
systemctl --user status waypad-daemon
journalctl --user -u waypad-daemon -f
```

Some routers block UDP broadcast between Wi-Fi clients. Manual IP entry is expected to work in that case.

## Pairing Code Rejected

Generate a fresh code:

```bash
waypad-daemon pair-code
```

Codes expire after 5 minutes by default and are single use.

## Host Fingerprint Changed

The app refuses to connect because the host identity changed. This is intentional. On the Linux host, confirm whether the daemon state was deleted or the host key was rotated:

```bash
waypad-daemon doctor
```

If the change was intentional, remove the trusted host in the app and pair again.

## Touchpad Connects But Does Nothing

Open Diagnostics in the app and inspect the input reason. On the host run:

```bash
waypad-daemon doctor
systemctl --user status xdg-desktop-portal xdg-desktop-portal-hyprland
```

For Hyprland, install the portal backend:

```bash
sudo pacman -S xdg-desktop-portal xdg-desktop-portal-hyprland
systemctl --user restart xdg-desktop-portal xdg-desktop-portal-hyprland
```

Then tap "Approve portal" in the app and approve the prompt on the Linux host.

If Diagnostics shows `hyprland-ipc`, the app is using the daemon's Hyprland fallback instead of the portal. That backend supports pointer movement, drag, mouse buttons, scroll, shortcuts, and live text through the focused host window. Normal ASCII text is injected as key events; unsupported text falls back to clipboard paste and temporarily replaces the current Wayland clipboard.

## Remote Screen Shows No Sources

Open Diagnostics and check the capture backend/reason. On the host:

```bash
waypad-daemon doctor
systemctl --user status pipewire wireplumber xdg-desktop-portal
```

For CachyOS/Hyprland, install the portal and capture helpers:

```bash
sudo pacman -S xdg-desktop-portal xdg-desktop-portal-hyprland pipewire wireplumber gst-plugin-pipewire gst-plugins-good grim
systemctl --user restart pipewire wireplumber xdg-desktop-portal xdg-desktop-portal-hyprland
```

If the daemon reports `hyprland-grim`, monitor streaming can work through the Hyprland fallback even when the standard portal stream path is incomplete.

## Remote Screen Says "Connection Closed" Or "Broken Pipe"

Current Waypad daemons return `stream_port = 47771` and `transport = waypad-control-port-stream-v2`. The app connects to the same TCP port as the encrypted control channel and attaches the stream with a one-line token request. If logs show a random high port such as `33577`, the daemon is old; rebuild/reinstall the daemon and restart `waypad-daemon`.

Collect Android logs while reproducing:

```bash
adb logcat -c
adb logcat -v time | grep -E 'Waypad|WaypadScreenStream|Broken pipe|ConnectException|SocketTimeout'
```

On the host, watch:

```bash
journalctl --user -u waypad-daemon -f
```

Healthy logs include `stream_connect_success host=... port=47771` on Android and `screen stream client attached` on the daemon. If Android reports `ConnectException` to `47771`, confirm the phone can reach the host IP and that the daemon is listening on the LAN interface.

## Fullscreen Shows Black Or Drops Stream

Fullscreen should not recreate the stream. Enter fullscreen from the Screen tab, wait for the overlay to hide, then press Android Back to exit. If the video disappears, capture:

```bash
adb logcat -d -v time | grep -E 'remote_screen_fullscreen|fullscreen_system_ui|stream_close|screen_stream_failed'
```

The expected transition is `remote_screen_fullscreen enabled=true`, `fullscreen_system_ui_hide`, then `enabled=false`, `fullscreen_system_ui_show` with no `stream_close` in between.

## Remote Screen Stays Portrait

The Android app should switch to sensor-driven rotation while the Screen tab is open, then return to portrait on the other tabs. If it stays portrait, reinstall the latest APK and check:

```bash
adb logcat -d -v time | grep -E 'orientation_policy|WaypadRemoteScreen'
```

Entering the Screen tab should log `orientation_policy=full_sensor`. Leaving it should log `orientation_policy=portrait`.

## Game Mode Controls Are Hidden

This is intentional. Game Mode hides the top bar after a short timeout to reduce
visual clutter and accidental local UI input. Reveal controls with either:

- Tap the small top handle.
- Press the controller Mode button.
- Press Start+Select together on a controller.

Those controller shortcuts are reserved for the Android UI and are consumed
locally so they do not become random clicks or gamepad events on the PC.

## QR Invite Or Mobile Data Connection Fails

Generate a fresh invite:

```bash
waypad-daemon invite --qr
```

The payload should contain the Linux LAN IP, not `127.0.0.1`. If automatic
detection picks the wrong interface, pass the address explicitly:

```bash
waypad-daemon invite --qr --address 192.168.0.184
```

For mobile data, Waypad currently supports direct TCP only. Expose the daemon
port deliberately, then advertise that endpoint:

```bash
waypad-daemon invite --qr --remote-address your-public-hostname.example
```

There is no bundled relay, STUN, TURN, or automatic ICE traversal yet. The
firewall/router must allow TCP `47771`.

### Pairing policy

The daemon now enforces pairing policy at the protocol level instead of silently
dropping TCP connections:

- `require_private_lan=true` (default): **already-paired devices can reconnect from any network**, but **new pairing from public IPs is blocked**.
- To allow **new pairing from mobile data / public IPs**, set `allow_public_pairing=true` in the daemon config (recommended), or set `require_private_lan=false`.
- The QR includes a `policy` field so the Android app knows whether remote pairing
is expected to work.

If the Android app shows **"Remote pairing blocked by host policy"**, the daemon
is correctly rejecting a public pairing attempt. To fix it on the host:

```bash
# Recommended: allow public pairing but keep LAN-only restriction for reconnection
# Edit ~/.config/waypad-daemon/config.json and set:
#   "allow_public_pairing": true
systemctl --user restart waypad-daemon
```

When an invite contains both `remote_address` and `lan_address`, Android logs
each endpoint attempt:

```bash
adb logcat -d -v time | grep -E 'qr_invite_connect_attempt|qr_invite_connect_failed|qr_invite_policy_rejected'
```

This is the expected fallback behavior. If every candidate fails, the advertised
public hostname/IP is not reachable from the current phone network or the daemon
is rejecting the pairing attempt because of policy.

## QR Scanner Does Not Open The Camera

The Discovery screen uses CameraX and ML Kit, not a placeholder. Collect logs:

```bash
adb logcat -d -v time | grep -E 'WaypadQrScanner|camera_permission|camera_start|qr_decode'
```

Healthy logs include `scanner_open`, `camera_permission_result granted=true`,
`camera_start_success`, and `qr_decode_success`. If permission is denied, open
Android Settings for Waypad and grant Camera.

## Stream Works But Taps Do Not Control The PC

Capture and input are separate host capabilities. The app can show the screen while input is blocked. Check Diagnostics:

- `wayland-portal`: tap "Approve portal" in Pad mode and approve pointer/keyboard control on the PC.
- `hyprland-ipc`: input should work through the daemon's Hyprland IPC fallback.
- `noop`: input is unavailable; use screen viewing read-only until the daemon reports a supported input backend.

Touches in black bars around the video are intentionally ignored because they do not map to desktop pixels.

## External Mouse Or Keyboard Connected To Android Does Nothing

External device forwarding is active while connected in Pad or Screen mode. Open Diagnostics and check:

- Android input devices: the phone must report the mouse, keyboard, touchpad, or controller.
- External pointer / External keyboard: the host must report support through the daemon capability model.
- Input backend: `wayland-portal` needs portal approval; `hyprland-ipc` works without a portal prompt on Hyprland.

Collect Android logs:

```bash
adb logcat -d -v time | grep -E 'WaypadExternalInput|external_input'
```

Healthy logs include `device_inventory`, optional `pointer_capture_request`, and `transport_send type=external_*` while the remote screen or pad is active.

## Controller Detected But Does Not Control The PC

Android gamepad/controller detection is implemented, including buttons, sticks, triggers, and hat axes. If Diagnostics shows controller forwarding as unsupported, run `waypad-daemon doctor` on the PC and check `external_input.controller`. Linux controller forwarding uses a daemon-side `uinput` virtual gamepad, so `/dev/uinput` must exist and be writable by the daemon user. Mouse and keyboard forwarding can still work from the same phone even when controller support is blocked by host permissions.

If the controller appears to click Android UI or blink/retrigger, open Remote
Screen fullscreen or Game Mode before testing. The app forwards controller input
only while remote capture is active, filters repeat `ACTION_DOWN` events, applies
axis deadzones, coalesces axis updates, drops stale realtime input when backlog
is high, and reserves only Mode or Start+Select for local control reveal.

For burst or lag diagnosis:

```bash
adb logcat -d -v time | grep -E 'input_queue_|external_controller_axis|controller_input_held'
```

Repeated `input_queue_drop_stale_realtime` means the app is protecting host
latency by dropping old analog states instead of replaying them late.

## APK Build Fails

Confirm JDK and Android SDK:

```bash
java -version
gradle --version
sdkmanager --list | grep "platforms;android-36"
```

Build:

```bash
gradle :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```
