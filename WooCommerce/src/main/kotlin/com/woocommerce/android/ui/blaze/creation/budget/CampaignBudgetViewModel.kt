package com.woocommerce.android.ui.blaze.creation.budget

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.ui.blaze.BlazeRepository
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CampaignBudgetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    blazeRepository: BlazeRepository
) : ScopedViewModel(savedStateHandle) {
}
