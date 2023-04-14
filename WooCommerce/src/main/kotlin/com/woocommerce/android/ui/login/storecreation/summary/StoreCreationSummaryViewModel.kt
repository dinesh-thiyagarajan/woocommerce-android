package com.woocommerce.android.ui.login.storecreation.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.ui.login.storecreation.CreateFreeTrialStore
import com.woocommerce.android.ui.login.storecreation.CreateFreeTrialStore.StoreCreationState.Loading
import com.woocommerce.android.ui.login.storecreation.NewStore
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreCreationSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val newStore: NewStore,
    private val createStore: CreateFreeTrialStore,
    private val tracker: AnalyticsTrackerWrapper
) : ScopedViewModel(savedStateHandle) {
    val isLoading = createStore.state
        .map { it is Loading }
        .asLiveData()

    fun onCancelPressed() { triggerEvent(OnCancelPressed) }
    fun onTryForFreeButtonPressed() {
        tracker.track(AnalyticsEvent.SITE_CREATION_TRY_FOR_FREE_TAPPED)
        launch {
            createStore(
                storeDomain = newStore.data.domain,
                storeName = newStore.data.name,
                profilerData = newStore.data.profilerData,
                countryCode = newStore.data.country?.code
            ).collect { siteId ->
                siteId?.let {
                    newStore.update(siteId = it)
                    triggerEvent(OnStoreCreationSuccess)
                } ?: triggerEvent(OnStoreCreationFailure)
            }
        }
    }

    object OnCancelPressed : MultiLiveEvent.Event()
    object OnStoreCreationSuccess : MultiLiveEvent.Event()
    object OnStoreCreationFailure : MultiLiveEvent.Event()
}
