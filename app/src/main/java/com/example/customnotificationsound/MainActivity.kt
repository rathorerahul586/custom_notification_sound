package com.example.customnotificationsound

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.URL
import java.net.URLConnection


class MainActivity : AppCompatActivity() {
    private var sharedPref: SharedPreferences? = null
    private var sharedPrefEditor: SharedPreferences.Editor? = null
    lateinit var filePath: String
    lateinit var spinner: Spinner
    lateinit var listSwitchButton: SwitchCompat
    private var soundSource: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        filePath = "${externalMediaDirs.firstOrNull()?.path}/Notifications"
        sharedPref = getSharedPreferences("shared_pref", Activity.MODE_PRIVATE)
        sharedPrefEditor = sharedPref?.edit()
        findViewById<Button>(R.id.recreate_notification_channel)?.setOnClickListener {
            soundSource = "Hard codded sounds from raw"
            recreateChannel(getRingtoneFromRaw())
        }

        findViewById<Button>(R.id.select_notification_sound)?.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone")
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, null as Uri?)
            chooseRingtoneLauncher.launch(intent)
        }

        findViewById<Button>(R.id.show_notification)?.setOnClickListener {
            NotificationUtil.sendNotification(this)
        }

        findViewById<Button>(R.id.download_notification_sound)?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                downloadAudio()
            }
        }

        findViewById<Button>(R.id.copy_notification_sound)?.setOnClickListener {
            copyRawToMediaStore("Loco-Sound 1", R.raw.loco_sound_1)
            copyRawToMediaStore("Loco-Sound 2", R.raw.loco_sound_2)
        }

        findViewById<Button>(R.id.open_setting)?.setOnClickListener {
            openNotificationChannelSettings()
        }

        findViewById<Button>(R.id.delete_notification_sound)?.setOnClickListener {
            deleteRingtonesFromMediaStore("Loco-Sound 1")
            deleteRingtonesFromMediaStore("Loco-Sound 2")
            deleteRingtonesFromMediaStore("Loco-notification")
        }

        findViewById<Spinner>(R.id.notification_list_sp)?.apply {
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    recreateChannel(Uri.parse(getSoundList()[position].uri))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }?.also {
            spinner = it
        }

        findViewById<SwitchCompat>(R.id.sound_list_switch)?.apply {
            setOnCheckedChangeListener { buttonView, isChecked ->
                setSpinnerAdapter()
            }
        }?.also {
            listSwitchButton = it
            setSpinnerAdapter()
        }

    }

    private fun setSpinnerAdapter() {
        spinner.adapter = ArrayAdapter(
            applicationContext,
            com.google.android.material.R.layout.support_simple_spinner_dropdown_item,
            getSoundList()
        )
    }

    private fun getSoundList(): ArrayList<CustomNotificationModel> {
        return if (listSwitchButton.isChecked) {
            soundSource = "Sounds from system’s sound library using In-App UI"
            getOnlyInAppSounds()
        } else {
            soundSource = "Sound from app provided sounds using In-App UI"
            getRingtoneList()
        }
    }

    private fun downloadAudio() {
        var count: Int
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
            notifyMediaScanner(storedFile.absolutePath)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteRingtonesFromMediaStore(tuneName: String) {
        contentResolver.delete(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.MediaColumns.TITLE + " = ?",
            arrayOf<String?>(tuneName)
        )
    }

    private fun copyRawToMediaStore(tuneName: String, id: Int) {
        val `in` = resources.openRawResource(id)
        val path = File(filePath)
        if (!path.exists()) {
            path.mkdirs()
        }

        val storedFile = File(path, "$tuneName.mp3")
        storedFile.createNewFile()
        val out = FileOutputStream(storedFile)
        val buff = ByteArray(1024)
        var read = 0
        try {
            while (`in`.read(buff).also { read = it } > 0) {
                out.write(buff, 0, read)
            }
        } finally {
            `in`.close()
            out.close()
            notifyMediaScanner(storedFile.absolutePath)
        }
    }

    private var chooseRingtoneLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri =
                    result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    soundSource ="Sounds from system+app provided sound using system Default UI"
                    recreateChannel(uri)
                } else {
                    soundSource = "Sound from app provided sounds using system Default UI"
                    recreateChannel(getRingtoneFromRaw())
                }
            }
        }

    private fun notifyMediaScanner(filePath: String) {
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(filePath),
            null
        ) { _, _ ->
            Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recreateChannel(soundUri: Uri?) {
        Log.d("TAG", "recreateChannel:soundUri -> $soundUri ")
        Log.d("TAG", "recreateChannel:soundUri -> ${soundUri?.path} ")
        val previousId: String = sharedPref?.getString("notification_id", "") ?: "raw"
        val newChannelId: String = soundUri?.getQueryParameter("title") ?: "raw"
        sharedPrefEditor?.putString("notification_id", newChannelId)
        sharedPrefEditor?.putString("notification_sound_src", soundSource)
        sharedPrefEditor?.apply()

        deleteNotificationChannel(previousId)
        createNotificationChannel(newChannelId, soundUri)
    }

    /**
     * A method to get notification sound from raw directory
     * */
    private fun getRingtoneFromRaw(): Uri {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.loco_sound_1)
    }

    private fun deleteNotificationChannel(channelId: String?) {
        NotificationManagerCompat.from(this)
            .deleteNotificationChannel(
                "text_notification_id$channelId"
            )
    }

    private fun createNotificationChannel(notificationChannelId: String?, soundUri: Uri?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Testing notification $notificationChannelId"
            val description = "Added to testing of notification sound"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel("text_notification_id$notificationChannelId", name, importance)
            channel.description = description
            channel.setSound(
                soundUri,
                Notification.AUDIO_ATTRIBUTES_DEFAULT
            )
            NotificationManagerCompat.from(this)
                .createNotificationChannel(channel)
        }
    }

    private fun getRingtoneList(): ArrayList<CustomNotificationModel> {
        val notificationList = ArrayList<CustomNotificationModel>()
        val manager = RingtoneManager(this)
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

    private fun getOnlyInAppSounds(): ArrayList<CustomNotificationModel> {
        val notificationList = ArrayList<CustomNotificationModel>()
        val notificationFile = File(filePath)
        Log.d("TAG", "getOnlyInAppSounds: {${notificationFile.path}}")
        notificationFile.listFiles()?.let {
            it.forEach {
                val name = it.name.replace(".mp3", "")
                val path = it.absolutePath + "?title=" + name
                MediaStore.Audio.Media.getContentUri(path).path?.let {
                    notificationList.add(
                        CustomNotificationModel(
                            name,
                            it.replace("/audio/media", "")
                        )
                    )
                    Log.d(
                        "TAG",
                        "getOnlyInAppSounds: $name ${it}"
                    )
                }
            }
        }
        return notificationList
    }

    private fun openNotificationChannelSettings() {
        val notificationChannelId =
            sharedPref?.getString("notification_id", "sound_1")

        val intent: Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, "text_notification_id$notificationChannelId")
        startActivity(intent)
    }
}