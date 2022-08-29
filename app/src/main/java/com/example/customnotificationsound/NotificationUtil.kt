package com.example.customnotificationsound

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.*
import java.net.URL
import java.net.URLConnection

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

    fun getSharedPref(context: Context): SharedPreferences {
        return context.getSharedPreferences("shared_pref", Activity.MODE_PRIVATE)
    }

    fun sendNotification(context: Context, notificationSound: String? = null) {
        val sp = getSharedPref(context)
        val notificationChannelId = sp.getString("notification_id", "sound_1")

        if (notificationChannelId != notificationSound) {
            recreateChannel(sp, context, getSoundUriByName(context, notificationSound))
        }

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

    fun recreateChannel(
        sharedPreferences: SharedPreferences?, context: Context, soundUri: Uri?
    ) {

        Log.d("TAG", "recreateChannel:soundUri -> $soundUri ")
        Log.d("TAG", "recreateChannel:soundUri -> ${soundUri?.getQueryParameter("title")} ")
        val previousId: String = sharedPreferences?.getString("notification_id", "") ?: "system"
        val newChannelId: String = soundUri?.getQueryParameter("title") ?: "system"
        sharedPreferences?.edit()?.apply {
            putString("notification_id", newChannelId)
            apply()
        }

        deleteNotificationChannel(context, previousId)
        createNotificationChannel(context, newChannelId, soundUri)
    }

    private fun deleteNotificationChannel(context: Context, channelId: String?) {
        NotificationManagerCompat.from(context).deleteNotificationChannel(
            "text_notification_id$channelId"
        )
    }

    private fun createNotificationChannel(
        context: Context, notificationChannelId: String?, soundUri: Uri?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Testing notification $notificationChannelId"
            val description = "Added to testing of notification sound"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel("text_notification_id$notificationChannelId", name, importance)
            channel.description = description
            channel.setSound(soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT)
            NotificationManagerCompat.from(context)
                .createNotificationChannel(channel)
        }
    }

    private fun getSoundUriByName(context: Context, soundName: String?): Uri? {
        val soundUri = getRingtoneList(context).firstOrNull { it.name == soundName }?.uri
        return if (soundUri == null) {
            if (soundName?.contains("Loco") == true) {
                downloadAudio(context, soundName)
            } else getDefaultRingtoneUri()
        } else {
            Uri.parse(soundUri)
        }
    }

    fun getRingtoneList(context: Context): ArrayList<CustomNotificationModel> {
        val notificationList = ArrayList<CustomNotificationModel>()
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_NOTIFICATION)
        val cursor: Cursor = manager.cursor
        while (cursor.moveToNext()) {
            val title: String = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri: String =
                cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(
                    RingtoneManager.ID_COLUMN_INDEX
                ) + "?title=" + cursor.getString(
                    RingtoneManager.TITLE_COLUMN_INDEX
                )

            notificationList.add(CustomNotificationModel(title, uri))
        }
        return notificationList
    }

    fun getDefaultRingtoneUri(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun downloadAudio(context: Context, soundName: String? = null): Uri? {
        var count: Int
        val filePath = "${context.externalMediaDirs.firstOrNull()?.path}/Notifications"
        try {
            val url =
                URL("http://commondatastorage.googleapis.com/codeskulptor-assets/week7-brrring.m4a")
            val conexion: URLConnection = url.openConnection()
            conexion.connect()
            val path = File(filePath)
            if (!path.exists()) {
                path.mkdirs()
            }
            val tuneName = "Loco-notification"
            val storedFile = File(path, "$tuneName.mp3")
            storedFile.createNewFile()
            val input: InputStream = BufferedInputStream(url.openStream())
            val output: OutputStream = FileOutputStream(storedFile)
            val data = ByteArray(1024)
            var total: Long = 0
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()
            notifyMediaScanner(context, storedFile.absolutePath)
            return if (soundName != null) {
                getSoundUriByName(context, soundName)
            } else
                null

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun notifyMediaScanner(context: Context, filePath: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            null
        ) { _, _ ->
            Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
        }
    }
}