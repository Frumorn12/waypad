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
