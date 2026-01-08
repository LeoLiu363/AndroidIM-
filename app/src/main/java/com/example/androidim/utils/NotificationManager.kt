package com.example.androidim.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.androidim.R
import com.example.androidim.ui.chat.ChatActivity
import com.example.androidim.ui.main.MainActivity

/**
 * 消息通知管理器
 */
object IMNotificationManager {
    
    private const val CHANNEL_ID = "im_messages"
    private const val CHANNEL_NAME = "消息通知"
    private const val NOTIFICATION_ID_BASE = 1000
    
    /**
     * 创建通知渠道（Android 8.0+ 需要）
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "即时通讯消息通知"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 显示新消息通知
     */
    fun showMessageNotification(
        context: Context,
        fromUserId: String,
        fromUsername: String,
        content: String
    ) {
        // 创建点击通知后跳转到聊天界面的 Intent
        val chatIntent = ChatActivity.newIntent(context, fromUserId, fromUsername)
        val pendingIntent = PendingIntent.getActivity(
            context,
            fromUserId.hashCode(),
            chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
            .setContentTitle(fromUsername)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 使用 fromUserId 的 hashCode 作为通知 ID，这样同一个好友的消息会更新同一个通知
        notificationManager.notify(NOTIFICATION_ID_BASE + fromUserId.hashCode(), notification)
    }
    
    /**
     * 取消指定好友的通知
     */
    fun cancelNotification(context: Context, userId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_BASE + userId.hashCode())
    }
    
    /**
     * 取消所有通知
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}

