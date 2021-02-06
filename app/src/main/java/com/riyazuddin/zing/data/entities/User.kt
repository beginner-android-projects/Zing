package com.riyazuddin.zing.data.entities

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.riyazuddin.zing.other.Constants.DEFAULT_PROFILE_PICTURE_URL
import java.io.Serializable

@IgnoreExtraProperties
data class User(
    val name: String = "",
    val uid: String = "",
    val username: String = "",
    val profilePicUrl: String = DEFAULT_PROFILE_PICTURE_URL,
    val follows: List<String> = listOf(),
    val bio: String = "I'm on Zing now",
    @get:Exclude
    var isFollowing: Boolean = false,
): Serializable