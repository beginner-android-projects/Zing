package com.riyazuddin.zing.data.entities

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.riyazuddin.zing.other.Constants.DEFAULT_PROFILE_PICTURE_URL
import com.riyazuddin.zing.other.Constants.PUBLIC
import java.io.Serializable
import java.util.*

@IgnoreExtraProperties
data class User(
    var name: String = "",
    var uid: String = "",
    var username: String = "",
    var profilePicUrl: String = DEFAULT_PROFILE_PICTURE_URL,
    val bio: String = "I'm on Zing now",
    var privacy: String = PUBLIC,
    @ServerTimestamp
    var date: Date? = null,

    @get:Exclude
    var online: Boolean = false,
    @get:Exclude
    var lastSeen: Long = 0L,
    @get:Exclude
    var isFollowing: Boolean = false,
) : Serializable