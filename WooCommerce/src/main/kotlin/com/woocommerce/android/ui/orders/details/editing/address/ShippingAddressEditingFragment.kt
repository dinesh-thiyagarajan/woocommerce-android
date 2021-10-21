package com.woocommerce.android.ui.orders.details.editing.address

import android.view.View
import com.google.android.material.switchmaterial.SwitchMaterial
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentBaseEditAddressBinding
import com.woocommerce.android.model.Address

class ShippingAddressEditingFragment : BaseAddressEditingFragment() {
    override val analyticsValue: String = AnalyticsTracker.ORDER_EDIT_SHIPPING_ADDRESS

    override val storedAddress: Address
        get() = sharedViewModel.order.shippingAddress

    override fun saveChanges() = sharedViewModel.updateShippingAddress(addressDraft)

    override fun onViewBound(binding: FragmentBaseEditAddressBinding) {
        bindReplicateAddressSwitchView(binding.replicateAddressSwitch)
        binding.email.visibility = View.GONE
        binding.addressSectionHeader.text = getString(R.string.order_detail_shipping_address_section)
    }

    override fun getFragmentTitle() = getString(R.string.order_detail_shipping_address_section)

    private fun bindReplicateAddressSwitchView(switch: SwitchMaterial) {
        switch.visibility = View.VISIBLE
        sharedViewModel.order.billingAddress.copy(email = "")
            .bindAsAddressReplicationToggleState()
    }
}
