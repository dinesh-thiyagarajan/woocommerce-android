package com.woocommerce.android.ui.blaze

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent.BLAZE_BANNER_DISMISSED
import com.woocommerce.android.analytics.AnalyticsEvent.BLAZE_ENTRY_POINT_TAPPED
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.blaze.BlazeUrlsHelper.BlazeFlowSource
import com.woocommerce.android.ui.products.ProductListRepository
import com.woocommerce.android.ui.products.ProductStatus.PUBLISH
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.store.WCOrderStore
import javax.inject.Inject

@HiltViewModel
class BlazeBannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val isBlazeEnabled: IsBlazeEnabled,
    private val blazeUrlsHelper: BlazeUrlsHelper,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val productRepository: ProductListRepository,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val selectedSite: SelectedSite,
    private val orderStore: WCOrderStore,
) : ScopedViewModel(savedStateHandle) {

    private val _isBlazeBannerVisible = savedStateHandle.getStateFlow(scope = viewModelScope, initialValue = false)
    val isBlazeBannerVisible = _isBlazeBannerVisible.asLiveData()

    private var blazeBannerSource: BlazeFlowSource = BlazeFlowSource.MY_STORE_BANNER

    fun updateBlazeBannerStatus() {
        launch {
            val publishedProducts = productRepository.getProductList()
                .filter { it.status == PUBLISH }
            val orderList = orderStore.getOrdersForSite(selectedSite.get())
            _isBlazeBannerVisible.value = !appPrefsWrapper.isBlazeBannerHidden(selectedSite.getSelectedSiteId()) &&
                publishedProducts.isNotEmpty() &&
                orderList.isEmpty() &&
                isBlazeEnabled()
        }
    }

    fun setBlazeBannerSource(source: BlazeFlowSource) {
        blazeBannerSource = source
    }

    fun onBlazeBannerDismissed() {
        analyticsTrackerWrapper.track(
            stat = BLAZE_BANNER_DISMISSED,
            properties = mapOf(AnalyticsTracker.KEY_BLAZE_SOURCE to blazeBannerSource.trackingName)
        )
        appPrefsWrapper.setBlazeBannerHidden(selectedSite.getSelectedSiteId(), hide = true)
        triggerEvent(DismissBlazeBannerEvent)
        triggerEvent(
            MultiLiveEvent.Event.ShowDialog(
                titleId = R.string.blaze_banner_dismiss_dialog_title,
                messageId = R.string.blaze_banner_dismiss_dialog_description,
                positiveButtonId = R.string.blaze_banner_dismiss_dialog_button,
                positiveBtnAction = { dialog, _ -> dialog.dismiss() },
            )
        )
    }

    fun onTryBlazeBannerClicked() {
        analyticsTrackerWrapper.track(
            stat = BLAZE_ENTRY_POINT_TAPPED,
            properties = mapOf(AnalyticsTracker.KEY_BLAZE_SOURCE to blazeBannerSource.trackingName)
        )
        triggerEvent(
            OpenBlazeEvent(
                url = blazeUrlsHelper.buildUrlForSite(BlazeFlowSource.MY_STORE_BANNER),
                source = blazeBannerSource
            )
        )
    }

    data class OpenBlazeEvent(val url: String, val source: BlazeFlowSource) : MultiLiveEvent.Event()
    object DismissBlazeBannerEvent : MultiLiveEvent.Event()
}
