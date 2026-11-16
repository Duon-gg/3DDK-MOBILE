package com.threeddk.tasks
import com.threeddk.tasks.data.preferredAvatar

import org.junit.Assert.*
import org.junit.Test

class AccountAvatarTest {
 @Test fun googlePhotoOverridesPreviouslyChosenAvatar() {
  assertEquals("google",preferredAvatar("google","firebase","old-avatar"))
 }
 @Test fun emailOnlyAccountRetainsFallbackWithoutInventingGooglePhoto() {
  assertEquals("saved",preferredAvatar(null," ","saved"))
  assertNull(preferredAvatar(null,null,""))
 }
}
