package com.threeddk.tasks

import android.accessibilityservice.AccessibilityService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.threeddk.tasks.data.googleWebClientId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAuthUiTest {
 private fun eventually(assertion:()->Unit){
  var last:Throwable?=null
  repeat(80){try{assertion();return}catch(e:AssertionError){last=e}catch(e:Exception){last=e};android.os.SystemClock.sleep(250)}
  throw last!!
 }

 @Test fun missingGoogleConfigurationKeepsEmailLoginAvailableAfterRecreation(){
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  assumeTrue(app.repository.configured&&app.repository.auth?.currentUser==null&&googleWebClientId(app).isBlank())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   onView(withId(R.id.google)).perform(scrollTo(),click())
   onView(withText(R.string.auth_google_setup)).check(matches(isDisplayed()))
   scenario.recreate()
   onView(withId(R.id.google)).check(matches(isEnabled()))
   onView(withId(R.id.email)).check(matches(isDisplayed()))
   onView(withId(R.id.login)).check(matches(isEnabled()))
  }
 }

 @Test fun configuredGoogleButtonAndEmailLoginSurviveRecreation(){
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  assumeTrue(app.repository.configured&&app.repository.auth?.currentUser==null&&googleWebClientId(app).isNotBlank())
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   onView(withId(R.id.google)).check(matches(withText(R.string.auth_continue_google)))
   onView(withId(R.id.google)).check(matches(isEnabled()))
   scenario.recreate()
   onView(withId(R.id.google)).check(matches(isEnabled()))
   onView(withId(R.id.email)).check(matches(isDisplayed()))
   onView(withId(R.id.login)).check(matches(isEnabled()))
  }
 }

 /** Only dismisses the provider UI; never selects an account or enters Google credentials. */
 @Test fun cancelRealGooglePickerKeepsEmailLoginAvailable(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveGooglePicker")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  assumeTrue(app.repository.configured&&app.repository.auth?.currentUser==null&&googleWebClientId(app).isNotBlank())
  val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  ActivityScenario.launch(MainActivity::class.java).use{
   onView(withId(R.id.google)).perform(scrollTo(),click())
   eventually{
    val root=automation.rootInActiveWindow
    val providerPackage=root?.packageName?.toString().orEmpty()
    root?.recycle()
    assertTrue("Cần thấy cửa sổ chọn tài khoản Google trước khi thử hủy",providerPackage in setOf("com.google.android.gms","com.android.credentialmanager","com.google.android.credentialmanager"))
   }
   assertTrue("Phải gửi được thao tác Back tới cửa sổ Google",automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
   eventually{
    onView(withId(R.id.google)).check(matches(isEnabled()))
    onView(withId(R.id.email)).check(matches(isDisplayed()))
    onView(withId(R.id.login)).check(matches(isEnabled()))
   }
   assertNull("Hủy không được tạo phiên Firebase",app.repository.auth?.currentUser)
   onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist())
  }
 }

 /** Run after the user completes Google sign-in; does not create fixtures or print identity data. */
 @Test fun manuallySignedInGoogleSessionIsAcceptedByBackend():Unit=runBlocking{
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveGoogleLogin")=="true")
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  val user=app.repository.auth?.currentUser
  assertTrue("Người dùng cần tự đăng nhập Google trong ứng dụng trước",user!=null&&user.isEmailVerified)
  assertEquals("Phiên phải đến từ Google, không chỉ tài khoản đã liên kết Google","google.com",user!!.getIdToken(false).await().signInProvider)
  val capabilities=app.repository.workspaceApi.call("GET","capabilities").asJsonObject
  assertTrue("API phải chấp nhận phiên Google và trả capability P0",capabilities.getAsJsonObject("features")["atomicTemplates"].asBoolean)
 }
}
