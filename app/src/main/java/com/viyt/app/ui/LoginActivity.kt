package com.viyt.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.viyt.app.core.Engine

/**
 * Signs you into YouTube in a web page inside viyt, then keeps the session cookies
 * on the phone for yt-dlp. Your password goes straight to Google; viyt never sees it.
 */
class LoginActivity : ComponentActivity() {

    private var finished = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViytTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                            navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Outlined.Close, "Cancel") } },
                            title = { Text("Sign in to YouTube") },
                            actions = { TextButton(onClick = { tryFinish(manual = true) }) { Text("Done") } },
                        )
                    }
                ) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        AndroidView(factory = { ctx -> buildWebView(ctx) }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(ctx: android.content.Context): WebView {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        return WebView(ctx).apply {
            cm.setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // A normal Chrome user agent; Google refuses sign-in from agents marked as embedded WebViews.
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/131.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.contains("youtube.com") && Engine.cookies.webViewHasSession()) tryFinish(manual = false)
                }
            }
            loadUrl(LOGIN_URL)
        }
    }

    private fun tryFinish(manual: Boolean) {
        if (finished) return
        if (Engine.cookies.webViewHasSession()) {
            finished = true
            val n = Engine.cookies.saveFromWebView()
            Toast.makeText(this, "Signed in ($n cookies saved)", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else if (manual) {
            Toast.makeText(this, "Not signed in yet. Finish signing in first.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
                "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm" +
                "%26hl%3Den%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"
    }
}
