package com.woocommerce.android.ui.orders

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.FeedbackPrefs
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_IPP_BANNER_CAMPAIGN_NAME
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_IPP_BANNER_REMIND_LATER
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_IPP_BANNER_SOURCE
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_SCANNING_SOURCE
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.VALUE_IPP_BANNER_SOURCE_ORDER_LIST
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.extensions.NotificationReceivedEvent
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.model.FeatureFeedbackSettings
import com.woocommerce.android.model.RequestResult
import com.woocommerce.android.notifications.NotificationChannelType
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.orders.creation.CodeScanner
import com.woocommerce.android.ui.orders.creation.CodeScannerStatus
import com.woocommerce.android.ui.orders.creation.CodeScanningErrorType
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.ui.orders.filters.domain.GetSelectedOrderFiltersCount
import com.woocommerce.android.ui.orders.filters.domain.GetWCOrderListDescriptorWithFilters
import com.woocommerce.android.ui.orders.filters.domain.GetWCOrderListDescriptorWithFiltersAndSearchQuery
import com.woocommerce.android.ui.orders.list.OrderListItemIdentifier
import com.woocommerce.android.ui.orders.list.OrderListItemUIType
import com.woocommerce.android.ui.orders.list.OrderListRepository
import com.woocommerce.android.ui.orders.list.OrderListViewModel
import com.woocommerce.android.ui.orders.list.OrderListViewModel.IPPSurveyFeedbackBannerState
import com.woocommerce.android.ui.orders.list.OrderListViewModel.OrderListEvent.ShowErrorSnack
import com.woocommerce.android.ui.payments.feedback.ipp.GetIPPFeedbackBannerData
import com.woocommerce.android.ui.payments.feedback.ipp.ShouldShowFeedbackBanner
import com.woocommerce.android.util.getOrAwaitValue
import com.woocommerce.android.util.observeForTesting
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.widgets.WCEmptyView.EmptyViewType.NETWORK_ERROR
import com.woocommerce.android.widgets.WCEmptyView.EmptyViewType.NETWORK_OFFLINE
import com.woocommerce.android.widgets.WCEmptyView.EmptyViewType.ORDER_LIST
import com.woocommerce.android.widgets.WCEmptyView.EmptyViewType.ORDER_LIST_LOADING
import com.woocommerce.android.widgets.WCEmptyView.EmptyViewType.SEARCH_RESULTS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.WCOrderListDescriptor
import org.wordpress.android.fluxc.model.list.PagedListWrapper
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus
import org.wordpress.android.fluxc.store.ListStore
import org.wordpress.android.fluxc.store.WCOrderFetcher
import org.wordpress.android.fluxc.store.WCOrderStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
class OrderListViewModelTest : BaseUnitTest() {
    private val selectedSite: SelectedSite = mock()
    private val networkStatus: NetworkStatus = mock()
    private val orderListRepository: OrderListRepository = mock()
    private val orderDetailRepository: OrderDetailRepository = mock()
    private val dispatcher: Dispatcher = mock()
    private val orderStore: WCOrderStore = mock()
    private val resourceProvider: ResourceProvider = mock()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()

    private val orderStatusOptions = OrderTestUtils.generateOrderStatusOptionsMappedByStatus()
    private lateinit var viewModel: OrderListViewModel
    private val listStore: ListStore = mock()
    private val pagedListWrapper: PagedListWrapper<OrderListItemUIType> = mock()
    private val orderFetcher: WCOrderFetcher = mock()
    private val getWCOrderListDescriptorWithFilters: GetWCOrderListDescriptorWithFilters = mock()
    private val getWCOrderListDescriptorWithFiltersAndSearchQuery: GetWCOrderListDescriptorWithFiltersAndSearchQuery =
        mock()
    private val getSelectedOrderFiltersCount: GetSelectedOrderFiltersCount = mock()
    private val shouldShowFeedbackBanner: ShouldShowFeedbackBanner = mock()
    private val getIPPFeedbackBannerData: GetIPPFeedbackBannerData = mock()
    private val analyticsTracker: AnalyticsTrackerWrapper = mock()
    private val appPrefs = mock<AppPrefs>()
    private val feedbackPrefs = mock<FeedbackPrefs>()
    private val codeScanner = mock<CodeScanner>()

    @Before
    fun setup() = testBlocking {
        whenever(getWCOrderListDescriptorWithFilters.invoke()).thenReturn(WCOrderListDescriptor(site = mock()))
        whenever(getWCOrderListDescriptorWithFiltersAndSearchQuery.invoke(anyString())).thenReturn(
            WCOrderListDescriptor(
                site = mock()
            )
        )
        whenever(pagedListWrapper.listError).doReturn(mock())
        whenever(pagedListWrapper.isEmpty).doReturn(mock())
        whenever(pagedListWrapper.isFetchingFirstPage).doReturn(mock())
        whenever(pagedListWrapper.isLoadingMore).doReturn(mock())
        whenever(pagedListWrapper.data).doReturn(mock())
        whenever(
            listStore.getList<WCOrderListDescriptor, OrderListItemIdentifier, OrderListItemUIType>(
                listDescriptor = any(),
                dataSource = any(),
                lifecycle = any()
            )
        ).doReturn(pagedListWrapper)
        doReturn(true).whenever(networkStatus).isConnected()

        viewModel = createViewModel()
    }

    private fun createViewModel() = OrderListViewModel(
        savedState = savedStateHandle,
        dispatchers = coroutinesTestRule.testDispatchers,
        orderListRepository = orderListRepository,
        orderDetailRepository = orderDetailRepository,
        orderStore = orderStore,
        listStore = listStore,
        networkStatus = networkStatus,
        dispatcher = dispatcher,
        selectedSite = selectedSite,
        fetcher = orderFetcher,
        resourceProvider = resourceProvider,
        getWCOrderListDescriptorWithFilters = getWCOrderListDescriptorWithFilters,
        getWCOrderListDescriptorWithFiltersAndSearchQuery = getWCOrderListDescriptorWithFiltersAndSearchQuery,
        getSelectedOrderFiltersCount = getSelectedOrderFiltersCount,
        orderListTransactionLauncher = mock(),
        getIPPFeedbackBannerData = getIPPFeedbackBannerData,
        shouldShowFeedbackBanner = shouldShowFeedbackBanner,
        markFeedbackBannerAsDismissed = mock(),
        markFeedbackBannerAsDismissedForever = mock(),
        markFeedbackBannerAsCompleted = mock(),
        analyticsTracker = analyticsTracker,
        appPrefs = appPrefs,
        feedbackPrefs = feedbackPrefs,
        codeScanner = codeScanner,
    )

    @Test
    fun `Request to load new list fetches order status options and payment gateways if connected`() = testBlocking {
        clearInvocations(orderListRepository)
        viewModel.submitSearchOrFilter(ANY_SEARCH_QUERY)

        verify(viewModel.activePagedListWrapper, times(1))?.fetchFirstPage()
        verify(orderListRepository, times(1)).fetchPaymentGateways()
        verify(orderListRepository, times(1)).fetchOrderStatusOptionsFromApi()
    }

    @Test
    fun `Load orders activates list wrapper`() = testBlocking {
        doReturn(RequestResult.SUCCESS).whenever(orderListRepository).fetchPaymentGateways()

        viewModel.loadOrders()

        assertNotNull(viewModel.ordersPagedListWrapper)
        assertNotNull(viewModel.activePagedListWrapper)
        verify(viewModel.ordersPagedListWrapper, times(1))?.fetchFirstPage()
        verify(viewModel.ordersPagedListWrapper, times(1))?.invalidateData()
        assertEquals(viewModel.ordersPagedListWrapper, viewModel.activePagedListWrapper)
    }

    /**
     * Test for proper handling of a request to fetch orders and order status options
     * when the device is offline. This scenario should result in an "offline" snackbar
     * message being emitted via a [com.woocommerce.android.viewmodel.MultiLiveEvent.Event] and the
     * [OrderListViewModel.viewStateLiveData.isRefreshPending] variable set to true to trigger another
     * attempt once the device comes back online.
     */
    @Test
    fun `Request to fetch order status options while offline handled correctly`() = testBlocking {
        doReturn(false).whenever(networkStatus).isConnected()

        viewModel.fetchOrdersAndOrderDependencies()

        viewModel.event.getOrAwaitValue().let { event ->
            assertTrue(event is ShowErrorSnack)
            assertEquals(event.messageRes, R.string.offline_error)
        }

        var isRefreshPending = false
        viewModel.viewStateLiveData.observeForever { old, new ->
            new.isRefreshPending.takeIfNotEqualTo(old?.isRefreshPending) {
                isRefreshPending = it
            }
        }
        assertTrue(isRefreshPending)
    }

    /* Test order status options are emitted via [OrderListViewModel.orderStatusOptions]
    * once fetched, and verify expected methods are called the correct number of
    * times.
    */
    @Test
    fun `Request to fetch order status options emits options`() = testBlocking {
        doReturn(RequestResult.SUCCESS).whenever(orderListRepository).fetchOrderStatusOptionsFromApi()
        doReturn(orderStatusOptions).whenever(orderListRepository).getCachedOrderStatusOptions()

        clearInvocations(orderListRepository)
        viewModel.fetchOrderStatusOptions()

        verify(orderListRepository, times(1)).fetchOrderStatusOptionsFromApi()
        verify(orderListRepository, times(1)).getCachedOrderStatusOptions()
        assertEquals(orderStatusOptions, viewModel.orderStatusOptions.getOrAwaitValue())
    }

    @Test
    fun `Given network is connected, when fetching orders and dependencies, then load order status list from api`() =
        testBlocking {
            doReturn(true).whenever(networkStatus).isConnected()

            viewModel.fetchOrdersAndOrderDependencies()

            verify(orderListRepository).fetchOrderStatusOptionsFromApi()
        }

    /**
     * Test the logic that generates the "No orders yet" empty view for the ALL tab
     * is successful and verify the view is emitted via [OrderListViewModel.emptyViewType].
     *
     * This view gets generated when:
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isError = null
     * - viewModel.orderStatusFilter = ""
     * - viewModel.isSearching = false
     * - pagedListWrapper.isFetchingFirstPage = false
     * - pagedListWrapper.data != null
     * - There are NO orders in the db for the active store
     */
    @Test
    fun `Display 'No orders yet' empty view when no orders for site for ALL tab`() = testBlocking {
        viewModel.isSearching = false
        whenever(pagedListWrapper.data.value).doReturn(mock())
        whenever(pagedListWrapper.isEmpty.value).doReturn(true)
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(false)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()

        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNotNull(emptyView)
            assertEquals(emptyView, ORDER_LIST)
        }
    }

    /**
     * Test the logic that generates the "error fetching orders" empty list view for any tab
     * is successful and verify the view is emitted via [OrderListViewModel.emptyViewType].
     *
     * This view gets generated when:
     * - viewModel.isSearching = false
     * - viewModel.orderStatusFilter = ""
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isFetchingFirstPage = false
     * - pagedListWrapper.isError = TRUE
     */
    @Test
    fun `Display error empty view on fetch orders error when no cached orders`() = testBlocking {
        viewModel.isSearching = false

        whenever(pagedListWrapper.data.value).doReturn(mock())
        whenever(pagedListWrapper.isEmpty.value).doReturn(true)
        whenever(pagedListWrapper.listError.value).doReturn(mock())
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(false)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()

        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNotNull(emptyView)
            assertEquals(emptyView, NETWORK_ERROR)
        }
    }

    /**
     * Test the logic that generates the "device offline" empty error list view for any tab
     * is successful and verify the view is emitted via [OrderListViewModel.emptyViewType].
     *
     * This view gets generated when:
     * - networkStatus.isConnected = false
     * - viewModel.isSearching = false
     * - viewModel.orderStatusFilter = ""
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isFetchingFirstPage = false
     * - pagedListWrapper.isError = null
     */
    @Test
    fun `Display offline empty view when offline and list is empty`() = testBlocking {
        viewModel.isSearching = false
        doReturn(false).whenever(networkStatus).isConnected()
        whenever(pagedListWrapper.data.value).doReturn(mock())
        whenever(pagedListWrapper.isEmpty.value).doReturn(true)
        whenever(pagedListWrapper.listError.value).doReturn(null)
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(false)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()

        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNotNull(emptyView)
            assertEquals(emptyView, NETWORK_OFFLINE)
        }
    }

    /**
     * Test the logic that generates the "No matching orders" empty list view for search/filter
     * results is successful and verify the view is emitted via [OrderListViewModel.emptyViewType].
     *
     * This view gets generated when:
     * - viewModel.isSearching = true
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isFetchingFirstPage = false
     * - pagedListWrapper.isError = null
     */
    @Test
    fun `Display empty view for empty search result`() = testBlocking {
        viewModel.isSearching = true
        viewModel.searchQuery = "query"
        whenever(pagedListWrapper.data.value).doReturn(mock())
        whenever(pagedListWrapper.isEmpty.value).doReturn(true)
        whenever(pagedListWrapper.listError.value).doReturn(null)
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(false)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()

        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNotNull(emptyView)
            assertEquals(emptyView, SEARCH_RESULTS)
        }
    }

    /**
     * Test the logic that generates the Loading empty list view for any tab of the order list
     * is successful and verify the view is emitted via [OrderListViewModel.emptyViewType].
     *
     * This view gets generated when:
     * - viewModel.isSearching = false
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isFetchingFirstPage = true
     * - pagedListWrapper.isError = null
     */
    @Test
    fun `Display Loading empty view for any order list tab`() = testBlocking {
        viewModel.isSearching = false
        whenever(pagedListWrapper.isEmpty.value).doReturn(true)
        whenever(pagedListWrapper.listError.value).doReturn(null)
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(true)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()

        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNotNull(emptyView)
            assertEquals(emptyView, ORDER_LIST_LOADING)
        }
    }

    /**
     * Test the logic that generates the Loading empty list view while in search mode
     * and verify the empty view is *not* shown in this situation
     *
     * This view gets generated when:
     * - viewModel.isSearching = true
     * - pagedListWrapper.isEmpty = true
     * - pagedListWrapper.isFetchingFirstPage = true
     * - pagedListWrapper.isError = null
     */
    @Test
    fun `Does not display the Loading empty view in search mode`() = testBlocking {
        viewModel.isSearching = true
        whenever(pagedListWrapper.listError.value).doReturn(null)
        whenever(pagedListWrapper.isFetchingFirstPage.value).doReturn(true)

        viewModel.createAndPostEmptyViewType(pagedListWrapper)
        advanceUntilIdle()
        viewModel.emptyViewType.observeForTesting {
            // Verify
            val emptyView = viewModel.emptyViewType.value
            assertNull(emptyView)
        }
    }

    @Test
    fun `Payment gateways are fetched if network connected and variable set when successful`() = testBlocking {
        doReturn(RequestResult.SUCCESS).whenever(orderListRepository).fetchPaymentGateways()

        viewModel.fetchPaymentGateways()

        verify(orderListRepository, times(1)).fetchPaymentGateways()
        assertTrue(viewModel.viewState.arePaymentGatewaysFetched)
    }

    @Test
    fun `Payment gateways are not fetched if network not connected`() = testBlocking {
        doReturn(false).whenever(networkStatus).isConnected()

        viewModel.fetchPaymentGateways()

        verify(orderListRepository, times(0)).fetchPaymentGateways()
        assertFalse(viewModel.viewState.arePaymentGatewaysFetched)
    }

    @Test
    fun `Payment gateways are not fetched if already fetched and network connected`() = testBlocking {
        doReturn(RequestResult.SUCCESS).whenever(orderListRepository).fetchPaymentGateways()

        // Fetch the first time around
        viewModel.fetchPaymentGateways()
        verify(orderListRepository, times(1)).fetchPaymentGateways()
        assertTrue(viewModel.viewState.arePaymentGatewaysFetched)
        clearInvocations(orderListRepository)

        // Try to fetch a second time
        viewModel.fetchPaymentGateways()
        verify(orderListRepository, times(0)).fetchPaymentGateways()
        assertTrue(viewModel.viewState.arePaymentGatewaysFetched)
    }

    /**
     * Ideally, this shouldn't be required as NotificationMessageHandler.dispatchBackgroundEvents
     * dispatches events that will trigger fetching orders and updating UI state.
     *
     * This doesn't work for search queries though as they use custom [WCOrderListDescriptor]
     * which contains a search query and based on this UI is refreshed or not.
     *
     * ATM we'll just trigger [PagedListWrapper.fetchFirstPage]. It's not an issue as later
     * in the flow we use [WCOrderFetcher] which filters out requests that duplicate requests
     * of fetching order.
     */
    @Test
    fun `Request refresh for active list when received new order notification and is in search`() = testBlocking {
        viewModel.isSearching = true

        viewModel.submitSearchOrFilter(searchQuery = "Joe Doe")

        // Reset as we're no interested in previous invocations in this test
        reset(viewModel.activePagedListWrapper)
        viewModel.onNotificationReceived(
            NotificationReceivedEvent(NotificationChannelType.NEW_ORDER)
        )

        verify(viewModel.activePagedListWrapper)?.fetchFirstPage()
    }

    @Test
    fun `when the order is swiped then the status is changed optimistically`() = testBlocking {
        // Given that updateOrderStatus will success
        val order = OrderTestUtils.generateOrder()
        val position = 1
        val gesture = OrderStatusUpdateSource.SwipeToCompleteGesture(order.orderId, position, order.status)
        val result = WCOrderStore.OnOrderChanged()

        val updateFlow = flow {
            emit(WCOrderStore.UpdateOrderResult.OptimisticUpdateResult(WCOrderStore.OnOrderChanged()))
            delay(1_000)
            emit(WCOrderStore.UpdateOrderResult.RemoteUpdateResult(result))
        }

        whenever(resourceProvider.getString(R.string.orderlist_mark_completed_success, order.orderId))
            .thenReturn("Order #${order.orderId} marked as completed")
        whenever(orderDetailRepository.updateOrderStatus(order.orderId, CoreOrderStatus.COMPLETED.value))
            .thenReturn(updateFlow)

        // When the order is swiped
        viewModel.onSwipeStatusUpdate(gesture)

        // Then the order status is changed optimistically
        val optimisticChangeEvent = viewModel.event.getOrAwaitValue()
        assertTrue(optimisticChangeEvent is MultiLiveEvent.Event.ShowUndoSnackbar)

        advanceTimeBy(1_001)

        // Then when the order status changed nothing happens because it was already handled optimistically
        val resultEvent = viewModel.event.getOrAwaitValue()
        assertEquals(optimisticChangeEvent, resultEvent)
    }

    @Test
    fun `when the order is swiped but the change fails, then a retry message is shown`() = testBlocking {
        // Given that updateOrderStatus will fail
        val order = OrderTestUtils.generateOrder()
        val position = 1
        val gesture = OrderStatusUpdateSource.SwipeToCompleteGesture(order.orderId, position, order.status)
        val result = WCOrderStore.OnOrderChanged(orderError = WCOrderStore.OrderError())

        val updateFlow = flow {
            emit(WCOrderStore.UpdateOrderResult.OptimisticUpdateResult(WCOrderStore.OnOrderChanged()))
            delay(1_000)
            emit(WCOrderStore.UpdateOrderResult.RemoteUpdateResult(result))
        }

        whenever(resourceProvider.getString(R.string.orderlist_mark_completed_success, order.orderId))
            .thenReturn("Order #${order.orderId} marked as completed")
        whenever(resourceProvider.getString(R.string.orderlist_updating_order_error, order.orderId))
            .thenReturn("Error updating Order #${order.orderId}")
        whenever(orderDetailRepository.updateOrderStatus(order.orderId, CoreOrderStatus.COMPLETED.value))
            .thenReturn(updateFlow)

        // When the order is swiped
        viewModel.onSwipeStatusUpdate(gesture)

        // Then the order status is changed optimistically
        val optimisticChangeEvent = viewModel.event.getOrAwaitValue()
        assertTrue(optimisticChangeEvent is MultiLiveEvent.Event.ShowUndoSnackbar)

        advanceTimeBy(1_001)

        // Then when the order status change fails, the retry message is shown
        val resultEvent = viewModel.event.getOrAwaitValue()
        assertTrue(resultEvent is OrderListViewModel.OrderListEvent.ShowRetryErrorSnack)
    }

    @Test
    fun `given IPP banner should be shown, when ViewModel init, then Orders banner is hidden`() = testBlocking {
        // given
        whenever(shouldShowFeedbackBanner()).thenReturn(true)
        whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)

        // when
        viewModel = createViewModel()

        // then
        assertFalse(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
    }

    @Test
    fun `given IPP not shown and SP and Orders dismissed and TTP enabled, when ViewModel init, then JITM shown`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)
            val featureFeedbackSettings = mock<FeatureFeedbackSettings> {
                on { feedbackState }.thenReturn(FeatureFeedbackSettings.FeedbackState.DISMISSED)
            }
            whenever(
                feedbackPrefs.getFeatureFeedbackSettings(
                    FeatureFeedbackSettings.Feature.SIMPLE_PAYMENTS_AND_ORDER_CREATION
                )
            ).thenReturn(featureFeedbackSettings)
            whenever(appPrefs.isTapToPayEnabled).thenReturn(true)

            // when
            viewModel = createViewModel()

            // then
            assertThat(viewModel.viewState.jitmEnabled).isEqualTo(true)
        }

    @Test
    fun `given IPP not shown and SP and Orders dismissed and TTP disabled, when ViewModel init, then JITM is not shown`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)
            val featureFeedbackSettings = mock<FeatureFeedbackSettings> {
                on { feedbackState }.thenReturn(FeatureFeedbackSettings.FeedbackState.DISMISSED)
            }
            whenever(
                feedbackPrefs.getFeatureFeedbackSettings(
                    FeatureFeedbackSettings.Feature.SIMPLE_PAYMENTS_AND_ORDER_CREATION
                )
            ).thenReturn(featureFeedbackSettings)
            whenever(appPrefs.isTapToPayEnabled).thenReturn(false)

            // when
            viewModel = createViewModel()

            // then
            assertThat(viewModel.viewState.jitmEnabled).isEqualTo(false)
        }

    @Test
    fun `when onDismissOrderCreationSimplePaymentsFeedback called, then FEATURE_FEEDBACK_BANNER tracked`() =
        testBlocking {
            // when
            viewModel.onDismissOrderCreationSimplePaymentsFeedback()

            // then
            verify(analyticsTracker).track(
                AnalyticsEvent.FEATURE_FEEDBACK_BANNER,
                mapOf(
                    AnalyticsTracker.KEY_FEEDBACK_CONTEXT to AnalyticsTracker.VALUE_SIMPLE_PAYMENTS_FEEDBACK,
                    AnalyticsTracker.KEY_FEEDBACK_ACTION to AnalyticsTracker.VALUE_FEEDBACK_DISMISSED
                )
            )
        }

    @Test
    fun `when onDismissOrderCreationSimplePaymentsFeedback called, then order banner visibility changed`() =
        testBlocking {
            // given
            val featureFeedbackSettings = mock<FeatureFeedbackSettings> {
                on { feedbackState }.thenReturn(FeatureFeedbackSettings.FeedbackState.DISMISSED)
            }
            whenever(
                feedbackPrefs.getFeatureFeedbackSettings(
                    FeatureFeedbackSettings.Feature.SIMPLE_PAYMENTS_AND_ORDER_CREATION
                )
            ).thenReturn(featureFeedbackSettings)

            // when
            viewModel.onDismissOrderCreationSimplePaymentsFeedback()

            // then
            assertThat(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible).isEqualTo(false)
        }

    @Test
    fun `given IPP banner should be shown, when ViewModel init, then IPP banner is shown`() = testBlocking {
        // given
        whenever(shouldShowFeedbackBanner()).thenReturn(true)
        whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)

        // when
        viewModel = createViewModel()

        // then
        assertTrue(viewModel.viewState.ippFeedbackBannerState is IPPSurveyFeedbackBannerState.Visible)
    }

    @Test
    fun `given IPP banner should not be shown, when ViewModel init, then Orders banner is shown`() = testBlocking {
        // given
        whenever(shouldShowFeedbackBanner()).thenReturn(false)

        // when
        viewModel = createViewModel()

        // then
        assertTrue(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
    }

    @Test
    fun `given IPP banner should not be shown, when ViewModel init, then IPP banner is hidden`() = testBlocking {
        // given
        whenever(shouldShowFeedbackBanner()).thenReturn(false)

        // when
        viewModel = createViewModel()

        // then
        assertTrue(viewModel.viewState.ippFeedbackBannerState is IPPSurveyFeedbackBannerState.Hidden)
    }

    @Test
    fun `given IPP banner is shown, when user clicks dismiss button, then dismissal dialog shouLd be displayed`() =
        testBlocking {
            // given
            viewModel.viewState =
                viewModel.viewState.copy(
                    ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(FAKE_IPP_FEEDBACK_BANNER_DATA)
                )

            // when
            viewModel.onDismissIPPFeedbackBannerClicked()

            // then
            assertEquals(OrderListViewModel.OrderListEvent.ShowIPPDismissConfirmationDialog, viewModel.event.value)
        }

    @Test
    fun `given IPP banner is shown, when CTA is clicked, then survey is opened`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerCTAClicked()

        // then
        assertEquals(
            OrderListViewModel.OrderListEvent.OpenIPPFeedbackSurveyLink(FAKE_IPP_FEEDBACK_BANNER_DATA.url),
            viewModel.event.value
        )
    }

    @Test
    fun `given IPP banner is shown, when CTA is clicked, then IPP banner is hidden`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerCTAClicked()

        // then
        assertEquals(IPPSurveyFeedbackBannerState.Hidden, viewModel.viewState.ippFeedbackBannerState)
    }

    @Test
    fun `given IPP banner is shown, when CTA is clicked, then Orders banner is shown`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerCTAClicked()

        // then
        assertTrue(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
    }

    @Test
    fun `given IPP banner is shown, when dismiss forever is clicked, then IPP banner is hidden`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerDismissedForever()

        // then
        assertEquals(IPPSurveyFeedbackBannerState.Hidden, viewModel.viewState.ippFeedbackBannerState)
    }

    @Test
    fun `given IPP banner is shown, when dismiss forever is clicked, then Orders banner is shown`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerDismissedForever()

        // then
        assertTrue(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
    }

    @Test
    fun `given IPP banner is shown, when dismissed temporarily, then IPP banner is hidden`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerDismissedShowLater()

        // then
        assertEquals(IPPSurveyFeedbackBannerState.Hidden, viewModel.viewState.ippFeedbackBannerState)
    }

    @Test
    fun `given IPP banner is shown, when dismissed temporarily, then Orders banner is shown`() {
        // given
        viewModel.viewState =
            viewModel.viewState.copy(
                ippFeedbackBannerState = IPPSurveyFeedbackBannerState.Visible(
                    FAKE_IPP_FEEDBACK_BANNER_DATA
                )
            )

        // when
        viewModel.onIPPFeedbackBannerDismissedShowLater()

        // then
        assertTrue(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
    }

    @Test
    fun `given IPP banner is shown, when dismissed temporarily, then correct event is tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(true)
            whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)
            viewModel = createViewModel()

            // when
            viewModel.onIPPFeedbackBannerDismissedShowLater()

            // then
            verify(analyticsTracker).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_DISMISSED,
                mapOf(
                    KEY_IPP_BANNER_REMIND_LATER to true,
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner is shown, when dismissed forever, then correct event is tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(true)
            whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)
            viewModel = createViewModel()

            // when
            viewModel.onIPPFeedbackBannerDismissedForever()

            // then
            verify(analyticsTracker).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_DISMISSED,
                mapOf(
                    KEY_IPP_BANNER_REMIND_LATER to false,
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner is shown, when CTA clicked, then correct event is tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(true)
            whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)
            viewModel = createViewModel()

            // when
            viewModel.onIPPFeedbackBannerCTAClicked()

            // then
            verify(analyticsTracker).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_CTA_TAPPED,
                mapOf(
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner is not shown, then events are not tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)

            // when
            viewModel = createViewModel()

            // then
            verify(analyticsTracker, never()).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_SHOWN,
                mapOf(
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner is shown, then correct event is tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(true)
            whenever(getIPPFeedbackBannerData()).thenReturn(FAKE_IPP_FEEDBACK_BANNER_DATA)

            // when
            viewModel = createViewModel()

            // then
            verify(analyticsTracker).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_SHOWN,
                mapOf(
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner should be shown, when banner data is null, then event is not tracked`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)
            assertNull(getIPPFeedbackBannerData())

            // when
            viewModel = createViewModel()

            // then
            verify(analyticsTracker, never()).track(
                AnalyticsEvent.IPP_FEEDBACK_BANNER_SHOWN,
                mapOf(
                    KEY_IPP_BANNER_SOURCE to VALUE_IPP_BANNER_SOURCE_ORDER_LIST,
                    KEY_IPP_BANNER_CAMPAIGN_NAME to FAKE_IPP_FEEDBACK_BANNER_DATA.campaignName
                )
            )
        }

    @Test
    fun `given IPP banner should be shown, when banner data is null, then Orders banner is shown`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)
            assertNull(getIPPFeedbackBannerData())

            // when
            viewModel = createViewModel()

            // then
            assertTrue(viewModel.viewState.isSimplePaymentsAndOrderCreationFeedbackVisible)
        }

    @Test
    fun `given IPP banner should be shown, when banner data is null, then IPP banner is hidden`() =
        testBlocking {
            // given
            whenever(shouldShowFeedbackBanner()).thenReturn(false)
            assertNull(getIPPFeedbackBannerData())

            // when
            viewModel = createViewModel()

            // then
            assertEquals(IPPSurveyFeedbackBannerState.Hidden, viewModel.viewState.ippFeedbackBannerState)
        }

    // region barcode scanner

    @Test
    fun `when code scanner succeeds, then trigger proper event`() {
        whenever(codeScanner.startScan()).thenAnswer {
            flow<CodeScannerStatus> {
                emit(CodeScannerStatus.Success("12345"))
            }
        }

        viewModel = createViewModel()
        viewModel.onScanClicked()

        assertThat(viewModel.event.value).isInstanceOf(OrderListViewModel.OrderListEvent.OnBarcodeScanned::class.java)
    }

    @Test
    fun `when code scanner fails, then trigger proper event`() {
        whenever(codeScanner.startScan()).thenAnswer {
            flow<CodeScannerStatus> {
                emit(
                    CodeScannerStatus.Failure(
                        error = "Failed to recognize the barcode",
                        type = CodeScanningErrorType.NotFound
                    )
                )
            }
        }

        viewModel = createViewModel()
        viewModel.onScanClicked()

        assertThat(viewModel.event.value).isInstanceOf(
            OrderListViewModel.OrderListEvent.OnAddingProductViaScanningFailed::class.java
        )
    }

    @Test
    fun `when code scanner succeeds, then trigger event with proper sku`() {
        whenever(codeScanner.startScan()).thenAnswer {
            flow<CodeScannerStatus> {
                emit(CodeScannerStatus.Success("12345"))
            }
        }

        viewModel = createViewModel()
        viewModel.onScanClicked()

        assertThat(viewModel.event.value).isEqualTo(OrderListViewModel.OrderListEvent.OnBarcodeScanned("12345"))
    }

    @Test
    fun `when scan clicked, then track proper analytics event`() {
        viewModel = createViewModel()

        viewModel.onScanClicked()

        verify(analyticsTracker).track(AnalyticsEvent.ORDER_LIST_PRODUCT_BARCODE_SCANNING_TAPPED)
    }

    @Test
    fun `when scan success, then track proper analytics event`() {
        whenever(codeScanner.startScan()).thenAnswer {
            flow<CodeScannerStatus> {
                emit(CodeScannerStatus.Success("12345"))
            }
        }
        viewModel = createViewModel()

        viewModel.onScanClicked()

        verify(analyticsTracker).track(
            eq(AnalyticsEvent.BARCODE_SCANNING_SUCCESS),
            any()
        )
    }

    @Test
    fun `when scan success, then track analytics event with proper source`() {
        whenever(codeScanner.startScan()).thenAnswer {
            flow<CodeScannerStatus> {
                emit(CodeScannerStatus.Success("12345"))
            }
        }
        viewModel = createViewModel()

        viewModel.onScanClicked()

        verify(analyticsTracker).track(
            AnalyticsEvent.BARCODE_SCANNING_SUCCESS,
            mapOf(
                KEY_SCANNING_SOURCE to "order_list"
            )
        )
    }

    //endregion

    private companion object {
        const val ANY_SEARCH_QUERY = "search query"

        val FAKE_IPP_FEEDBACK_BANNER_DATA = GetIPPFeedbackBannerData.IPPFeedbackBanner(-1, -1, "", "")
    }
}
