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

## Stream Is Very Slow (~10 FPS Average)

If the stream averages only 5-15 FPS despite selecting a 60 FPS or Game Mode
profile, check the source being used:

### Check stream backend on the daemon

```bash
journalctl --user -u waypad-daemon -f | grep 'backend='
```

- `backend=hyprland-grim` → Screenshot-per-frame using `grim` tool. Slow by design.
  Switch to "Portal picker (60 FPS capable)" in the Android app.
- `backend=wayland-screencast-portal` → PipeWire + GStreamer pipeline. Fast.

### Grim fallback = ~10 FPS

The grim backend spawns a new `grim` process per frame for full-screen JPEG
capture. At 1080p, each frame takes 80-200ms. The daemon caps grim sources at
15 FPS to prevent wasted retries. This backend exists as a fallback for hosts
without PipeWire capture and is **not suitable for gaming**.

### Fix: select Portal picker

1. On the Remote Display tab, tap "Sources" to list available sources
2. Select **"Portal picker (60 FPS capable)"**
3. **First time only**: A ScreenCast approval dialog appears on the Linux host — approve it
4. After first approval, the daemon saves a restore token and subsequent streams
   start automatically without any dialog
5. The stream now uses the real-time PipeWire pipeline

If Portal picker doesn't appear, install on the Linux host:
```bash
sudo pacman -S pipewire wireplumber xdg-desktop-portal \
  xdg-desktop-portal-hyprland gst-plugin-pipewire gst-plugins-good
systemctl --user restart pipewire wireplumber \
  xdg-desktop-portal xdg-desktop-portal-hyprland
```

If Portal picker exists but **no approval dialog appears on Linux**:
1. Delete any stale restore token: `rm ~/.config/waypad-daemon/portal_restore_token.json`
2. Restart daemon: `systemctl --user restart waypad-daemon`
3. Check the dialog isn't hidden behind other windows
4. Verify portal: `systemctl --user status xdg-desktop-portal-hyprland`

### Check delivered vs target FPS

With stats enabled in Settings, the stream overlay shows `delivered/target fps`
(e.g. `52/60 fps`). If delivered is far below target even with the portal
path, the bottleneck is:
1. Network bandwidth (reduce resolution or quality)
2. Host encode CPU (JPEG at high quality is CPU-intensive)
3. Compositor capture rate

## Streaming Feels Laggy / Not 60 FPS

The app shows `delivered/target fps` in the stream status overlay when stats are
enabled in Settings. If the delivered number is far below the target (e.g. 18/60
fps), check each stage:

### Verify the daemon is receiving the correct settings

```bash
journalctl --user -u waypad-daemon -f | grep "screen stream started"
```

Expected: `fps=60 quality=52` (Game Mode) or `fps=60 quality=58` (Ultra Low Latency).

### Check what is actually being produced

```bash
adb logcat -d -v time | grep -E 'WaypadScreenStream.*frame seq=|WaypadViewModel.*screen_stream'
```

Healthy output shows `frame seq=N` advancing and `starting screen stream` with
the correct profile in the same log batch.

### Frame dropping behavior

The daemon drops frames that cannot be sent within a 12 ms deadline to keep
latency low. If your network is congested or Wi-Fi quality is poor, the
delivered FPS will drop below the target. This is intentional: the pipeline
prefers lower framerate over adding buffer delay.

### Android decode bottleneck

JPEG decode uses `BitmapFactory` with `RGB_565` configuration on a dedicated
thread. If frame delivery stalls, check:

```bash
adb logcat -d -v time | grep "frame_skip_stale"
```

Stale frame skips mean the pipeline is producing frames faster than Android can
decode them. Reduce the capture resolution or switch to a lower quality profile.

### Portal vs Grim capture

Portal (PipeWire/GStreamer) capture is significantly faster than the Grim
fallback. For gaming, the Portal path is required. Verify:

```bash
waypad-daemon doctor | grep capture
journalctl --user -u waypad-daemon -f | grep "stream.*started"
```

"Portal stream" is good. "grim stream" will never reach 60 fps and is only
suitable for desktop viewing.

## Controller Input Feels Delayed / Unplayable

### Verify the controller path

Controller forwarding requires:
1. Controller connected to Android and detected by the app
2. Game Mode or fullscreen active on the Remote Display tab
3. Host controller forwarding support (`external_input.controller = true`)

Check Android-side event flow:

```bash
adb logcat -d -v time | grep -E 'input_queue_drop|input_queue_coalesce|external_controller_axis|external_controller_button'
```

Healthy: `input_queue_coalesce_controller_axes` with multiple axes per batch
means the app is combining analog updates before sending them. The controller
path uses a fire-forget send path that does not wait for daemon responses.

### Burst/backlog behavior

If you see `input_queue_drop_stale_realtime`, the app is protecting latency by
dropping old analog states. This is correct behavior for gaming. If this fires
constantly with low controller activity, the control channel may be blocked
(see "Stream Looks Good But Feels Laggy" below).

### Host-side uinput injection

Controller events use uinput with deferred flush for axis events (flush only
on button events). This batches multiple axis writes into fewer kernel calls.
Watch daemon logs:

```bash
journalctl --user -u waypad-daemon -f | grep "virtual gamepad"
```

## Stream Looks Good But Feels Laggy (High Motion-To-Photon Delay)

This is the "looks nice but feels terrible" situation. The video appears smooth
but there is a large gap between what the PC shows and what the phone shows.

### Check frame age

The stats overlay shows last frame age. If this is consistently above 80 ms, the
pipeline has head-of-line delay:

1. **JPEG encoding time**: Large frames + high quality = high encode time.
   Lower quality (Game Mode uses 52) or lower resolution.
2. **Network buffer bloat**: TCP can build large send buffers. The daemon now
   enforces a 12 ms send deadline per frame.
3. **Android decode time**: Check for `frame_skip_stale` in logs. If multiple
   frames are skipped in sequence, the decoder cannot keep up.

### Check the host GStreamer pipeline

```bash
journalctl --user -u waypad-daemon -f | grep "gstreamer"
```

Warnings from the `gstreamer` producer indicate pipewire feed issues. The
pipeline uses `leaky=downstream` queue with 1 buffer max and `drop-only=true`
videorate for minimal buffering. If GStreamer stderr shows frame drops, the
compositor capture is the bottleneck.

### Reduce end-to-end path

For minimum motion-to-photon delay:
- Use Game Mode profile (60 fps, quality 52, max 1280px)
- Keep the phone close to the Wi-Fi access point
- Close other apps on the host that use PipeWire or the GPU
- Use the Portal capture path, not Grim

## Commands Arrive In Bursts After Delay

This is the "buffering then dump" problem. Causes and fixes:

### Android-side queue buildup

If other commands (text, shortcuts) are pending in the same channel as pointer
moves, the drain loop blocks on those slow operations. Check:

```bash
adb logcat -d -v time | grep "input_queue_backpressure"
```

Backpressure on terminal (non-realtime) commands means the channel is full and
important events are being preserved. This should be rare. If it happens often,
the daemon may be slow to respond.

### Control channel contention

The single SecureChannel handles all commands. Long-running operations like
`list_screen_sources` block the channel temporarily. The fire-forget path for
controller events bypasses response draining to keep latency low.

### Host-side serialization

The daemon processes one command at a time. If `gstreamer stderr` or portal
calls are slow, the entire command pipeline stalls. The daemon uses async portal
calls but some operations (text injection) are sequential by design.

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

## Performance Tuning Quick Reference

| Setting | Balanced | Quality | Ultra Low Latency | Game Mode |
|---------|----------|---------|-------------------|-----------|
| Max FPS | 30 | 30 | 60 | 60 |
| JPEG Quality | 70 | 86 | 58 | 52 |
| Max Dimension | 1600 | 2400 | 1280 | 1280 |
| Use case | Desktop | Static content | Interactive | Gaming |
| Latency priority | Balanced | Quality | Low | Lowest |
| Image quality | Good | Best | Acceptable | Playable |

Game Mode also:
- Hides UI for immersive fullscreen
- Enables controller forwarding
- Prevents accidental Android UI interaction
- Uses minimum encoding quality for speed

## Stream Gets Stuck On "Connecting" And Portal Dialog Never Appears

If the stream stays on "Connecting stream" forever and no ScreenCast
approval dialog appears on the Linux desktop, pre-authorize once:

```bash
# On the Linux host, run this ONCE:
waypad-daemon authorize-portal
```

A dialog appears — approve it. After that, restart the daemon:
```bash
systemctl --user restart waypad-daemon
```

Now all future streams will start automatically at 60 FPS without
any host-side approval.

## Display Persistence & Auto-Restore

The daemon remembers which screen source you used last and auto-selects it
on subsequent stream starts:

```bash
# View saved preference:
cat ~/.config/waypad-daemon/preferred_source.json
```

If the saved source disappears (monitor unplugged, permission revoked),
the daemon falls back to the default source. No terminal commands needed.

### Portal approval story

| Situation | What happens |
|-----------|-------------|
| First time opening stream | Portal dialog appears if backend supports it |
| Stream restart without portal approval | Grim fallback (20-25 fps, 250ms send deadline) |
| After portal approved once | restore_token auto-approves forever |
| restore_token expires or is revoked | Auto-fallback to grim; re-run `authorize-portal` to re-approve |
| Changing FPS/quality | Stream restarts on SAME display, no picker needed |

## Grim FPS Limit (~10 FPS Maximum)

The grim screenshot backend is fundamentally limited to ~9-10 FPS because:
1. Each frame spawns a new `grim` process (Wayland connection + screenshot + JPEG encode)
2. Minimum time per cycle on Hyprland: ~100-110ms
3. No continuous capture protocol — grim is a one-shot tool

**For 30+ FPS, use "Portal picker (60 FPS capable)" source.**
The portal uses PipeWire continuous capture + GStreamer encode pipeline.

### If portal doesn't work:
```bash
# Check PipeWire/GStreamer installation
gst-inspect-1.0 pipewiresrc jpegenc
systemctl --user status pipewire wireplumber
```

---

## Settings Persistence

Waypad now uses Android DataStore to persist all stream and input settings. Settings are saved immediately when changed and restored on app launch.

### First-run defaults

On a fresh install, the app defaults to **Game Mode**:
- Profile: Game
- Target FPS: 60
- Max resolution: 1280px
- JPEG quality: 52
- Stats overlay: ON

This default was chosen for controller-based play. If you prefer desktop browsing, switch to **Balanced** or **Quality** in Settings.

### What is persisted

- Stream profile, custom FPS, resolution, and JPEG quality
- Show stats toggle
- Haptics on/off
- Game Mode on/off

### What is NOT persisted

- Current screen source selection (the daemon remembers this)
- Live stream state (must reconnect after app restart)
- Trusted hosts (stored separately in encrypted prefs)

### Troubleshooting: 60 fps selected but stream not actually 60

1. Check the **stream overlay** or **Diagnostics** screen. It shows:
   - `Target FPS` — what you selected
   - `Actual FPS (host)` — what the daemon reported it can deliver
   - `Delivered FPS` — what the phone is actually receiving

2. If **Actual FPS** is lower than **Target FPS**, the daemon clamped it because the backend cannot go higher:
   - `hyprland-grim` is capped at 30 FPS (and realistically delivers 7-15 FPS)
   - `wayland-screencast-portal` and `x11-ffmpeg` support up to 60 FPS

3. To fix: select a different source in the **Stream Setup** card. Look for:
   - `x11-ffmpeg` (X11 hosts — true 60 FPS)
   - `wayland-screencast-portal` (Wayland hosts with portal — true 60 FPS)

### Troubleshooting: setting falls back after restart

If a setting reverts after killing and reopening the app:

```bash
adb logcat -d -v time | grep "settings_loaded"
```

Expected: `settings_loaded profile=Game fps=60 ...`

If it shows `profile=Balanced fps=30`, DataStore did not write. This can happen if:
- The app crashed before the async write completed
- The phone storage is full
- The APK was reinstalled without preserving data

**Fix**: change the setting again, wait 2 seconds, then force-stop and reopen the app. DataStore writes are asynchronous but typically complete within milliseconds.

### Troubleshooting: stream settings not sticking

Settings only apply when a **new** stream starts. Changing FPS while a stream is live does NOT reconfigure the active pipeline. You must:
1. Change the setting in **Settings → Stream Performance**
2. Return to the **Screen** tab
3. Tap **Stop Stream**, then **Start Stream**

The app will warn you about this in the UI: *"These apply the next time you start a stream."*

### Troubleshooting: poor FPS even with portal selected

If delivered FPS is still low with the portal backend:

1. **Check network**: Move closer to the Wi-Fi access point. The stream is uncompressed JPEG over TCP; poor Wi-Fi causes frame drops.
2. **Reduce resolution**: In Settings, set Max Resolution to 1280 instead of 2400.
3. **Reduce quality**: Game Mode (quality 52) is optimized for speed. Quality 86+ is CPU-heavy.
4. **Check host CPU**: `journalctl --user -u waypad-daemon -f | grep throughput`. If `fps_measured` is low on the host, the encoder or compositor is the bottleneck.
5. **Check for frame skipping**: `adb logcat -d -v time | grep frame_skip_stale`. If Android is skipping frames, lower resolution/quality.

### Troubleshooting: controller/game mode confusion

- **Controller ready but not forwarding**: The app shows "Controller connected" on the Pad tab. You MUST open **Remote Screen → Fullscreen** or **Game Mode** for controller input to reach the PC. This prevents accidental Android UI navigation.
- **Game Mode hides controls**: This is intentional. Reveal them by:
  - Tapping the top handle
  - Pressing the controller **Mode** button
  - Pressing **Start + Select** together
- **Game Mode resets stream settings to Game profile**: This is intentional. Entering Game Mode applies the Game preset (60 FPS, 1280px, quality 52). Exiting Game Mode restores your previous custom settings.

### X11 users: new high-performance backend

If your Linux host runs X11 (not Wayland), the daemon now automatically lists X11 monitors using `ffmpeg x11grab`. This backend delivers true 60 FPS without any portal approval. Look for sources labeled **"X11 – 60 FPS, no approval"** in the app.

To verify X11 capture is available:
```bash
waypad-daemon doctor | grep capture
# Expected: backend includes x11-ffmpeg or shows available X11 monitors
```
