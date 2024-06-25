package com.woocommerce.android.ui.woopos.home.cart

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.R
import com.woocommerce.android.model.Product
import com.woocommerce.android.ui.woopos.home.ChildToParentEvent
import com.woocommerce.android.ui.woopos.home.ChildToParentEvent.OrderDraftCreated
import com.woocommerce.android.ui.woopos.home.ParentToChildrenEvent
import com.woocommerce.android.ui.woopos.home.WooPosChildrenToParentEventSender
import com.woocommerce.android.ui.woopos.home.WooPosParentToChildrenEventReceiver
import com.woocommerce.android.ui.woopos.util.format.WooPosFormatPrice
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.getStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WooPosCartViewModel @Inject constructor(
    private val childrenToParentEventSender: WooPosChildrenToParentEventSender,
    private val parentToChildrenEventReceiver: WooPosParentToChildrenEventReceiver,
    private val repository: WooPosCartRepository,
    private val resourceProvider: ResourceProvider,
    private val wooPosFormatPrice: WooPosFormatPrice,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = savedState.getStateFlow(
        scope = viewModelScope,
        initialValue = WooPosCartState(),
        key = "cartViewState"
    )

    val state: LiveData<WooPosCartState> = _state
        .asLiveData()
        .map { updateToolbarState(it) }
        .map { updateStateDependingOnCartStatus(it) }

    init {
        listenEventsFromParent()
    }

    @Suppress("ReturnCount")
    fun onUIEvent(event: WooPosCartUIEvent) {
        when (event) {
            is WooPosCartUIEvent.CheckoutClicked -> {
                goToTotals()
                createOrderDraft()
            }

            is WooPosCartUIEvent.ItemRemovedFromCart -> {
                val currentState = _state.value
                if (currentState.isOrderCreationInProgress) return

                _state.value = currentState.copy(itemsInCart = currentState.itemsInCart - event.item)
            }

            WooPosCartUIEvent.BackClicked -> {
                val currentState = _state.value
                if (currentState.cartStatus == WooPosCartStatus.IN_PROGRESS) {
                    return
                }
                _state.value = currentState.copy(cartStatus = WooPosCartStatus.IN_PROGRESS)

                sendEventToParent(ChildToParentEvent.BackFromCheckoutToCartClicked)
            }

            WooPosCartUIEvent.ClearAllClicked -> {
                val currentState = _state.value
                if (currentState.isOrderCreationInProgress) return

                _state.value = currentState.copy(
                    itemsInCart = emptyList()
                )
            }
        }
    }

    private fun goToTotals() {
        sendEventToParent(ChildToParentEvent.CheckoutClicked)
        _state.value = _state.value.copy(cartStatus = WooPosCartStatus.CHECKOUT)
    }

    private fun createOrderDraft() {
        viewModelScope.launch {
            val currentState = _state.value
            _state.value = currentState.copy(isOrderCreationInProgress = true)

            val result = repository.createOrderWithProducts(
                productIds = currentState.itemsInCart.map { it.id.productId }
            )

            _state.value = _state.value.copy(isOrderCreationInProgress = false)

            result.fold(
                onSuccess = { order ->
                    Log.d("WooPosCartViewModel", "Order created successfully - $order")
                    childrenToParentEventSender.sendToParent(OrderDraftCreated(order.id))
                },
                onFailure = { error ->
                    Log.e("WooPosCartViewModel", "Order creation failed - $error")
                }
            )
        }
    }

    private fun listenEventsFromParent() {
        viewModelScope.launch {
            parentToChildrenEventReceiver.events.collect { event ->
                when (event) {
                    is ParentToChildrenEvent.BackFromCheckoutToCartClicked -> {
                        _state.value = _state.value.copy(cartStatus = WooPosCartStatus.IN_PROGRESS)
                    }

                    is ParentToChildrenEvent.ItemClickedInProductSelector -> {
                        if (_state.value.isOrderCreationInProgress) return@collect

                        val itemClicked = viewModelScope.async {
                            repository.getProductById(event.productId)?.toCartListItem(
                                orderNumber = _state.value.itemsInCart.size + 1
                            )!!
                        }

                        val currentState = _state.value
                        _state.value = currentState.copy(
                            itemsInCart = currentState.itemsInCart + itemClicked.await()
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun updateToolbarState(newState: WooPosCartState): WooPosCartState {
        val itemsCount = if (newState.itemsInCart.isNotEmpty()) {
            resourceProvider.getString(
                R.string.woo_pos_items_in_cart,
                newState.itemsInCart.size
            )
        } else {
            ""
        }
        val newToolbar = when (newState.cartStatus) {
            WooPosCartStatus.IN_PROGRESS -> {
                WooPosToolbar(
                    icon = R.drawable.ic_shopping_cart,
                    itemsCount = itemsCount,
                    isClearAllButtonVisible = newState.itemsInCart.isNotEmpty()
                )
            }

            WooPosCartStatus.CHECKOUT -> {
                WooPosToolbar(
                    icon = R.drawable.ic_back_24dp,
                    itemsCount = itemsCount,
                    isClearAllButtonVisible = false
                )
            }
        }
        return newState.copy(toolbar = newToolbar)
    }

    private fun updateStateDependingOnCartStatus(newState: WooPosCartState) =
        when (newState.cartStatus) {
            WooPosCartStatus.IN_PROGRESS -> {
                newState.copy(
                    areItemsRemovable = true,
                    isCheckoutButtonVisible = true,
                )
            }

            WooPosCartStatus.CHECKOUT -> {
                newState.copy(
                    areItemsRemovable = false,
                    isCheckoutButtonVisible = false,
                )
            }
        }

    private fun sendEventToParent(event: ChildToParentEvent) {
        viewModelScope.launch {
            childrenToParentEventSender.sendToParent(event)
        }
    }

    private suspend fun Product.toCartListItem(orderNumber: Int): WooPosCartListItem =
        WooPosCartListItem(
            id = WooPosCartListItem.Id(productId = remoteId, orderNumber = orderNumber),
            name = name,
            price = wooPosFormatPrice(price),
            imageUrl = firstImageUrl
        )
}
