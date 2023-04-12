package com.woocommerce.android.ui.products

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.model.BundledProduct
import com.woocommerce.android.viewmodel.LiveDataDelegate
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class ProductBundleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    getBundledProducts: GetBundledProducts,
    refreshBundledProducts: RefreshBundledProducts
) : ScopedViewModel(savedState) {
    private val navArgs: ProductBundleFragmentArgs by savedState.navArgs()

    private val _productList = MutableLiveData<List<BundledProduct>>()
    val productList: LiveData<List<BundledProduct>> = _productList

    val productListViewStateData = LiveDataDelegate(savedState, BundledProductListViewState(isSkeletonShown = true))
    private var productListViewState by productListViewStateData

    init {
        productListViewState = productListViewState.copy(isSkeletonShown = true)
        launch {
            refreshBundledProducts(navArgs.productId)
            getBundledProducts(navArgs.productId)
                .distinctUntilChanged()
                .collectLatest { bundledProducts ->
                    _productList.value = bundledProducts
                    productListViewState = productListViewState.copy(isSkeletonShown = false)
                }
        }
    }

    @Parcelize
    data class BundledProductListViewState(val isSkeletonShown: Boolean? = null) : Parcelable
}
