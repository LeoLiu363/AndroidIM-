package com.example.androidim

import android.app.Application
import com.example.androidim.utils.IMNotificationManager

class IMApplication : Application() {
    
    companion object {
        @Volatile
        private var INSTANCE: IMApplication? = null
        
        fun getInstance(): Application {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        // 创建通知渠道
        IMNotificationManager.createNotificationChannel(this)
    }
}

