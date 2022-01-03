package com.woocommerce.android.ui.orders.creation

import com.woocommerce.android.model.Order
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event

sealed class OrderCreationNavigationTarget : Event() {
    object EditCustomer : OrderCreationNavigationTarget()
    object EditCustomerNote : OrderCreationNavigationTarget()
    object AddSimpleProduct : OrderCreationNavigationTarget()
    data class ShowProductDetails(val item: Order.Item) : OrderCreationNavigationTarget()
}