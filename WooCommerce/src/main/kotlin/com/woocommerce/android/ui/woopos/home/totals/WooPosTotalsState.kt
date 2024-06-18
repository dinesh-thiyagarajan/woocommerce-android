package com.woocommerce.android.ui.woopos.home.totals

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WooPosTotalsState(
    val orderId: Long?,
    val isCollectPaymentButtonEnabled: Boolean,
    var orderTotal: java.math.BigDecimal,
    var orderSubtotal: java.math.BigDecimal,
    var orderTax: java.math.BigDecimal,
    var isLoading: Boolean
) : Parcelable
