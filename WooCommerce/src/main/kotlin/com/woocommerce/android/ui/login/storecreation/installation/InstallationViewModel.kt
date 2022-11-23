package com.woocommerce.android.ui.login.storecreation.installation

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewAuthenticator
import com.woocommerce.android.ui.login.storecreation.installation.InstallationViewModel.ViewState.LoadingState
import com.woocommerce.android.ui.login.storecreation.installation.InstallationViewModel.ViewState.SuccessState
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.network.UserAgent
import javax.inject.Inject

@HiltViewModel
class InstallationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val wpComWebViewAuthenticator: WPComWebViewAuthenticator,
    val userAgent: UserAgent,
    val analyticsTrackerWrapper: AnalyticsTrackerWrapper
) : ScopedViewModel(savedStateHandle) {
    private val _viewState = savedState.getStateFlow<ViewState>(this, LoadingState)
    val viewState = _viewState.asLiveData()

    private val url = "https://woowoozela.com/"

    init {
        launch {
            _viewState.update { SuccessState(url) }
        }
        analyticsTrackerWrapper.track(
            AnalyticsEvent.SITE_CREATION_STEP,
            mapOf(
                AnalyticsTracker.KEY_STEP to AnalyticsTracker.VALUE_STEP_STORE_CREATED
            )
        )
    }

    fun onShowPreviewButtonClicked() {
        analyticsTrackerWrapper.track(AnalyticsEvent.SITE_CREATION_SITE_PREVIEWED)
        triggerEvent(OpenStore(url))
    }

    fun onManageStoreButtonClicked() {
        // TODO
    }

    fun onRetryButtonClicked() {
        // TODO
    }

    sealed interface ViewState : Parcelable {
        @Parcelize
        object LoadingState : ViewState

        @Parcelize
        object ErrorState : ViewState

        @Parcelize
        data class SuccessState(val url: String) : ViewState
    }

    data class OpenStore(val url: String) : Event()
}
