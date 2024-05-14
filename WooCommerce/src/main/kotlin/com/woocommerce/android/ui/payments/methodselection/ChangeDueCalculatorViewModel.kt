package com.woocommerce.android.ui.payments.methodselection

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class ChangeDueCalculatorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderDetailRepository: OrderDetailRepository
) : ScopedViewModel(savedStateHandle) {

    private val orderId: Long = savedStateHandle.get<Long>("orderId")
        ?: throw IllegalArgumentException("OrderId is required")

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val amountDue: BigDecimal, val change: BigDecimal) : UiState()
        data object Error : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadOrderDetails()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun loadOrderDetails() {
        launch {
            try {
                val order = orderDetailRepository.getOrderById(orderId)
                if (order != null) {
                    _uiState.value = UiState.Success(amountDue = order.total, 0.00.toBigDecimal())
                } else {
                    _uiState.value = UiState.Error
                }
            } catch (e: IOException) {
                WooLog.e(
                    tag = WooLog.T.ORDERS,
                    message = "Error loading order details"
                )
                _uiState.value = UiState.Error
            }
        }
    }
}
