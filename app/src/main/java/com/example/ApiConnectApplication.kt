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
    }

    companion object {
        lateinit var instance: ApiConnectApplication
            private set
    }
}
