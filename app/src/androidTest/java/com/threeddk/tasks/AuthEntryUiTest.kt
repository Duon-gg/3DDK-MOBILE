package com.threeddk.tasks

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AuthEntryUiTest {
 @Test fun welcomeEmailPasswordAndBackSurviveRotationWithoutSendingCredentials() {
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  assumeTrue(app.repository.configured&&app.repository.auth?.currentUser==null)
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   onView(withId(R.id.welcome_pages)).perform(swipeLeft())
   onView(withId(R.id.welcome_login)).perform(click())
   onView(withId(R.id.password_box)).check(matches(withEffectiveVisibility(Visibility.GONE)))
   onView(withId(R.id.email)).perform(replaceText("preview@example.test"),closeSoftKeyboard())
   onView(withId(R.id.login)).perform(scrollTo(),click())
   onView(withId(R.id.password_box)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
   scenario.recreate()
   onView(withId(R.id.password_box)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
   onView(withId(R.id.form_back)).perform(scrollTo(),click())
   onView(withId(R.id.password_box)).check(matches(withEffectiveVisibility(Visibility.GONE)))
   onView(withId(R.id.form_back)).perform(scrollTo(),click())
   onView(withId(R.id.welcome_register)).check(matches(isDisplayed()))
  }
 }
}
