package com.threeddk.tasks

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.threeddk.tasks.data.googleWebClientId
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAuthUiTest {
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
}
