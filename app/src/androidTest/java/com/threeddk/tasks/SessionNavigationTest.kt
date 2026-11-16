package com.threeddk.tasks

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNavigationTest {
 private fun eventually(assertion:()->Unit){
  var last:Throwable?=null
  repeat(40){
   try{assertion();return}catch(e:AssertionError){last=e}catch(e:Exception){last=e}
   android.os.SystemClock.sleep(100)
  }
  throw last!!
 }

 /** Changes only in-memory UI state; never signs out, writes fixtures or touches drafts. */
 @Test fun returningToAuthClearsProfileAndPrivateBackStack(){
  val app=ApplicationProvider.getApplicationContext<TasksApp>()
  assumeTrue(app.repository.configured&&app.repository.auth?.currentUser==null)
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   eventually{onView(withId(R.id.welcome_login)).check(matches(isDisplayed()))}
   scenario.onActivity{activity->
    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    assertEquals(R.id.auth,nav.currentDestination?.id)
    // A signed-out workspace would require a real UID, so seed the private Profile destination first.
    nav.navigate(R.id.profile)
   }
   eventually{onView(withId(R.id.logout)).check(matches(isDisplayed()))}
   scenario.onActivity{activity->ViewModelProvider(activity)[AppViewModel::class.java].session.value="READY"}
   eventually{onView(withId(R.id.logout)).check(matches(isDisplayed()))}
   scenario.onActivity{activity->ViewModelProvider(activity)[AppViewModel::class.java].session.value="AUTH"}
   eventually{
    onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    scenario.onActivity{activity->
     val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
     assertEquals("AUTH must replace the private Profile screen",R.id.auth,nav.currentDestination?.id)
     assertNull("Back must not return to private screens after logout",nav.previousBackStackEntry)
    }
   }
   onView(withId(R.id.welcome_login)).check(matches(isDisplayed()))
  }
 }
}
