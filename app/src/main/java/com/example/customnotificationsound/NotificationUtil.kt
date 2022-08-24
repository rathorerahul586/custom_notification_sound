package com.example.customnotificationsound

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Copyright (C) 2020 Loconav Inc.
 *
 * Created by Rahul Kumar
 * @Date: 24.08.2022
 * @Time: 6:20 PM
 * @Email: rahul.kumar@loconav.com
 *
 *Description:
 */
object NotificationUtil {

    private fun getSharedPref(context: Context): SharedPreferences {
        return context.getSharedPreferences("shared_pref", Activity.MODE_PRIVATE)
    }

    fun sendNotification(context: Context) {
        val notificationChannelId =
            getSharedPref(context).getString("notification_id", "sound_1")
        val builder = NotificationCompat.Builder(
            context,
            "text_notification_id$notificationChannelId"
        )
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("My notification $notificationChannelId")
            .setContentText("${getSharedPref(context).getString("notification_sound_src", "")}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(1, builder.build())
        }
    }
}