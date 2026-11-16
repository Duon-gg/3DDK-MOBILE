package com.threeddk.tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.gson.JsonParser
import com.threeddk.tasks.data.changePassword
import com.threeddk.tasks.data.hasPasswordProvider
import com.threeddk.tasks.data.requestPasswordReset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PasswordAuthenticationTest {
    @Test fun changeAndResetPasswordsWithFirebase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("passwordEmulator") == "true")
        val project = "demo-3ddk-password"
        val host = "10.0.2.2"
        val port = 9099
        val id = UUID.randomUUID().toString()
        val email = "$id@example.test"
        val original = "Before-$id!"
        val changed = "After-$id!"
        val recovered = "Recovered-$id!"
        val app = FirebaseApp.initializeApp(
            instrumentation.targetContext,
            FirebaseOptions.Builder().setProjectId(project)
                .setApplicationId("1:123456789:android:1234567890abcdef")
                .setApiKey("fake-api-key").build(),
            "password-test-$id"
        )
        try {
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, port) }
            runBlocking {
                val account = checkNotNull(auth.createUserWithEmailAndPassword(email, original).await().user)
                assertTrue(account.hasPasswordProvider)
                assertRejected { auth.changePassword("Wrong-$id!", changed) }
                auth.changePassword(original, changed)
                assertEquals(account.uid, auth.currentUser?.uid)
                auth.signOut()
                assertRejected { auth.signInWithEmailAndPassword(email, original).await() }
                assertEquals(account.uid, auth.signInWithEmailAndPassword(email, changed).await().user?.uid)
                auth.signOut()

                auth.requestPasswordReset(" $email ")
                val code = resetCode(host, port, project, email)
                assertEquals(email, auth.verifyPasswordResetCode(code).await())
                auth.confirmPasswordReset(code, recovered).await()
                assertRejected { auth.signInWithEmailAndPassword(email, changed).await() }
                assertEquals(account.uid, auth.signInWithEmailAndPassword(email, recovered).await().user?.uid)
            }
        } finally {
            app.delete()
        }
    }

    private suspend fun assertRejected(attempt: suspend () -> Unit) {
        try {
            attempt()
            throw AssertionError("Firebase accepted an invalid password")
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            // The Auth emulator rejects this credential.
        }
    }

    private fun resetCode(host: String, port: Int, project: String, email: String): String {
        val connection = URL("http://$host:$port/emulator/v1/projects/$project/oobCodes")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            val payload = connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            return payload.getAsJsonArray("oobCodes").map { it.asJsonObject }
                .single { it.get("email").asString == email && it.get("requestType").asString == "PASSWORD_RESET" }
                .get("oobCode").asString
        } finally {
            connection.disconnect()
        }
    }
}
