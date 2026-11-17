package com.viyt.app

import android.app.Application
import com.viyt.app.core.Engine

class ViytApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.start(this)
    }
}
