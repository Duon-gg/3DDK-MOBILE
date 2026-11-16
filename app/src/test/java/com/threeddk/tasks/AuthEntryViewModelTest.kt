package com.threeddk.tasks

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class AuthEntryViewModelTest {
 @Test fun emailStepAndBackKeepChosenIntent() {
  val model=AuthEntryViewModel(SavedStateHandle())
  assertEquals("WELCOME",model.screen.value)
  model.choose(true)
  model.continueEmail()
  assertTrue(model.passwordStep.value)
  model.back()
  assertEquals("REGISTER",model.screen.value)
  assertFalse(model.passwordStep.value)
  model.back()
  assertEquals("WELCOME",model.screen.value)
 }
 @Test fun recreatedModelRestoresFormWithoutStoringPassword() {
  val state=SavedStateHandle(mapOf("screen" to "LOGIN","passwordStep" to true))
  val model=AuthEntryViewModel(state)
  assertEquals("LOGIN",model.screen.value)
  assertTrue(model.passwordStep.value)
  model.choose(true)
  assertFalse(model.passwordStep.value)
 }
}
