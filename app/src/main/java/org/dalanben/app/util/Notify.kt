package org.dalanben.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.dalanben.app.MainActivity
import org.dalanben.app.R

object Notify {

    const val CHANNEL_MSG = "dalanben_msg"       // 私信
    const val CHANNEL_NOTIF = "dalanben_notif"    // 互动通知
    const val CHANNEL_SYSTEM = "dalanben_system"   // 系统/审核通知

    private var lastMsg = 0
    private var lastNotif = 0

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            listOf(
                NotificationChannel(CHANNEL_MSG, "私信", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "新私信通知"
                },
                NotificationChannel(CHANNEL_NOTIF, "互动", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "点赞、评论、关注等互动通知"
                },
                NotificationChannel(CHANNEL_SYSTEM, "系统", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "审核结果、系统公告"
                }
            ).forEach { nm.createNotificationChannel(it) }
        }
    }

    fun checkAndNotify(ctx: Context, msgCount: Int, notifCount: Int) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val nm = NotificationManagerCompat.from(ctx)

        // 新增私信
        val newMsg = msgCount - lastMsg
        if (newMsg > 0) {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra("navigate_to", "messages")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(100, NotificationCompat.Builder(ctx, CHANNEL_MSG)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${newMsg} 条新私信")
                .setContentText("你有未读的私信消息")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())
        }

        // 新增互动通知
        val newNotif = notifCount - lastNotif
        if (newNotif > 0) {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra("navigate_to", "messages")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(200, NotificationCompat.Builder(ctx, CHANNEL_NOTIF)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${newNotif} 条新通知")
                .setContentText("你有新的互动通知")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build())
        }

        lastMsg = msgCount
        lastNotif = notifCount
    }
}
