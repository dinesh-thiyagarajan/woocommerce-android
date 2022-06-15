package com.woocommerce.android.ui.orders.creation.customerlist

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.AppConstants
import com.woocommerce.android.R
import com.woocommerce.android.model.Address
import com.woocommerce.android.model.AmbiguousLocation
import com.woocommerce.android.model.Location
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.orders.details.editing.address.AddressViewModel
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.model.customer.WCCustomerModel
import org.wordpress.android.fluxc.model.order.OrderAddress
import javax.inject.Inject

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val networkStatus: NetworkStatus,
    private val customerListRepository: CustomerListRepository
) : ScopedViewModel(savedState) {
    private val _viewState = MutableLiveData<CustomerListViewState>()
    val viewState: LiveData<CustomerListViewState> = _viewState

    private var searchJob: Job? = null

    init {
        _viewState.value = CustomerListViewState()
    }

    fun onCustomerClick(customer: CustomerListItem) {
        triggerEvent(MultiLiveEvent.Event.Exit)

        launch {
            customerListRepository.getCustomer(customer.remoteId)?.let { wcCustomer ->
                val shippingAddress = OrderAddress.Shipping(
                    company = wcCustomer.shippingCompany,
                    address1 = wcCustomer.shippingAddress1,
                    address2 = wcCustomer.shippingAddress2,
                    city = wcCustomer.shippingCity,
                    firstName = wcCustomer.shippingFirstName,
                    lastName = wcCustomer.shippingLastName,
                    country = wcCustomer.shippingCountry,
                    state = wcCustomer.shippingState,
                    postcode = wcCustomer.shippingPostcode,
                    phone = ""
                )
                val billingAddress = OrderAddress.Billing(
                    company = wcCustomer.billingCompany,
                    address1 = wcCustomer.billingAddress1,
                    address2 = wcCustomer.billingAddress2,
                    city = wcCustomer.billingCity,
                    firstName = wcCustomer.billingFirstName,
                    lastName = wcCustomer.billingLastName,
                    country = wcCustomer.billingCountry,
                    state = wcCustomer.billingState,
                    postcode = wcCustomer.billingPostcode,
                    phone = wcCustomer.billingPhone,
                    email = wcCustomer.billingEmail
                )

                triggerEvent(
                    AddressViewModel.CustomerSelected(
                        shippingAddress = shippingAddress.toAddressModel(),
                        billingAddress = billingAddress.toAddressModel()
                    )
                )
            }
        }
    }

    private fun OrderAddress.toAddressModel(): Address {
        return Address(
            company = company,
            lastName = lastName,
            firstName = firstName,
            address1 = address1,
            address2 = address2,
            email = if (this is OrderAddress.Billing) {
                this.email
            } else {
                ""
            },
            postcode = postcode,
            phone = phone,
            country = Location.EMPTY, // TODO nbradbury
            state = AmbiguousLocation.Raw(state),
            city = city
        )
    }

    fun onSearchQueryChanged(query: String?) {
        if (query != null && query.length > 2) {
            // cancel any existing search, then start a new one after a brief delay so we don't
            // actually perform the fetch until the user stops typing
            searchJob?.cancel()
            searchJob = launch {
                delay(AppConstants.SEARCH_TYPING_DELAY_MS)
                searchCustomerList(query)
            }
        } else {
            launch {
                searchJob?.cancelAndJoin()
                _viewState.value = _viewState.value?.copy(
                    customers = emptyList(),
                    searchQuery = "",
                )
            }
        }
    }

    private suspend fun searchCustomerList(query: String) {
        if (networkStatus.isConnected()) {
            _viewState.value = _viewState.value?.copy(
                isSkeletonShown = true,
                searchQuery = query
            )
            val customers = ArrayList<CustomerListItem>()
            customerListRepository.searchCustomerList(query)?.forEach {
                customers.add(it.toUiModel())
            }
            _viewState.value = _viewState.value?.copy(
                isSkeletonShown = false,
                customers = customers
            )
        } else {
            triggerEvent(MultiLiveEvent.Event.ShowSnackbar(R.string.offline_error))
        }
    }

    @Parcelize
    data class CustomerListItem(
        val remoteId: Long,
        val firstName: String,
        val lastName: String,
        val email: String,
        val avatarUrl: String
    ) : Parcelable

    @Parcelize
    data class CustomerListViewState(
        val customers: List<CustomerListItem> = emptyList(),
        val isSkeletonShown: Boolean = false,
        val searchQuery: String = ""
    ) : Parcelable
}

private fun WCCustomerModel.toUiModel() =
    CustomerListViewModel.CustomerListItem(
        remoteId = remoteCustomerId,
        firstName = firstName,
        lastName = lastName,
        email = email,
        avatarUrl = avatarUrl
    )
