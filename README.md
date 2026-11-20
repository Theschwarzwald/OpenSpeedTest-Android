# OpenSpeedTest for Android

A small Kotlin / Jetpack Compose app that runs your self-hosted OpenSpeedTest server.

> **Heads-up: this app was vibe coded.** I built it with an AI assistant and have only
> minimal programming knowledge myself. It works for me, but the code hasn't been
> professionally reviewed and may have rough edges. Use it at your own risk. Issues and
> pull requests are welcome, but I may not be able to fix things quickly (or at all).

## Open & run
1. Android Studio → File → Open → pick this `OpenSpeedTestApp` folder.
2. Let Gradle sync (if asked to set up the Gradle wrapper, say yes).
3. Plug in your phone (or start an emulator) and press ▶ Run.

## Using it
- The speed test fills the whole screen. Tap the small ☰ pill (top left) to open the server sidebar.
  The dot shows status: green = connected, yellow = connecting, red = unreachable.
- ↻ at the top of the sidebar reloads the test page.
- When a test finishes, a **Try again** button appears at the bottom. It takes you back to the Start screen.
- Every finished test is saved automatically. Tap **History** (top right) to see past results
  (date, server, download, upload, ping, jitter). Delete single results or Clear all.
- **Add server** → enter a name and address, e.g. `192.168.1.10:3000`
  (OpenSpeedTest's Docker image listens on 3000 for http, 3001 for https).
- Tap a server to connect. The radio button shows which one is connected.
- ✏️ edits a server, 🗑️ deletes it (asks first).

## Staying connected
- The selected server is saved and reopened every time the app starts.
- It only changes when you tap a different server (or delete it).
- If the server can't be reached, the app keeps retrying every 5 s; it never drops the server.
- Rotation / dark-mode changes don't reload the page, so a running test isn't interrupted.
- The screen stays on while the app is open.
- Back button sends the app to the background instead of closing it.

## Where things live
| File | What it does |
|---|---|
| `SpeedTestApp.kt` | Main screen, top bar, connect / retry logic |
| `ServerDrawer.kt` | Sidebar + add/edit dialog |
| `ServerViewModel.kt` | Holds the server list and the connected server |
| `ServerRepository.kt` | Saves servers on the phone |
| `SpeedTestWebView.kt` | WebView setup and error handling |

## If the page is ever blank
Plug the phone into a PC, open `chrome://inspect` in Chrome, and click **inspect** under
the app's WebView to see any page errors.

## Dark mode & OLED
- The top buttons follow the speed test page: white text in dark mode, dark text in light mode
  (this includes when you tap the page's own ☀ button).
- On OLED screens, dark mode uses pure black (page, sidebars, top buttons) so pixels switch off.
- OLED is auto-detected (best guess). Override it at the bottom of the Servers sidebar: Auto / On / Off.

## Download
A prebuilt debug APK is attached to the [latest release](../../releases/latest).

## License
This app is open source under the [MIT License](LICENSE). You're free to use, copy, modify and
share it, as long as the license notice stays with it.

This is an unofficial app and isn't affiliated with OpenSpeedTest. The OpenSpeedTest name and
logo belong to their respective owners.
