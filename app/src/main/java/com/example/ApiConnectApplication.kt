package com.example

import android.app.Application
import com.example.data.api.RetrofitClient
import com.example.data.local.AppDatabase

class ApiConnectApplication : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        RetrofitClient.initializeCache(this)
        com.example.data.startup.AppStartupStabilizer.stabilize(this)
        // GKK secure channel: dispatch stack ready even if the UI never opens,
        // so background data-SMS receipt + heartbeats work.
        com.example.data.kali.KaliEnvironmentManager.init(this)
        com.example.data.kali.KaliEnvironmentManager.boot()
        com.example.data.dispatch.HeartbeatManager.start(this)
    }

    companion object {
        lateinit var instance: ApiConnectApplication
            private set
    }
}
