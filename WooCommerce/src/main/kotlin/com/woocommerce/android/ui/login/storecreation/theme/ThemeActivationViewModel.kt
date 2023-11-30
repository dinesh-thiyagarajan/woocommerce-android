package com.woocommerce.android.ui.login.storecreation.theme

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R
import com.woocommerce.android.ui.login.storecreation.theme.ThemeActivationViewModel.ViewState.LoadingState
import com.woocommerce.android.ui.themes.ThemeRepository
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeActivationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val themeRepository: ThemeRepository,
    private val appPrefsWrapper: AppPrefsWrapper
) : ScopedViewModel(savedStateHandle) {
    private val _viewState = MutableStateFlow<ViewState>(LoadingState)
    val viewState = _viewState.asLiveData()

    private val args by savedStateHandle.navArgs<ThemeActivationFragmentDialogArgs>()

    init {
        startThemeInstallation()
    }

    private fun startThemeInstallation() = launch {
        _viewState.value = LoadingState
        themeRepository.activateTheme(args.themeId).fold(
            onSuccess = {
                appPrefsWrapper.clearThemeIdForStoreCreation()
                triggerEvent(Event.ShowSnackbar(R.string.theme_activated_successfully))
                triggerEvent(Event.Exit)
            },
            onFailure = { _viewState.value = ViewState.ErrorState(onRetry = ::startThemeInstallation) }
        )
    }

    sealed interface ViewState {
        object LoadingState : ViewState
        data class ErrorState(val onRetry: () -> Unit) : ViewState
    }
}
