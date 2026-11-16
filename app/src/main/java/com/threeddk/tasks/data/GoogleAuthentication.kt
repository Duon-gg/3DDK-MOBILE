package com.threeddk.tasks.data
import com.threeddk.tasks.BuildConfig
import com.threeddk.tasks.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@SuppressLint("DiscouragedApi")
fun googleWebClientId(context:Context):String {
 val id=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
 return (if(id!=0)context.getString(id) else BuildConfig.FIREBASE_WEB_CLIENT_ID).trim()
}

suspend fun googleIdToken(context:Context,webClientId:String):String {
 val option=GetSignInWithGoogleOption.Builder(webClientId).build()
 val request=GetCredentialRequest.Builder().addCredentialOption(option).build()
 val applicationContext=context.applicationContext
 val activityContext=MutableContextWrapper(context)
 val credential=try{
  CredentialManager.create(applicationContext).getCredential(activityContext,request).credential
 }finally{
  // A provider may retain the wrapper after its cancelled request finishes.
  activityContext.baseContext=applicationContext
 }
 require(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
 return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

fun authenticationErrorResource(code:String):Int=when(code){
 "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL","ERROR_CREDENTIAL_ALREADY_IN_USE"->R.string.auth_account_collision
 "ERROR_OPERATION_NOT_ALLOWED"->R.string.auth_provider_disabled
 "ERROR_INVALID_CREDENTIAL","ERROR_INVALID_LOGIN_CREDENTIALS","ERROR_INVALID_IDP_RESPONSE"->R.string.auth_invalid_credential
 "ERROR_NETWORK_REQUEST_FAILED"->R.string.auth_network_error
 else->R.string.auth_default_error
}
