package com.woocommerce.android.ui.jetpack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.ui.jetpack.benefits.JetpackActivationEligibilityErrorViewModel
import com.woocommerce.android.ui.jetpack.benefits.JetpackActivationEligibilityErrorViewModel.OpenUrlEvent
import com.woocommerce.android.ui.jetpack.benefits.JetpackActivationEligibilityErrorViewModel.StartJetpackActivationForApplicationPasswords
import com.woocommerce.android.ui.login.LoginEmailHelpDialogFragment.Listener
import com.woocommerce.android.ui.main.AppBarStatus
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.Exit
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class JetpackActivationEligibilityErrorFragment : BaseFragment(), Listener {
    override val activityAppBarStatus: AppBarStatus
        get() = AppBarStatus.Hidden

    private val viewModel: JetpackActivationEligibilityErrorViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                WooThemeWithBackground {
                    JetpackActivationEligibilityErrorScreen(viewModel)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.event.observe(this) { event ->
            when (event) {
                is StartJetpackActivationForApplicationPasswords -> {
                    findNavController().navigateSafely(
                        JetpackActivationEligibilityErrorFragmentDirections
                            .actionJetpackActivationEligibilityErrorFragmentToJetpackActivation(
                                siteUrl = event.siteUrl,
                                jetpackStatus = event.jetpackStatus,
                            )
                    )
                }
                is OpenUrlEvent -> ChromeCustomTabUtils.launchUrl(requireContext(), event.url)
                is Exit -> findNavController().popBackStack()
            }
        }
    }

    override fun onEmailNeedMoreHelpClicked() {
        TODO("Not yet implemented")
    }
}
