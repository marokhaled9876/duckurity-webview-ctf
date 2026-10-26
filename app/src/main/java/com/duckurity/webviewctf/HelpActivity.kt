package com.duckurity.webviewctf

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * ============================================================
 *  INTENTIONALLY VULNERABLE -- CTF CHALLENGE #1 (Mobile, Medium)
 *  Part of the Duckurity internship task pack. Do not "fix" this
 *  file; the whole point is to teach/exploit the bug described in
 *  README.md. A hardened version is included there for reference.
 * ============================================================
 *
 * Bug class: unrestricted android.webkit.JavascriptInterface exposure
 * combined with an exported Activity that loads an unvalidated URL.
 *
 * Root cause (see README for full writeup):
 *   1) addJavascriptInterface() binds FlagBridge to EVERY page this
 *      WebView ever renders -- there is no per-origin restriction in
 *      the old (API < 21 compatible) JS-bridge API.
 *   2) HelpActivity is exported and reads the "url" query parameter off
 *      its duckurity://help deep link (intent.data) with NO allowlist,
 *      so an attacker-controlled remote page can be loaded into this
 *      same WebView and call the bridge.
 *
 * Intentionally, this is the ONLY way into the bridge:
 *   - there is no raw "url" Intent extra handled (an `adb shell am
 *     start --es url ...` one-liner does nothing on its own),
 *   - only http/https targets are accepted (no local file:// loading),
 *   - and the bundled help.html has no injectable sink.
 *   The single solve path is: duckurity://help?url=<attacker http(s)
 *   page> -- see solve/SOLVE.md.
 */
class HelpActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // Needed so a remote page reached via the deep link can still
        // fetch() the loopback exfil listener used in the solve PoC.
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true

        // The privileged bridge. In the "real" app this exists so the
        // bundled help.html can show order status, trigger native
        // share sheets, etc. -- it was never meant to be reachable from
        // untrusted content.
        webView.addJavascriptInterface(FlagBridge(), "nativeBridge")

        // --- THE BUG ---
        // The only attacker-reachable input is the "url" query param on
        // the duckurity://help deep link (intent.data). It's checked for
        // scheme (http/https only, so this can't be used to read local
        // files) but NOT for host/domain -- any http(s) URL an attacker
        // controls is accepted and handed straight to loadUrl(), which
        // is enough to reach the privileged nativeBridge object attached
        // above. A `?url=` value that isn't present, or isn't http(s),
        // falls back to the bundled, safe help page.
        val deepLinkUrl = intent.data?.getQueryParameter("url")
        val target = deepLinkUrl?.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: "file:///android_asset/help.html"

        webView.loadUrl(target)
    }

    /**
     * The "secret" the challenge is built around. Note that nothing
     * about this class checks WHO is calling getSecretFlag() -- any
     * JavaScript running in the WebView, regardless of which page
     * loaded it, can call this.
     */
    class FlagBridge {
        @JavascriptInterface
        fun getSecretFlag(): String {
            return "DUCK{w3bv13w_bridg3_tru5t_bound4ry_pwn3d}"
        }
    }
}
