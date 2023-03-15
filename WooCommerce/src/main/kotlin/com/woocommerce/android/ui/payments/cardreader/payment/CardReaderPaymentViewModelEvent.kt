package com.woocommerce.android.ui.payments.cardreader.payment

import androidx.annotation.StringRes
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event

class ShowSnackbarInDialog(@StringRes val message: Int) : Event()

object PlayChaChing : Event()

object InteracRefundSuccessful : Event()