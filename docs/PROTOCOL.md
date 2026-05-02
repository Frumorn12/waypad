# Waypad Android Protocol Notes

The Android app implements the protocol documented by the daemon repository in `waypad-daemon/docs/PROTOCOL.md`.

## Implemented Client Behavior

- Broadcasts `WAYPAD_DISCOVER_V1` on UDP port `47770`.
- Connects to TCP port `47771` by default.
- Performs P-256 ECDH with the daemon.
- Verifies the daemon ECDSA signature over the handshake transcript.
- Computes and pins the daemon host fingerprint.
- Encrypts every post-handshake frame with AES-GCM.
- Rejects changed host fingerprints for trusted hosts.
- Stores trusted host records with Android Keystore protected AES-GCM.

## Pairing

Pairing requires a code generated on the Linux host:

```bash
waypad-daemon pair-code
```

The app sends the code inside the encrypted channel. Discovery data is not trusted as authority; it is only used to find host address and display the expected fingerprint.

## Commands

The app sends these command names:

```text
prepare_input
pointer_move
pointer_move_absolute
pointer_button
scroll
external_input
text
shortcut
media
volume
brightness
clipboard_set
system
get_capabilities
list_screen_sources
start_screen_stream
stop_screen_stream
```

Unsupported actions are surfaced as host-provided error messages.

## External Android Input

When the app is connected and the user is in Pad or Screen mode, external Android devices are forwarded with `external_input`:

```json
{
  "name": "external_input",
  "device_id": "android:7:abcd1234",
  "device_type": "mouse",
  "event": {
    "type": "pointer_move",
    "dx": 12.5,
    "dy": -3.0
  }
}
```

Supported event types are:

```text
device_connected
device_disconnected
pointer_move
pointer_button
pointer_scroll
keyboard_key
controller_button
controller_axis
```

Keyboard events carry XKB keysyms plus pressed/released state. Controller axes are normalized to `[-1.0, 1.0]` with Android motion-range deadzones applied before transport. The host capability field `external_input.controller` is authoritative; on Linux it is true when the daemon can expose a `uinput` virtual gamepad.

## Remote Screen Stream

The app sends `list_screen_sources`, lets the user choose a monitor/source when available, and starts the stream with `start_screen_stream`. The daemon returns:

```json
{
  "session_id": "...",
  "stream_port": 47771,
  "token": "...",
  "codec": "jpeg",
  "transport": "waypad-control-port-stream-v2"
}
```

For `waypad-control-port-stream-v2`, the app connects back to the daemon control port returned in `stream_port` and writes one JSON line before any encrypted control-channel handshake:

```json
{"type":"stream_connect","token":"..."}
```

The daemon attaches that TCP socket to the pending stream session, writes `WAYPAD_STREAM_V1`, then sends repeated `u32 header length`, `u32 payload length`, JSON header, and JPEG payload frames. Older `waypad-frame-stream-v1` daemons used a dynamic per-stream TCP port; the Android client still understands that shape, but current daemons use the stable control port to avoid LAN firewall/NAT failures on random high ports.

Pointer input over the displayed stream uses `pointer_move_absolute` with source-local coordinates. The app maps phone touch positions through contain-fit scaling first, so letterboxed/pillarboxed areas are ignored.
