package com.woocommerce.android.ui.login.jetpack.wpcom

import android.os.Parcelable
import androidx.core.util.PatternsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.woocommerce.android.R
import com.woocommerce.android.ui.login.MagicLinkFlow
import com.woocommerce.android.ui.login.MagicLinkSource
import com.woocommerce.android.ui.login.WPComLoginRepository
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.getStateFlow
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.util.GravatarUtils
import javax.inject.Inject

@HiltViewModel
class JetpackActivationMagicLinkRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
    private val wpComLoginRepository: WPComLoginRepository
) : ScopedViewModel(savedStateHandle) {

    private val navArgs: JetpackActivationMagicLinkRequestFragmentArgs by savedStateHandle.navArgs()

    private val _viewState = savedStateHandle.getStateFlow<ViewState>(
        scope = viewModelScope,
        initialValue = ViewState.MagicLinkRequestState(
            emailOrUsername = navArgs.emailOrUsername,
            avatarUrl = avatarUrlFromEmail(navArgs.emailOrUsername),
            isJetpackInstalled = navArgs.jetpackStatus.isJetpackInstalled,
            isLoadingDialogShown = false
        )
    )
    val viewState = _viewState.asLiveData()

    init {
        requestMagicLink()
    }

    fun onRequestMagicLinkClick() = requestMagicLink()

    fun onOpenEmailClientClick() {
        triggerEvent(OpenEmailClient)
    }

    fun onCloseClick() {
        triggerEvent(Exit)
    }

    private fun requestMagicLink() = launch {
        _viewState.value = ViewState.MagicLinkRequestState(
            emailOrUsername = navArgs.emailOrUsername,
            avatarUrl = avatarUrlFromEmail(navArgs.emailOrUsername),
            isJetpackInstalled = navArgs.jetpackStatus.isJetpackInstalled,
            isLoadingDialogShown = true
        )
        val source = when {
            !navArgs.jetpackStatus.isJetpackInstalled -> MagicLinkSource.JetpackInstallation
            !navArgs.jetpackStatus.isJetpackConnected -> MagicLinkSource.JetpackConnection
            else -> MagicLinkSource.WPComAuthentication
        }
        wpComLoginRepository.requestMagicLink(
            emailOrUsername = navArgs.emailOrUsername,
            flow = MagicLinkFlow.SiteCredentialsToWPCom,
            source = source
        ).fold(
            onSuccess = {
                _viewState.value = ViewState.MagicLinkSentState(
                    email = navArgs.emailOrUsername.takeIf { it.isAnEmail() },
                    isJetpackInstalled = navArgs.jetpackStatus.isJetpackInstalled,
                )
            },
            onFailure = {
                _viewState.update { (it as ViewState.MagicLinkRequestState).copy(isLoadingDialogShown = false) }
                triggerEvent(ShowSnackbar(R.string.error_generic))
            }
        )
    }

    private fun avatarUrlFromEmail(email: String): String {
        val avatarSize = resourceProvider.getDimensionPixelSize(R.dimen.image_minor_100)
        return GravatarUtils.gravatarFromEmail(email, avatarSize, GravatarUtils.DefaultImage.STATUS_404)
    }

    private fun String.isAnEmail() = PatternsCompat.EMAIL_ADDRESS.matcher(this).matches()

    sealed interface ViewState : Parcelable {
        val isJetpackInstalled: Boolean

        @Parcelize
        data class MagicLinkRequestState(
            val emailOrUsername: String,
            val avatarUrl: String,
            override val isJetpackInstalled: Boolean,
            val isLoadingDialogShown: Boolean
        ) : ViewState

        @Parcelize
        data class MagicLinkSentState(
            val email: String?,
            override val isJetpackInstalled: Boolean
        ) : ViewState
    }

    object OpenEmailClient : MultiLiveEvent.Event()
}
