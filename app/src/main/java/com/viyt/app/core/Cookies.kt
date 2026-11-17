package com.viyt.app.core

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

/**
 * Your YouTube session lives here, on the phone only, as a Netscape cookies.txt file
 * that yt-dlp reads with --cookies. Nothing is sent anywhere except youtube.com.
 */
class Cookies(private val context: Context) {

    val file: File get() = File(context.filesDir, "cookies.txt")

    @Volatile private var cached: Map<String, String>? = null
    @Volatile private var cachedStamp = -1L

    /** name -> value for every YouTube cookie, read once and kept in memory. */
    fun map(): Map<String, String>? {
        val f = file
        if (!f.exists()) return null
        val stamp = f.lastModified()
        if (stamp == cachedStamp) return cached
        val m = LinkedHashMap<String, String>()
        f.forEachLine { line ->
            if (line.isBlank() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) return@forEachLine
            val p = line.split('\t')
            if (p.size >= 7 && p[0].contains("youtube.com")) m[p[5]] = p[6]
        }
        cached = m.ifEmpty { null }
        cachedStamp = stamp
        return cached
    }

    val isSignedIn: Boolean
        get() = map()?.let { it.containsKey("SAPISID") || it.containsKey("__Secure-3PAPISID") || it.containsKey("LOGIN_INFO") } == true

    /** True once the WebView holds a signed-in YouTube session. */
    fun webViewHasSession(): Boolean {
        val c = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return false
        return c.contains("SAPISID") && c.contains("LOGIN_INFO")
    }

    /** Copy the WebView's YouTube cookies into cookies.txt. Returns how many were saved. */
    fun saveFromWebView(): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val seen = LinkedHashMap<String, String>()
        for (url in listOf("https://www.youtube.com", "https://m.youtube.com", "https://youtube.com")) {
            val raw = cm.getCookie(url) ?: continue
            raw.split(";").forEach { part ->
                val i = part.indexOf('=')
                if (i > 0) {
                    val name = part.substring(0, i).trim()
                    val value = part.substring(i + 1).trim()
                    if (name.isNotEmpty()) seen[name] = value
                }
            }
        }
        if (seen.isEmpty()) return 0
        val expiry = System.currentTimeMillis() / 1000 + 400L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n# Written by viyt\n\n")
        for ((name, value) in seen) {
            // domain, include-subdomains, path, secure, expiry, name, value
            sb.append(".youtube.com\tTRUE\t/\tTRUE\t").append(expiry).append('\t')
                .append(name).append('\t').append(value).append('\n')
        }
        file.writeText(sb.toString())
        return seen.size
    }

    /** Import a cookies.txt exported from a desktop browser. Returns false if it doesn't look valid. */
    fun importFrom(uri: Uri): Boolean {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: return false
        val usable = text.lineSequence().any { line ->
            !line.startsWith("#") && line.contains("youtube.com") && line.split('\t').size >= 7
        }
        if (!usable) return false
        file.writeText(if (text.startsWith("# Netscape") || text.startsWith("# HTTP")) text
                       else "# Netscape HTTP Cookie File\n$text")
        return true
    }

    fun signOut() {
        file.delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
