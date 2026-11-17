package com.viyt.app.core

import android.content.Context
import android.content.SharedPreferences

/** Small settings store. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("viyt", Context.MODE_PRIVATE)

    /** Max height used when streaming: 360, 480, 720 or 1080. */
    var streamHeight: Int
        get() = sp.getInt("stream_height", 720)
        set(v) = sp.edit().putInt("stream_height", v).apply()

    var defaultDownload: DownloadFormat
        get() = runCatching { DownloadFormat.valueOf(sp.getString("dl_format", "BEST")!!) }
            .getOrDefault(DownloadFormat.BEST)
        set(v) = sp.edit().putString("dl_format", v.name).apply()

    /** Update yt-dlp from the nightly channel (fastest YouTube fixes) instead of stable. */
    var nightly: Boolean
        get() = sp.getBoolean("nightly", true)
        set(v) = sp.edit().putBoolean("nightly", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update", 0L)
        set(v) = sp.edit().putLong("last_update", v).apply()

    var accountLabel: String?
        get() = sp.getString("account_label", null)
        set(v) = sp.edit().putString("account_label", v).apply()
}
