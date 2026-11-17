package com.viyt.app.core

import com.viyt.app.BuildConfig

/** Which of the two apps this build is: ViTube (videos) or ViVibe (music). */
object AppInfo {
    val vibe: Boolean = BuildConfig.IS_VIBE
    val name: String = if (vibe) "ViVibe" else "ViTube"
}
