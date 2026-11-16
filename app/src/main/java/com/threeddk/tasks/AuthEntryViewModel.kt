package com.threeddk.tasks

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
}
