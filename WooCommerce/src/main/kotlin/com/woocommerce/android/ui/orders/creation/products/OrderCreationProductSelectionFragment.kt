package com.woocommerce.android.ui.orders.creation.products

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentOrderCreationProductSelectionBinding
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.orders.creation.OrderCreationViewModel
import com.woocommerce.android.ui.products.OnLoadMoreListener
import com.woocommerce.android.ui.products.ProductListAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OrderCreationProductSelectionFragment :
    BaseFragment(R.layout.fragment_order_creation_product_selection),
    OnLoadMoreListener {
    private val sharedViewModel by hiltNavGraphViewModels<OrderCreationViewModel>(R.id.nav_graph_order_creations)
    private val productListViewModel by viewModels<OrderCreationProductSelectionViewModel>()

    private var currentAdapter: ProductListAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(FragmentOrderCreationProductSelectionBinding.bind(view)) {
            productsList.layoutManager = LinearLayoutManager(requireActivity())
            productsList.adapter = ProductListAdapter(
                clickListener = ::onProductClick,
                loadMoreListener = this@OrderCreationProductSelectionFragment
            ).apply { currentAdapter = this }
        }
        setupObservers()
    }

    private fun setupObservers() {
        productListViewModel.productListData.observe(viewLifecycleOwner) {
            currentAdapter?.setProductList(it)
        }
    }

    private fun onProductClick(remoteProductId: Long, sharedView: View?) {

    }

    override fun onRequestLoadMore() {
    }
}
