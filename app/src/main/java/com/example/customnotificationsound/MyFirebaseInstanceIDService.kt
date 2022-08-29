package com.example.customnotificationsound

import android.media.RingtoneManager
import android.util.Log
import androidx.constraintlayout.widget.Constraints
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FireBaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(s: String) {
        super.onNewToken(s)
        Log.e("NEW_TOKEN", s)
        NotificationUtil.getSharedPref(this).edit().apply{
            putString("token", s)
        }.also {
            it.apply()
        }
        instanceID()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        var title: String? = getString(R.string.app_name)
        var body = ""
        Log.d(Constraints.TAG, "Message Notification data: ${remoteMessage.data}")
        remoteMessage.notification?.let {
            Log.d(Constraints.TAG, "Message Notification Body: ${it}")
            title = it.title.toString()
            body = it.body.toString()
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        NotificationUtil.sendNotification(this, remoteMessage.data["sound_name"])
    }

    private fun instanceID() {
        FirebaseInstallations.getInstance().getToken(false)
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(Constraints.TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }
                // Get new Instance ID token
                val instanse = FirebaseInstallations.getInstance().id
                Log.d(Constraints.TAG, "onComplete: $instanse")
            })
    }
}