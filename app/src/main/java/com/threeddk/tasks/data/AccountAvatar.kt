package com.threeddk.tasks.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

fun accountAvatar(user: FirebaseUser?, savedAvatar: String?): String? = preferredAvatar(
    user?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.photoUrl?.toString(),
    user?.photoUrl?.toString(),
    savedAvatar
)

internal fun preferredAvatar(google: String?, firebase: String?, saved: String?): String? =
    listOf(google, firebase, saved).firstOrNull { !it.isNullOrBlank() }
