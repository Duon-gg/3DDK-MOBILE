package com.threeddk.tasks.viewmodel
import com.threeddk.tasks.R

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class AuthEntryViewModel(private val state: SavedStateHandle) : ViewModel() {
    val screen = state.getStateFlow("screen", "WELCOME")
    val passwordStep = state.getStateFlow("passwordStep", false)
    fun choose(register: Boolean) {
        state["screen"] = if (register) "REGISTER" else "LOGIN"
        state["passwordStep"] = false
    }
    fun continueEmail() { state["passwordStep"] = true }
    fun back() {
        if (passwordStep.value) state["passwordStep"] = false
        else state["screen"] = "WELCOME"
    }
  val pages=listOf(
   Triple(R.string.welcome_one,R.string.welcome_one_detail,R.drawable.welcome_workspace),
   Triple(R.string.welcome_two,R.string.welcome_two_detail,R.drawable.welcome_progress),
   Triple(R.string.welcome_three,R.string.welcome_three_detail,R.drawable.welcome_team)
  )

}
