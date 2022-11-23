package com.woocommerce.android.ui.login.jetpack.main

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

private const val STEPS_SAVED_STATE_KEY = "steps"

@HiltViewModel
class JetpackActivationMainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ScopedViewModel(savedStateHandle) {
    private val navArgs: JetpackActivationMainFragmentArgs by savedStateHandle.navArgs()

    private val steps = savedStateHandle.getStateFlow(
        scope = viewModelScope,
        // Just for demo
        initialValue = listOf(
            Step(
                type = StepType.Installation,
                state = StepState.Success
            ),
            Step(
                type = StepType.Activation,
                state = StepState.Ongoing
            ),
            @Suppress("MagicNumber")
            Step(
                type = StepType.Activation,
                state = StepState.Error(403)
            ),
            Step(
                type = StepType.Connection,
                state = StepState.Idle
            ),
            Step(
                type = StepType.Done,
                state = StepState.Idle
            )
        ),
        key = STEPS_SAVED_STATE_KEY
    )
    val viewState = steps.map {
        ViewState(
            siteUrl = navArgs.siteUrl,
            isJetpackInstalled = navArgs.isJetpackInstalled,
            steps = it
        )
    }.asLiveData()

    fun onCloseClick() {
        triggerEvent(Exit)
    }

    data class ViewState(
        val siteUrl: String,
        val isJetpackInstalled: Boolean,
        val steps: List<Step>
    )

    @Parcelize
    data class Step(
        val type: StepType,
        val state: StepState
    ) : Parcelable

    enum class StepType {
        Installation, Activation, Connection, Done
    }

    sealed interface StepState : Parcelable {
        @Parcelize
        object Idle : StepState

        @Parcelize
        object Ongoing : StepState

        @Parcelize
        object Success : StepState

        @Parcelize
        data class Error(val code: Int) : StepState
    }
}