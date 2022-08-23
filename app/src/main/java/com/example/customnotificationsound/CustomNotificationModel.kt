package com.example.customnotificationsound

import android.net.Uri

/**
 * Copyright (C) 2020 Loconav Inc.
 *
 * Created by Rahul Kumar
 * @Date: 22.08.2022
 * @Time: 5:13 PM
 * @Email: rahul.kumar@loconav.com
 *
 *Description:
 */
data class CustomNotificationModel(
    val name: String,
    val uri: String
){
    override fun toString(): String {
        return name
    }
}
