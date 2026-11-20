package com.example.openspeedtest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

sealed interface PageState {
    data object Idle : PageState
    data object Loading : PageState
    data object Loaded : PageState
    data class Error(val message: String) : PageState
}

class SpeedTestClient(
    private val onState: (PageState) -> Unit,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {

    var allowSelfSigned = false
    var oled = false
    private var mainFrameFailed = false

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url == BLANK) return
        mainFrameFailed = false
        onState(PageState.Loading)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url == BLANK) return
        if (!mainFrameFailed) {
            onState(PageState.Loaded)
            view.evaluateJavascript("window.__ostOled = $oled;", null)
            view.evaluateJavascript(RESULT_WATCHER_JS, null)
        }
    }

    // Only the page itself failing counts. A hiccup in one upload/download request
    // during a test does NOT kick you off the server.
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            mainFrameFailed = true
            onState(PageState.Error(error.description?.toString() ?: "Connection failed"))
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            mainFrameFailed = true
            onState(PageState.Error("Server returned HTTP ${errorResponse.statusCode}"))
        }
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (allowSelfSigned) {
            handler.proceed()
        } else {
            handler.cancel()
            mainFrameFailed = true
            onState(PageState.Error("Certificate not trusted. Edit this server and turn on \"Allow self-signed HTTPS certificate\"."))
        }
    }

    // If the WebView engine crashes, rebuild it instead of crashing the app.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

    companion object {
        const val BLANK = "about:blank"

        /**
         * Runs inside the OpenSpeedTest page. Every second it reads the
         * Download / Upload / Ping / Jitter numbers. When all are filled in and the
         * page says "All done" (or the numbers stop changing for 4 s), it sends the
         * result to the app once.
         */
        val RESULT_WATCHER_JS = """
            (function () {
              if (window.__ostWatcher) return;
              window.__ostWatcher = true;
              function num(t) {
                if (!t) return null;
                var s = String(t).replace(/[^0-9.]/g, '');
                if (!s || s === '.') return null;
                var n = parseFloat(s);
                return isNaN(n) ? null : n;
              }
              function find(re) {
                var groups = [
                  document.querySelectorAll('.rtextnum[id]'),
                  document.querySelectorAll('text[id], tspan[id]')
                ];
                for (var g = 0; g < groups.length; g++) {
                  for (var i = 0; i < groups[g].length; i++) {
                    var e = groups[g][i];
                    if (re.test(e.id)) {
                      var n = num(e.textContent);
                      if (n !== null) return n;
                    }
                  }
                }
                return null;
              }
              var last = null, since = 0, reported = null, phase = '';
              function setPhase(p) { if (p !== phase) { phase = p; OSTBridge.onPhase(p); } }
              setInterval(function () {
                try {
                  var d = find(/^down/i), u = find(/^up/i), p = find(/^ping/i), j = find(/jit/i);
                  if (d === null || u === null || p === null) {
                    last = null; reported = null;
                    setPhase(d === null && p === null ? 'idle' : 'running');
                    return;
                  }
                  var key = [d, u, p, j].join('|');
                  if (key !== last) { last = key; since = Date.now(); }
                  var text = document.body ? (document.body.textContent || '') : '';
                  var done = /all\s*done/i.test(text) || (Date.now() - since > 4000);
                  if (done && reported !== key) {
                    reported = key;
                    OSTBridge.onResult(JSON.stringify({ down: d, up: u, ping: p, jitter: j }));
                  }
                  setPhase(reported === key ? 'done' : 'running');
                } catch (e) {}
              }, 1000);

              // ---- Theme: tell the app if the page is dark, and make it pure black on OLED ----
              var oledStyle = null, oledFills = [], lastTheme = '', scannedKey = '';
              function rgb(c) {
                var m = String(c || '').match(/[0-9.]+/g);
                if (!m || m.length < 3) return null;
                if (m.length >= 4 && parseFloat(m[3]) === 0) return null; // transparent
                return [Math.round(+m[0]), Math.round(+m[1]), Math.round(+m[2])];
              }
              function pageBg() {
                return rgb(getComputedStyle(document.body).backgroundColor) ||
                       rgb(getComputedStyle(document.documentElement).backgroundColor) ||
                       [255, 255, 255];
              }
              function clearOled() {
                if (oledStyle) { oledStyle.remove(); oledStyle = null; }
                for (var i = 0; i < oledFills.length; i++) oledFills[i].style.fill = '';
                oledFills = []; scannedKey = '';
              }
              function applyOled(bg) {
                if (!oledStyle) {
                  oledStyle = document.createElement('style');
                  oledStyle.textContent = 'html,body{background:#000 !important;}';
                  (document.head || document.documentElement).appendChild(oledStyle);
                }
                var key = bg.join(',');
                if (key === scannedKey) return;
                scannedKey = key;
                var els = document.querySelectorAll('svg *');
                for (var i = 0; i < els.length; i++) {
                  var e = els[i];
                  if (e.style.fill) continue;
                  var f = rgb(getComputedStyle(e).fill);
                  if (f && f.join(',') === key) { e.style.fill = '#000'; oledFills.push(e); }
                }
              }
              setInterval(function () {
                try {
                  if (!document.body) return;
                  // Measure the page's own color with our black override switched off.
                  if (oledStyle) oledStyle.disabled = true;
                  var bg = pageBg();
                  if (oledStyle) oledStyle.disabled = false;
                  var dark = (0.299 * bg[0] + 0.587 * bg[1] + 0.114 * bg[2]) < 128;
                  if (dark && window.__ostOled === true) applyOled(bg);
                  else if (oledStyle || oledFills.length) clearOled();
                  var t = (dark ? 'd' : 'l') + bg.join(',');
                  if (t !== lastTheme) { lastTheme = t; OSTBridge.onTheme(dark, bg[0], bg[1], bg[2]); }
                } catch (e) {}
              }, 700);
            })();
        """.trimIndent()
    }
}

enum class TestPhase { Idle, Running, Done }

/** Receives messages from the page's JavaScript (on a background thread) and hands them to the UI. */
class SpeedTestBridge(
    private val onResult: (download: Double, upload: Double, ping: Double, jitter: Double?) -> Unit,
    private val onPhase: (TestPhase) -> Unit,
    private val onTheme: (dark: Boolean, argb: Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onResult(json: String) {
        try {
            val o = JSONObject(json)
            val d = o.getDouble("down")
            val u = o.getDouble("up")
            val p = o.getDouble("ping")
            val j = if (o.isNull("jitter")) null else o.getDouble("jitter")
            main.post { onResult(d, u, p, j) }
        } catch (e: Exception) {
            Log.w("OpenSpeedTest", "Could not read result: $json", e)
        }
    }

    @JavascriptInterface
    fun onTheme(dark: Boolean, r: Int, g: Int, b: Int) {
        val argb = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        main.post { onTheme(dark, argb) }
    }

    @JavascriptInterface
    fun onPhase(phase: String) {
        val p = when (phase) {
            "done" -> TestPhase.Done
            "running" -> TestPhase.Running
            else -> TestPhase.Idle
        }
        main.post { onPhase(p) }
    }
}

/** Holds the one live WebView and remembers what it has loaded, so it never reloads by accident. */
class WebViewController {
    var webView: WebView? = null
        private set
    private var client: SpeedTestClient? = null
    private var loadedKey: String? = null

    fun attach(webView: WebView, client: SpeedTestClient) {
        this.webView = webView
        this.client = client
        loadedKey = null
    }

    fun detach() {
        webView = null
        client = null
        loadedKey = null
    }

    /** Loads [server] only if it isn't already the one showing. */
    fun load(server: Server?) {
        val wv = webView ?: return
        val key = server?.let { "${it.id}|${it.url}|${it.allowSelfSigned}" }
        if (key == loadedKey) return
        loadedKey = key
        if (server == null) {
            wv.loadUrl(SpeedTestClient.BLANK)
        } else {
            client?.allowSelfSigned = server.allowSelfSigned
            wv.loadUrl(server.url)
        }
    }

    /** Forces a fresh load of [server] (Retry / Reload). */
    fun reload(server: Server?) {
        loadedKey = null
        load(server)
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun createSpeedTestWebView(context: Context, client: SpeedTestClient, bridge: SpeedTestBridge): WebView {
    WebView.setWebContentsDebuggingEnabled(true) // lets you inspect it from chrome://inspect on a PC
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.TRANSPARENT) // no white flash in dark mode
        webViewClient = client
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("OpenSpeedTest", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE        // never measure from cache
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100                                // big system font won't break the gauge layout
            // Look like normal mobile Chrome, same as your browser does.
            userAgentString = userAgentString.replace("; wv", "")
        }
        // Keep the test running at full priority, even if the app goes to the background.
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        addJavascriptInterface(bridge, "OSTBridge")
        isFocusable = true
        isFocusableInTouchMode = true
    }
}
