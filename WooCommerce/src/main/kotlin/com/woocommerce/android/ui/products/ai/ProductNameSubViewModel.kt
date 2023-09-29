package com.woocommerce.android.ui.products.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.getStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ProductNameSubViewModel(
    savedStateHandle: SavedStateHandle,
    val analyticsTracker: AnalyticsTrackerWrapper,
    override val onDone: (String) -> Unit
) : AddProductWithAISubViewModel<String> {
    companion object {
        private const val KEY_SUBSCREEN_NAME = "product_name"
    }

    private val _events = MutableSharedFlow<Event>()
    override val events: Flow<Event> get() = _events

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val name = savedStateHandle.getStateFlow(viewModelScope, "", KEY_SUBSCREEN_NAME)

    val state = name.map {
        UiState(it)
    }.asLiveData()

    fun onProductNameChanged(enteredName: String) {
        name.value = enteredName
    }

    fun onDoneClick() {
        onDone(name.value)
    }

    fun onSuggestNameClicked() {
        analyticsTracker.track(
            AnalyticsEvent.PRODUCT_NAME_AI_ENTRY_POINT_TAPPED,
            mapOf(
                AnalyticsTracker.KEY_HAS_INPUT_NAME to (name.value.isNotEmpty()).toString(),
                AnalyticsTracker.KEY_SOURCE to AnalyticsTracker.VALUE_PRODUCT_CREATION_AI
            )
        )
        viewModelScope.launch {
            _events.emit(NavigateToAIProductNameBottomSheet)
        }
    }

    override fun close() {
        viewModelScope.cancel()
    }

    data class UiState(
        val name: String
    )

    object NavigateToAIProductNameBottomSheet : Event()
}