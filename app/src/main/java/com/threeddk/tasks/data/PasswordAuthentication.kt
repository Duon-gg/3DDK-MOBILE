package com.threeddk.tasks.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

val FirebaseUser.hasPasswordProvider: Boolean
    get() = !email.isNullOrBlank() && providerData.any {
        it.providerId == EmailAuthProvider.PROVIDER_ID
    }

suspend fun FirebaseAuth.changePassword(currentPassword: String, newPassword: String) {
    val user = checkNotNull(currentUser)
    check(user.hasPasswordProvider)
    user.reauthenticate(EmailAuthProvider.getCredential(checkNotNull(user.email), currentPassword)).await()
    check(currentUser?.uid == user.uid)
    user.updatePassword(newPassword).await()
}

suspend fun FirebaseAuth.requestPasswordReset(email: String) {
    try {
        sendPasswordResetEmail(email.trim()).await()
    } catch (error: FirebaseAuthInvalidUserException) {
        if (error.errorCode != "ERROR_USER_NOT_FOUND") throw error
    }
}
