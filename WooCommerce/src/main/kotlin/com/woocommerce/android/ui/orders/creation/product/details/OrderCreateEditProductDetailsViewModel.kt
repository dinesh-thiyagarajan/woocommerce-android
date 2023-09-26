package com.woocommerce.android.ui.orders.creation.product.details

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.model.Order
import com.woocommerce.android.ui.orders.creation.OrderCreationProduct
import com.woocommerce.android.ui.orders.creation.product.discount.CalculateItemDiscountAmount
import com.woocommerce.android.ui.orders.creation.product.discount.GetItemDiscountAmountText
import com.woocommerce.android.util.CurrencyFormatter
import com.woocommerce.android.util.getStockText
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ExitWithResult
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.parcelize.Parcelize
import org.wordpress.android.util.HtmlUtils
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class OrderCreateEditProductDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
    private val currencyFormatter: CurrencyFormatter,
    private val getItemDiscountAmountText: GetItemDiscountAmountText,
    private val calculateItemDiscountAmount: CalculateItemDiscountAmount,
    private val tracker: AnalyticsTrackerWrapper,
) : ScopedViewModel(savedState) {
    private val args: OrderCreateEditProductDetailsFragmentArgs by savedState.navArgs()

    private val currency = args.currency

    private val product: MutableStateFlow<OrderCreationProduct> =
        savedState.getStateFlow(viewModelScope, args.product, "key_item")

    val viewState = product.map { product ->
        ViewState(
            discountEditButtonsEnabled = args.discountEditEnabled,
            productDetailsState = ProductDetailsState(
                title = getProductTitle(product.item.name),
                stockPriceSubtitle = getStockPriceSubtitle(product),
                skuSubtitle = getSkuSubtitle(product.item.sku),
                imageUrl = product.productInfo.imageUrl,
            ),
            discountSectionState = DiscountSectionState(
                isVisible = product.item.isDiscounted(),
                discountAmountText = getItemDiscountAmountText(calculateItemDiscountAmount(product.item), currency),
            ),
            configurationButtonVisible = product.isConfigurable()
        )
    }.asLiveData()

    private fun Order.Item.isDiscounted(): Boolean = calculateItemDiscountAmount(this) > BigDecimal.ZERO

    private fun getStockPriceSubtitle(item: OrderCreationProduct): String {
        val decimalFormatter = getDecimalFormatter(currencyFormatter, args.currency)
        return buildString {
            if (item.item.isVariation && item.item.attributesDescription.isNotEmpty()) {
                append(item.item.attributesDescription)
            } else {
                append(item.getStockText(resourceProvider))
            }
            append(BULLET_SEPARATOR_CHAR)
            append(decimalFormatter(item.item.total).replace(" ", NON_BREAKING_SPACE))
        }
    }

    private fun getDecimalFormatter(
        currencyFormatter: CurrencyFormatter,
        currencyCode: String? = null
    ): (BigDecimal) -> String {
        return currencyCode?.let {
            currencyFormatter.buildBigDecimalFormatter(it)
        } ?: currencyFormatter.buildBigDecimalFormatter()
    }

    private fun getSkuSubtitle(sku: String): String =
        resourceProvider.getString(
            R.string.orderdetail_product_lineitem_sku_value,
            sku
        )

    private fun getProductTitle(productName: String): String =
        if (productName.isEmpty()) {
            productName
        } else {
            HtmlUtils.fastStripHtml(productName)
        }

    fun onAddDiscountClicked() {
        triggerEvent(NavigationTarget.DiscountEdit(this.product.value, currency))
        tracker.track(AnalyticsEvent.ORDER_PRODUCT_DISCOUNT_ADD_BUTTON_TAPPED)
    }

    fun onEditDiscountClicked() {
        triggerEvent(NavigationTarget.DiscountEdit(this.product.value, currency))
        tracker.track(AnalyticsEvent.ORDER_PRODUCT_DISCOUNT_EDIT_BUTTON_TAPPED)
    }

    fun onCloseClicked() {
        triggerEvent(MultiLiveEvent.Event.Exit)
    }

    fun onRemoveProductClicked() {
        triggerEvent(ExitWithResult(ProductDetailsEditResult.ProductRemoved(this.product.value.item)))
    }

    data class ViewState(
        val productDetailsState: ProductDetailsState,
        val discountSectionState: DiscountSectionState,
        val addDiscountButtonVisible: Boolean = !discountSectionState.isVisible,
        val discountEditButtonsEnabled: Boolean,
        val configurationButtonVisible: Boolean
    )

    data class ProductDetailsState(
        val title: String,
        val stockPriceSubtitle: String,
        val skuSubtitle: String,
        val imageUrl: String,
    )

    data class DiscountSectionState(
        val isVisible: Boolean,
        val discountAmountText: String,
    )

    @Parcelize
    sealed class ProductDetailsEditResult : Parcelable {
        @Parcelize
        data class ProductRemoved(val item: Order.Item) : Parcelable, ProductDetailsEditResult()
    }

    sealed class NavigationTarget : MultiLiveEvent.Event() {
        data class DiscountEdit(val item: OrderCreationProduct, val currency: String) : MultiLiveEvent.Event()
    }

    private companion object {
        private const val BULLET_SEPARATOR_CHAR = " \u2022 "
        private const val NON_BREAKING_SPACE = "\u00A0"
    }
}
