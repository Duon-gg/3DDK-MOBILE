package com.threeddk.tasks
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class LaunchTest {
 @Test fun unconfiguredBuildExplainsSetupAndSurvivesRecreation(){
  if(BuildConfig.FIREBASE_APP_ID.isNotBlank())return
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   onView(withId(R.id.status)).check(matches(withText(org.hamcrest.Matchers.containsString("chưa được kết nối"))))
   scenario.recreate()
   onView(withId(R.id.status)).check(matches(isDisplayed()))
  }
 }
}
