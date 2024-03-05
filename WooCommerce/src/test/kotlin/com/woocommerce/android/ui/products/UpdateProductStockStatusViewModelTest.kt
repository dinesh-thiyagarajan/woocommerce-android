package com.woocommerce.android.ui.products

import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.ui.products.UpdateProductStockStatusViewModel.ProductStockStatusInfo
import com.woocommerce.android.ui.products.UpdateProductStockStatusViewModel.StockStatusState
import com.woocommerce.android.ui.products.UpdateProductStockStatusViewModel.UpdateStockStatusExitState
import com.woocommerce.android.ui.products.UpdateProductStockStatusViewModel.UpdateStockStatusResult
import com.woocommerce.android.ui.products.UpdateProductStockStatusViewModel.UpdateStockStatusUiState
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ResourceProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateProductStockStatusViewModelTest : BaseUnitTest() {
    private val productListRepository: ProductListRepository = mock()
    private val analyticsTracker: AnalyticsTrackerWrapper = mock()

    private lateinit var viewModel: UpdateProductStockStatusViewModel
    private lateinit var resourceProvider: ResourceProvider

    @Test
    fun `given mixed stock statuses, when viewModel is initialized, then ui state reflects mixed status`() =
        testBlocking {
            // Given
            val stockStatusInfos = listOf(
                ProductStockStatusInfo(productId = 1L, stockStatus = ProductStockStatus.InStock, manageStock = false),
                ProductStockStatusInfo(productId = 2L, stockStatus = ProductStockStatus.OutOfStock, manageStock = false)
            )
            mockFetchStockStatuses(stockStatusInfos)
            setupViewModel(stockStatusInfos.map { it.productId })

            // When
            var state: UpdateStockStatusUiState? = null
            viewModel.viewState.observeForever { state = it }

            // Then
            assertThat(state?.currentStockStatusState).isInstanceOf(StockStatusState.Mixed::class.java)
        }

    @Test
    fun `given products with the same stock status, when viewModel is init, then state reflects common status`() =
        testBlocking {
            // Given
            val selectedProductIds = listOf(1L, 2L)
            val commonStockStatus = ProductStockStatus.InStock
            mockFetchStockStatuses(selectedProductIds, commonStockStatus, false)

            // When
            setupViewModel(selectedProductIds)

            var state: UpdateStockStatusUiState? = null
            viewModel.viewState.observeForever { state = it }

            // Then
            assertThat(state?.currentStockStatusState).isEqualTo(StockStatusState.Common(commonStockStatus))
        }

    @Test
    fun `given viewModel is init, when setCurrentStockStatus is called, Then UI state updates current stock status`() =
        testBlocking {
            // Given
            val selectedProductIds = listOf(1L)
            mockFetchStockStatuses(selectedProductIds, ProductStockStatus.InStock, false)

            setupViewModel(selectedProductIds)
            val newStockStatus = ProductStockStatus.OutOfStock

            // When
            viewModel.onStockStatusSelected(newStockStatus)

            var state: UpdateStockStatusUiState? = null
            viewModel.viewState.observeForever { state = it }

            // Then
            assertThat(state?.currentProductStockStatus).isEqualTo(newStockStatus)
        }

    @Test
    fun `given viewModel is init, when updateStockStatusForProducts is called then analytics event is tracked`() =
        testBlocking {
            // Given
            val selectedProductIds = listOf(1L, 2L)
            mockFetchStockStatuses(selectedProductIds, ProductStockStatus.InStock, false)
            setupViewModel(selectedProductIds)

            // When
            viewModel.onDoneButtonClicked()

            // Then
            verify(analyticsTracker).track(AnalyticsEvent.PRODUCT_STOCK_STATUSES_UPDATE_DONE_TAPPED)
        }

    @Test
    fun `when stock status is updated, then ExitWithResult event is dispatched`() = testBlocking {
        // Given
        val selectedProductIds = listOf(1L, 2L)
        mockFetchStockStatuses(selectedProductIds, ProductStockStatus.InStock, false)
        setupViewModel(selectedProductIds)

        mockBulkUpdateStockStatus(selectedProductIds, ProductStockStatus.InStock, UpdateStockStatusResult.Updated)

        var event: MultiLiveEvent.Event? = null
        viewModel.event.observeForever { event = it }

        // When
        viewModel.onDoneButtonClicked()

        // Then
        assertThat(event).isInstanceOf(MultiLiveEvent.Event.ExitWithResult::class.java)
        assertThat((event as MultiLiveEvent.Event.ExitWithResult<*>).data).isEqualTo(
            UpdateStockStatusExitState.Success
        )
    }

    @Test
    fun `when stock status update fails, then ExitWithResult event with Error is dispatched`() = testBlocking {
        // Given
        val selectedProductIds = listOf(1L, 2L)
        mockFetchStockStatuses(selectedProductIds, ProductStockStatus.InStock, false)
        setupViewModel(selectedProductIds)
        mockBulkUpdateStockStatus(selectedProductIds, ProductStockStatus.InStock, UpdateStockStatusResult.Error)

        var event: MultiLiveEvent.Event? = null
        viewModel.event.observeForever { event = it }

        // When
        viewModel.onDoneButtonClicked()

        // Then
        assertThat(event).isInstanceOf(MultiLiveEvent.Event.ExitWithResult::class.java)
        assertThat((event as MultiLiveEvent.Event.ExitWithResult<*>).data).isEqualTo(
            UpdateStockStatusExitState.Error
        )
    }

    @Test
    fun `when stock status update is managed products, then ExitWithResult event with Error is dispatched`() =
        testBlocking {
            // Given
            val selectedProductIds = listOf(1L, 2L)
            mockFetchStockStatuses(selectedProductIds, ProductStockStatus.InStock, false)
            setupViewModel(selectedProductIds)
            mockBulkUpdateStockStatus(
                selectedProductIds,
                ProductStockStatus.InStock,
                UpdateStockStatusResult.IsManagedProducts
            )

            var event: MultiLiveEvent.Event? = null
            viewModel.event.observeForever { event = it }

            // When
            viewModel.onDoneButtonClicked()

            // Then
            assertThat(event).isInstanceOf(MultiLiveEvent.Event.ExitWithResult::class.java)
            assertThat((event as MultiLiveEvent.Event.ExitWithResult<*>).data).isEqualTo(
                UpdateStockStatusExitState.Error
            )
        }

    @Test
    fun `when all products are eligible for update, correct status message is shown`() = testBlocking {
        // Given
        val stockStatusInfos = listOf(
            ProductStockStatusInfo(productId = 1L, stockStatus = ProductStockStatus.InStock, manageStock = false),
            ProductStockStatusInfo(productId = 2L, stockStatus = ProductStockStatus.OutOfStock, manageStock = false)
        )
        mockFetchStockStatusesWithManageStock(stockStatusInfos)
        val expectedMessage = "2 products will be updated."
        whenever(resourceProvider.getString(R.string.product_update_stock_status_update_count, 2)).thenReturn(
            expectedMessage
        )
        setupViewModel(stockStatusInfos.map { it.productId })

        // When
        var state: UpdateStockStatusUiState? = null
        viewModel.viewState.observeForever { state = it }

        // Then
        assertThat(state?.statusMessage).isEqualTo(expectedMessage)
    }

    @Test
    fun `when some products have managed stock, correct status message is shown`() = testBlocking {
        // Given
        val stockStatusInfos = listOf(
            ProductStockStatusInfo(productId = 1L, stockStatus = ProductStockStatus.InStock, manageStock = false),
            ProductStockStatusInfo(productId = 2L, stockStatus = ProductStockStatus.OutOfStock, manageStock = true)
        )
        mockFetchStockStatusesWithManageStock(stockStatusInfos)
        val expectedMessage = "1 product will be updated. 1 product will be ignored."
        whenever(
            resourceProvider.getString(
                R.string.product_update_stock_status_update_count,
                1
            )
        ).thenReturn("1 product will be updated.")
        whenever(
            resourceProvider.getString(
                R.string.product_update_stock_status_ignored_count,
                1
            )
        ).thenReturn("1 product will be ignored.")
        setupViewModel(stockStatusInfos.map { it.productId })

        // When
        var state: UpdateStockStatusUiState? = null
        viewModel.viewState.observeForever { state = it }

        // Then
        assertThat(state?.statusMessage).isEqualTo(expectedMessage)
    }

    private fun setupViewModel(selectedProductIds: List<Long>) {
        resourceProvider = mock {
            on { getString(R.string.product_update_stock_status_update_count, any()) } doAnswer { invocation ->
                val count = invocation.arguments[1] as Int
                "$count products will be updated."
            }
            on { getString(R.string.product_update_stock_status_ignored_count, any()) } doAnswer { invocation ->
                val count = invocation.arguments[1] as Int
                "$count products will be ignored."
            }
        }
        viewModel = UpdateProductStockStatusViewModel(
            savedStateHandle = UpdateProductStockStatusFragmentArgs(
                selectedProductIds = selectedProductIds.toLongArray()
            ).toSavedStateHandle(),
            productListRepository = productListRepository,
            analyticsTracker = analyticsTracker,
            resourceProvider = resourceProvider
        )
    }

    private suspend fun mockBulkUpdateStockStatus(
        selectedProductIds: List<Long>,
        stockStatus: ProductStockStatus,
        result: UpdateStockStatusResult
    ) {
        whenever(productListRepository.bulkUpdateStockStatus(selectedProductIds, stockStatus)).thenReturn(result)
    }

    private suspend fun mockFetchStockStatusesWithManageStock(
        stockStatusInfos: List<ProductStockStatusInfo>
    ) {
        val productIds = stockStatusInfos.map { it.productId }
        whenever(productListRepository.fetchStockStatuses(productIds)).thenReturn(stockStatusInfos)
    }

    private suspend fun mockFetchStockStatuses(
        selectedProductIds: List<Long>,
        stockStatus: ProductStockStatus,
        manageStock: Boolean
    ) {
        whenever(productListRepository.fetchStockStatuses(selectedProductIds)).thenReturn(
            selectedProductIds.map { id ->
                ProductStockStatusInfo(
                    productId = id,
                    stockStatus = stockStatus,
                    manageStock = manageStock
                )
            }
        )
    }

    private suspend fun mockFetchStockStatuses(stockStatusInfos: List<ProductStockStatusInfo>) {
        val productIds = stockStatusInfos.map { it.productId }
        whenever(productListRepository.fetchStockStatuses(productIds)).thenReturn(stockStatusInfos)
    }
}
