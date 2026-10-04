package com.uwu.animex.ui.onboarding

import androidx.lifecycle.ViewModel
import com.uwu.animex.data.Onboarding
import kotlinx.coroutines.flow.StateFlow

class OnboardingViewModel : ViewModel() {
    val done: StateFlow<Boolean> = Onboarding.done

    fun complete() = Onboarding.complete()
}
