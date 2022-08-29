package com.example.customnotificationsound

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream


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
            NotificationUtil.recreateChannel(sharedPref, this, getRingtoneFromRaw())
        }

        findViewById<Button>(R.id.default_notification_sound)?.setOnClickListener {
            NotificationUtil.recreateChannel(
                sharedPref,
                this,
                NotificationUtil.getDefaultRingtoneUri()
            )
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
                NotificationUtil.downloadAudio(this@MainActivity)
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
                    NotificationUtil.recreateChannel(
                        sharedPref,
                        context,
                        Uri.parse(getSoundList()[position].uri)
                    )
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
            NotificationUtil.getRingtoneList(this)
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
            NotificationUtil.notifyMediaScanner(this, storedFile.absolutePath)
        }
    }

    private var chooseRingtoneLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri =
                    result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    soundSource = "Sounds from system+app provided sound using system Default UI"
                    NotificationUtil.recreateChannel(sharedPref, this, uri)
                } else {
                    soundSource = "Sound from app provided sounds using system Default UI"
                    NotificationUtil.recreateChannel(sharedPref, this, getRingtoneFromRaw())
                }
            }
        }

    /**
     * A method to get notification sound from raw directory
     * */
    private fun getRingtoneFromRaw(): Uri {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.loco_sound_1)
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