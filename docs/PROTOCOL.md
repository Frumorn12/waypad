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
pointer_button
scroll
text
shortcut
media
volume
brightness
clipboard_set
system
get_capabilities
```

Unsupported actions are surfaced as host-provided error messages.
