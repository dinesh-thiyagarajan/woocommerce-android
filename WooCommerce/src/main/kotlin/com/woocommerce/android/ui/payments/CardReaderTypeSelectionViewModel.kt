package com.woocommerce.android.ui.payments

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderType
import com.woocommerce.android.ui.payments.taptopay.IsTapToPayAvailable
import com.woocommerce.android.ui.payments.taptopay.IsTapToPayEnabled
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CardReaderTypeSelectionViewModel
@Inject constructor(
    savedState: SavedStateHandle,
    isTapToPayAvailable: IsTapToPayAvailable,
    isTapToPayEnabled: IsTapToPayEnabled
) : ScopedViewModel(savedState) {
    private val navArgs: CardReaderTypeSelectionDialogFragmentArgs by savedState.navArgs()

    init {
        if (!isTapToPayAvailable(navArgs.countryCode, isTapToPayEnabled)) {
            onUseBluetoothReaderSelected()
        }
    }

    fun onUseTapToPaySelected() {
        navigateToConnectionFlow(CardReaderType.BUILT_IN)
    }

    fun onUseBluetoothReaderSelected() {
        navigateToConnectionFlow(CardReaderType.EXTERNAL)
    }

    private fun navigateToConnectionFlow(cardReaderType: CardReaderType) {
        _event.value = NavigateToCardReaderPaymentFlow(
            navArgs.cardReaderFlowParam,
            cardReaderType
        )
    }

    data class NavigateToCardReaderPaymentFlow(
        val cardReaderFlowParam: CardReaderFlowParam,
        val cardReaderType: CardReaderType,
    ) : MultiLiveEvent.Event()
}
