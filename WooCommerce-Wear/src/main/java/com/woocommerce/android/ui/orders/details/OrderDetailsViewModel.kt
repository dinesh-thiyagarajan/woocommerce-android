package com.woocommerce.android.ui.orders.details

import android.os.Parcelable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.extensions.getStateFlow
import com.woocommerce.android.extensions.toWearOrder
import com.woocommerce.android.ui.NavArgs.ORDER_ID
import com.woocommerce.android.ui.login.LoginRepository
import com.woocommerce.android.ui.orders.FormatOrderData
import com.woocommerce.android.ui.orders.FormatOrderData.OrderItem
import com.woocommerce.android.ui.orders.OrdersRepository
import com.woocommerce.android.ui.orders.details.FetchOrderProducts.OrderProductsRequest.Error
import com.woocommerce.android.ui.orders.details.FetchOrderProducts.OrderProductsRequest.Finished
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.SiteModel
import javax.inject.Inject

@HiltViewModel
class OrderDetailsViewModel @Inject constructor(
    private val fetchOrderProducts: FetchOrderProducts,
    private val ordersRepository: OrdersRepository,
    private val formatOrderData: FormatOrderData,
    private val loginRepository: LoginRepository,
    savedState: SavedStateHandle
) : ScopedViewModel(savedState) {
    private val orderId = savedState.get<Long>(ORDER_ID.key) ?: 0

    private val _viewState = savedState.getStateFlow(
        scope = this,
        initialValue = ViewState()
    )
    val viewState = _viewState.asLiveData()

    init {
        _viewState.update { it.copy(isLoading = true) }
        loginRepository.selectedSiteFlow
            .filterNotNull()
            .onEach { requestProductsData(it) }
            .launchIn(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        reloadData()
    }

    fun reloadData() {
        if (_viewState.value.isLoading) return
        _viewState.update { it.copy(isLoading = true) }
        launch {
            loginRepository.selectedSite?.let { requestProductsData(it) }
        }
    }

    private suspend fun requestProductsData(site: SiteModel) {
        fetchOrderProducts(site, orderId)
            .map { productsRequest ->
                when (productsRequest) {
                    is Finished -> Pair(site, productsRequest.products)
                    is Error -> Pair(site, null)
                    else -> null
                }
            }.filterNotNull().map { (site, products) ->
                ordersRepository.getOrderFromId(site, orderId)
                    ?.toWearOrder()
                    ?.let { formatOrderData(site, it, products) }
            }.onEach {
                presentOrderData(it)
            }.launchIn(this)
    }

    private fun presentOrderData(order: OrderItem?) {
        _viewState.update {
            it.copy(isLoading = false, orderItem = order)
        }
    }

    @Parcelize
    data class ViewState(
        val isLoading: Boolean = false,
        val orderItem: OrderItem? = null,
    ) : Parcelable
}
