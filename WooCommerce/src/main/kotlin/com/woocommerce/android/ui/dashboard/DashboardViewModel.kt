package com.woocommerce.android.ui.dashboard

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsEvent.DASHBOARD_STORE_TIMEZONE_DIFFER_FROM_DEVICE
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.extensions.isEligibleForAI
import com.woocommerce.android.extensions.isNotNullOrEmpty
import com.woocommerce.android.extensions.isSitePublic
import com.woocommerce.android.extensions.offsetInHours
import com.woocommerce.android.network.ConnectionChangeReceiver
import com.woocommerce.android.network.ConnectionChangeReceiver.ConnectionChangeEvent
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.tools.SiteConnectionType
import com.woocommerce.android.ui.analytics.hub.sync.AnalyticsUpdateDataStore
import com.woocommerce.android.ui.analytics.ranges.StatsTimeRange
import com.woocommerce.android.ui.analytics.ranges.StatsTimeRangeSelection
import com.woocommerce.android.ui.analytics.ranges.StatsTimeRangeSelection.SelectionType
import com.woocommerce.android.ui.dashboard.DashboardViewModel.OrderState.AtLeastOne
import com.woocommerce.android.ui.dashboard.DashboardViewModel.OrderState.Empty
import com.woocommerce.android.ui.dashboard.domain.GetStats
import com.woocommerce.android.ui.dashboard.domain.GetStats.LoadStatsResult
import com.woocommerce.android.ui.dashboard.domain.GetTopPerformers
import com.woocommerce.android.ui.dashboard.domain.GetTopPerformers.TopPerformerProduct
import com.woocommerce.android.ui.dashboard.domain.ObserveLastUpdate
import com.woocommerce.android.ui.dashboard.stats.GetSelectedDateRange
import com.woocommerce.android.ui.mystore.data.CustomDateRangeDataStore
import com.woocommerce.android.ui.prefs.privacy.banner.domain.ShouldShowPrivacyBanner
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.util.TimezoneProvider
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.apache.commons.text.StringEscapeUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.model.WCRevenueStatsModel
import org.wordpress.android.fluxc.store.WooCommerceStore
import org.wordpress.android.fluxc.utils.putIfNotNull
import org.wordpress.android.util.FormatUtils
import org.wordpress.android.util.PhotonUtils
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val networkStatus: NetworkStatus,
    private val resourceProvider: ResourceProvider,
    private val wooCommerceStore: WooCommerceStore,
    private val getStats: GetStats,
    private val getTopPerformers: GetTopPerformers,
    private val currencyFormatter: CurrencyFormatter,
    private val selectedSite: SelectedSite,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val usageTracksEventEmitter: DashboardStatsUsageTracksEventEmitter,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val dashboardTransactionLauncher: DashboardTransactionLauncher,
    private val timezoneProvider: TimezoneProvider,
    private val observeLastUpdate: ObserveLastUpdate,
    private val customDateRangeDataStore: CustomDateRangeDataStore,
    getSelectedDateRange: GetSelectedDateRange,
    shouldShowPrivacyBanner: ShouldShowPrivacyBanner
) : ScopedViewModel(savedState) {
    companion object {
        private const val DAYS_TO_REDISPLAY_JP_BENEFITS_BANNER = 5
        val SUPPORTED_RANGES_ON_MY_STORE_TAB = listOf(
            SelectionType.TODAY,
            SelectionType.WEEK_TO_DATE,
            SelectionType.MONTH_TO_DATE,
            SelectionType.YEAR_TO_DATE,
            SelectionType.CUSTOM
        )
    }

    val performanceObserver: LifecycleObserver = dashboardTransactionLauncher

    private var _revenueStatsState = MutableLiveData<RevenueStatsViewState>()
    val revenueStatsState: LiveData<RevenueStatsViewState> = _revenueStatsState

    private var _visitorStatsState = MutableLiveData<VisitorStatsViewState>()
    val visitorStatsState: LiveData<VisitorStatsViewState> = _visitorStatsState

    private var _topPerformersState = MutableLiveData<TopPerformersState>()
    val topPerformersState: LiveData<TopPerformersState> = _topPerformersState

    private var _hasOrders = MutableLiveData<OrderState>()
    val hasOrders: LiveData<OrderState> = _hasOrders

    private var _lastUpdateStats = MutableLiveData<Long?>()
    val lastUpdateStats: LiveData<Long?> = _lastUpdateStats

    private var _lastUpdateTopPerformers = MutableLiveData<Long?>()
    val lastUpdateTopPerformers: LiveData<Long?> = _lastUpdateTopPerformers

    private var _appbarState = MutableLiveData<AppbarState>()
    val appbarState: LiveData<AppbarState> = _appbarState

    private val refreshTrigger = MutableSharedFlow<RefreshState>(extraBufferCapacity = 1)

    val customRange = customDateRangeDataStore.dateRange.asLiveData()
    private val _selectedDateRange = getSelectedDateRange()
    val selectedDateRange: LiveData<StatsTimeRangeSelection> = _selectedDateRange.asLiveData()

    val storeName = selectedSite.observe().map { site ->
        if (!site?.displayName.isNullOrBlank()) {
            site?.displayName
        } else {
            site?.name
        } ?: resourceProvider.getString(R.string.store_name_default)
    }.asLiveData()

    init {
        ConnectionChangeReceiver.getEventBus().register(this)

        _topPerformersState.value = TopPerformersState(isLoading = true)

        viewModelScope.launch {
            combine(
                _selectedDateRange,
                refreshTrigger.onStart { emit(RefreshState()) }
            ) { selectedRange, refreshEvent ->
                Pair(selectedRange, refreshEvent.shouldRefresh)
            }.collectLatest { (selectedRange, isForceRefresh) ->
                coroutineScope {
                    launch { loadStoreStats(selectedRange, isForceRefresh) }
                    launch { loadTopPerformersStats(selectedRange, isForceRefresh) }
                }
            }
        }
        observeTopPerformerUpdates()
        trackLocalTimezoneDifferenceFromStore()

        launch {
            shouldShowPrivacyBanner().let {
                if (it) {
                    triggerEvent(DashboardEvent.ShowPrivacyBanner)
                }
            }
        }

        if (selectedSite.getOrNull()?.isEligibleForAI == true &&
            !appPrefsWrapper.wasAIProductDescriptionPromoDialogShown
        ) {
            triggerEvent(DashboardEvent.ShowAIProductDescriptionDialog)
            appPrefsWrapper.wasAIProductDescriptionPromoDialogShown = true
        }

        updateShareStoreButtonVisibility()
    }

    private fun updateShareStoreButtonVisibility() {
        _appbarState.value = AppbarState(showShareStoreButton = selectedSite.get().isSitePublic)
    }

    override fun onCleared() {
        ConnectionChangeReceiver.getEventBus().unregister(this)
        super.onCleared()
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: ConnectionChangeEvent) {
        if (event.isConnected) {
            refreshTrigger.tryEmit(RefreshState())
        }
    }

    fun onTabSelected(selectionType: SelectionType) {
        usageTracksEventEmitter.interacted()
        appPrefsWrapper.setActiveStatsTab(selectionType.name)

        if (selectionType == SelectionType.CUSTOM) {
            analyticsTrackerWrapper.track(
                AnalyticsEvent.DASHBOARD_STATS_CUSTOM_RANGE_TAB_SELECTED
            )
        }
    }

    fun onPullToRefresh() {
        usageTracksEventEmitter.interacted()
        analyticsTrackerWrapper.track(AnalyticsEvent.DASHBOARD_PULLED_TO_REFRESH)
        refreshTrigger.tryEmit(RefreshState(isForced = true))
    }

    fun onViewAnalyticsClicked() {
        AnalyticsTracker.track(AnalyticsEvent.DASHBOARD_SEE_MORE_ANALYTICS_TAPPED)
        selectedDateRange.value?.let {
            triggerEvent(DashboardEvent.OpenAnalytics(it))
        }
    }

    fun onShareStoreClicked() {
        AnalyticsTracker.track(AnalyticsEvent.DASHBOARD_SHARE_YOUR_STORE_BUTTON_TAPPED)
        triggerEvent(
            DashboardEvent.ShareStore(storeUrl = selectedSite.get().url)
        )
    }

    private suspend fun loadStoreStats(selectedRange: StatsTimeRangeSelection, forceRefresh: Boolean) {
        if (!networkStatus.isConnected()) {
            _revenueStatsState.value = RevenueStatsViewState.Content(null, selectedRange)
            _visitorStatsState.value = VisitorStatsViewState.NotLoaded
            return
        }
        _revenueStatsState.value = RevenueStatsViewState.Loading
        getStats(forceRefresh, selectedRange)
            .collect {
                when (it) {
                    is LoadStatsResult.RevenueStatsSuccess -> onRevenueStatsSuccess(it, selectedRange)
                    is LoadStatsResult.RevenueStatsError -> _revenueStatsState.value = RevenueStatsViewState.GenericError
                    LoadStatsResult.PluginNotActive -> _revenueStatsState.value = RevenueStatsViewState.PluginNotActiveError
                    is LoadStatsResult.VisitorsStatsSuccess -> _visitorStatsState.value = VisitorStatsViewState.Content(
                        stats = it.stats, totalVisitorCount = it.totalVisitorCount
                    )

                    is LoadStatsResult.VisitorsStatsError -> _visitorStatsState.value = VisitorStatsViewState.Error
                    is LoadStatsResult.VisitorStatUnavailable -> onVisitorStatsUnavailable(it.connectionType)
                    is LoadStatsResult.HasOrders -> _hasOrders.value = if (it.hasOrder) AtLeastOne else Empty
                }
                dashboardTransactionLauncher.onStoreStatisticsFetched()
            }
        launch {
            observeLastUpdate(
                selectedRange,
                listOf(
                    AnalyticsUpdateDataStore.AnalyticData.REVENUE,
                    AnalyticsUpdateDataStore.AnalyticData.VISITORS
                )
            ).collect { lastUpdateMillis -> _lastUpdateStats.value = lastUpdateMillis }
        }
        launch {
            observeLastUpdate(
                selectedRange,
                AnalyticsUpdateDataStore.AnalyticData.TOP_PERFORMERS
            ).collect { lastUpdateMillis -> _lastUpdateTopPerformers.value = lastUpdateMillis }
        }
    }

    private fun onRevenueStatsSuccess(
        result: LoadStatsResult.RevenueStatsSuccess,
        selectedRange: StatsTimeRangeSelection
    ) {
        _revenueStatsState.value = RevenueStatsViewState.Content(
            result.stats?.toStoreStatsUiModel(),
            selectedRange
        )
        analyticsTrackerWrapper.track(
            AnalyticsEvent.DASHBOARD_MAIN_STATS_LOADED,
            buildMap {
                put(AnalyticsTracker.KEY_RANGE, selectedRange.selectionType.identifier)
                putIfNotNull(AnalyticsTracker.KEY_ID to result.stats?.rangeId)
            }
        )
    }

    private fun onVisitorStatsUnavailable(connectionType: SiteConnectionType) {
        val daysSinceDismissal = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - appPrefsWrapper.getJetpackBenefitsDismissalDate()
        )
        val supportsJetpackInstallation = connectionType == SiteConnectionType.JetpackConnectionPackage ||
            connectionType == SiteConnectionType.ApplicationPasswords
        val showBanner = supportsJetpackInstallation && daysSinceDismissal >= DAYS_TO_REDISPLAY_JP_BENEFITS_BANNER
        val benefitsBanner = JetpackBenefitsBannerUiModel(
            show = showBanner,
            onDismiss = {
                _visitorStatsState.value =
                    VisitorStatsViewState.Unavailable(JetpackBenefitsBannerUiModel(show = false))
                appPrefsWrapper.recordJetpackBenefitsDismissal()
                analyticsTrackerWrapper.track(
                    stat = AnalyticsEvent.FEATURE_JETPACK_BENEFITS_BANNER,
                    properties = mapOf(AnalyticsTracker.KEY_JETPACK_BENEFITS_BANNER_ACTION to "dismissed")
                )
            }
        )
        _visitorStatsState.value = VisitorStatsViewState.Unavailable(benefitsBanner)
    }

    private suspend fun loadTopPerformersStats(selectedRange: StatsTimeRangeSelection, forceRefresh: Boolean) {
        if (!networkStatus.isConnected()) return

        _topPerformersState.value = _topPerformersState.value?.copy(isLoading = true, isError = false)
        val result = getTopPerformers.fetchTopPerformers(selectedRange, forceRefresh)
        result.fold(
            onFailure = { _topPerformersState.value = _topPerformersState.value?.copy(isError = true) },
            onSuccess = {
                analyticsTrackerWrapper.track(
                    AnalyticsEvent.DASHBOARD_TOP_PERFORMERS_LOADED,
                    mapOf(AnalyticsTracker.KEY_RANGE to selectedRange.selectionType.identifier)
                )
            }
        )
        _topPerformersState.value = _topPerformersState.value?.copy(isLoading = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTopPerformerUpdates() {
        viewModelScope.launch {
            _selectedDateRange
                .flatMapLatest { dateRange ->
                    getTopPerformers.observeTopPerformers(dateRange)
                }
                .collectLatest {
                    _topPerformersState.value = _topPerformersState.value?.copy(
                        topPerformers = it.toTopPerformersUiList(),
                    )
                }
        }
    }

    private fun trackLocalTimezoneDifferenceFromStore() {
        val selectedSite = selectedSite.getIfExists() ?: return
        val siteTimezone = selectedSite.timezone.takeIf { it.isNotNullOrEmpty() } ?: return
        val localTimeZoneOffset = timezoneProvider.deviceTimezone.offsetInHours.toString()

        val shouldTriggerTimezoneTrack = appPrefsWrapper.isTimezoneTrackEventNeverTriggeredFor(
            siteId = selectedSite.siteId,
            localTimezone = localTimeZoneOffset,
            storeTimezone = siteTimezone
        ) && selectedSite.timezone != localTimeZoneOffset

        if (shouldTriggerTimezoneTrack) {
            analyticsTrackerWrapper.track(
                stat = DASHBOARD_STORE_TIMEZONE_DIFFER_FROM_DEVICE,
                properties = mapOf(
                    AnalyticsTracker.KEY_STORE_TIMEZONE to siteTimezone,
                    AnalyticsTracker.KEY_LOCAL_TIMEZONE to localTimeZoneOffset
                )
            )
            appPrefsWrapper.setTimezoneTrackEventTriggeredFor(
                siteId = selectedSite.siteId,
                localTimezone = localTimeZoneOffset,
                storeTimezone = siteTimezone
            )
        }
    }

    private fun onTopPerformerSelected(productId: Long) {
        triggerEvent(DashboardEvent.OpenTopPerformer(productId))
        analyticsTrackerWrapper.track(AnalyticsEvent.TOP_EARNER_PRODUCT_TAPPED)
        usageTracksEventEmitter.interacted()
    }

    private fun WCRevenueStatsModel.toStoreStatsUiModel(): RevenueStatsUiModel {
        val totals = parseTotal()
        return RevenueStatsUiModel(
            intervalList = getIntervalList().toStatsIntervalUiModelList(),
            totalOrdersCount = totals?.ordersCount,
            totalSales = totals?.totalSales,
            currencyCode = wooCommerceStore.getSiteSettings(selectedSite.get())?.currencyCode,
            rangeId = rangeId
        )
    }

    private fun List<WCRevenueStatsModel.Interval>.toStatsIntervalUiModelList() =
        map {
            StatsIntervalUiModel(
                it.interval,
                it.subtotals?.ordersCount,
                it.subtotals?.totalSales
            )
        }

    private fun List<TopPerformerProduct>.toTopPerformersUiList() = map { it.toTopPerformersUiModel() }

    private fun TopPerformerProduct.toTopPerformersUiModel() =
        TopPerformerProductUiModel(
            productId = productId,
            name = StringEscapeUtils.unescapeHtml4(name),
            timesOrdered = FormatUtils.formatDecimal(quantity),
            netSales = resourceProvider.getString(
                R.string.dashboard_top_performers_net_sales,
                getTotalSpendFormatted(total.toBigDecimal(), currency)
            ),
            imageUrl = imageUrl?.toImageUrl(),
            onClick = ::onTopPerformerSelected
        )

    private fun getTotalSpendFormatted(totalSpend: BigDecimal, currency: String) =
        currencyFormatter.formatCurrency(
            totalSpend,
            wooCommerceStore.getSiteSettings(selectedSite.get())?.currencyCode ?: currency
        )

    private fun String.toImageUrl() =
        PhotonUtils.getPhotonImageUrl(
            this,
            resourceProvider.getDimensionPixelSize(R.dimen.image_minor_100),
            0
        )

    fun onCustomRangeSelected(range: StatsTimeRange) {
        analyticsTrackerWrapper.track(
            AnalyticsEvent.DASHBOARD_STATS_CUSTOM_RANGE_CONFIRMED,
            mapOf(
                AnalyticsTracker.KEY_IS_EDITING to (customRange.value != null),
            )
        )

        if (selectedDateRange.value?.selectionType != SelectionType.CUSTOM) {
            onTabSelected(SelectionType.CUSTOM)
        }
        viewModelScope.launch {
            customDateRangeDataStore.updateDateRange(range)
        }
    }

    fun onAddCustomRangeClicked() {
        triggerEvent(
            DashboardEvent.OpenDatePicker(
                fromDate = customRange.value?.start ?: Date(),
                toDate = customRange.value?.end ?: Date()
            )
        )

        val event = if (customRange.value == null) {
            AnalyticsEvent.DASHBOARD_STATS_CUSTOM_RANGE_ADD_BUTTON_TAPPED
        } else {
            AnalyticsEvent.DASHBOARD_STATS_CUSTOM_RANGE_EDIT_BUTTON_TAPPED
        }
        analyticsTrackerWrapper.track(event)
    }

    sealed class RevenueStatsViewState {
        data object Loading : RevenueStatsViewState()
        data object GenericError : RevenueStatsViewState()
        data object PluginNotActiveError : RevenueStatsViewState()
        data class Content(
            val revenueStats: RevenueStatsUiModel?,
            val statsRangeSelection: StatsTimeRangeSelection
        ) : RevenueStatsViewState()
    }

    sealed class VisitorStatsViewState {
        data object Error : VisitorStatsViewState()
        data object NotLoaded : VisitorStatsViewState()
        data class Unavailable(
            val benefitsBanner: JetpackBenefitsBannerUiModel
        ) : VisitorStatsViewState()

        data class Content(
            val stats: Map<String, Int>,
            val totalVisitorCount: Int?
        ) : VisitorStatsViewState()
    }

    data class TopPerformersState(
        val isLoading: Boolean = false,
        val isError: Boolean = false,
        val topPerformers: List<TopPerformerProductUiModel> = emptyList(),
    )

    sealed class OrderState {
        data object Empty : OrderState()
        data object AtLeastOne : OrderState()
    }

    data class AppbarState(
        val showShareStoreButton: Boolean = false,
    )

    sealed class DashboardEvent : MultiLiveEvent.Event() {
        data class OpenTopPerformer(
            val productId: Long
        ) : DashboardEvent()

        data class OpenAnalytics(val analyticsPeriod: StatsTimeRangeSelection) : DashboardEvent()

        data object ShowPrivacyBanner : DashboardEvent()

        data object ShowAIProductDescriptionDialog : DashboardEvent()

        data class ShareStore(val storeUrl: String) : DashboardEvent()

        data class OpenDatePicker(val fromDate: Date, val toDate: Date) : DashboardEvent()
    }

    data class RefreshState(private val isForced: Boolean = false) {
        /**
         * [shouldRefresh] will be true only the first time the refresh event is consulted and when
         * isForced is initialized on true. Once the event is handled the property will change its value to false
         */
        var shouldRefresh: Boolean = isForced
            private set
            get(): Boolean {
                val result = field
                if (field) {
                    field = false
                }
                return result
            }
    }
}
