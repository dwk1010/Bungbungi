package com.bungbungi.app

import android.app.Application

class BungApp : Application() {
    lateinit var engine: DriveEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = DriveEngine(this).also { it.start() }
    }
}
