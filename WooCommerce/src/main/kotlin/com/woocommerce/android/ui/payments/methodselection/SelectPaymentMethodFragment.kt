package com.woocommerce.android.ui.payments.methodselection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentSelectPaymentMethodBinding
import com.woocommerce.android.extensions.exhaustive
import com.woocommerce.android.extensions.handleDialogNotice
import com.woocommerce.android.extensions.handleDialogResult
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.dialog.WooDialog
import com.woocommerce.android.ui.main.MainActivity.Companion.BackPressListener
import com.woocommerce.android.ui.payments.cardreader.connect.CardReaderConnectDialogFragment
import com.woocommerce.android.ui.payments.cardreader.payment.CardReaderPaymentDialogFragment
import com.woocommerce.android.ui.payments.methodselection.SelectPaymentMethodViewState.Loading
import com.woocommerce.android.ui.payments.methodselection.SelectPaymentMethodViewState.Success
import com.woocommerce.android.ui.payments.scantopay.ScanToPayDialogFragment
import com.woocommerce.android.ui.payments.taptopay.summary.TapToPaySummaryFragment
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.util.UiHelpers
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowDialog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectPaymentMethodFragment : BaseFragment(R.layout.fragment_select_payment_method), BackPressListener {
    private val viewModel: SelectPaymentMethodViewModel by viewModels()
    private val sharePaymentUrlLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onSharePaymentUrlCompleted()
    }

    private var _binding: FragmentSelectPaymentMethodBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectPaymentMethodBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpObservers(binding)
        setupResultHandlers()
    }

    private fun setUpObservers(binding: FragmentSelectPaymentMethodBinding) {
        handleViewState(binding)
        handleEvents(binding)
    }

    private fun handleViewState(binding: FragmentSelectPaymentMethodBinding) {
        viewModel.viewStateData.observe(viewLifecycleOwner) { state ->
            when (state) {
                Loading -> renderLoadingState(binding)
                is Success -> renderSuccessfulState(binding, state)
            }.exhaustive
        }
    }

    private fun renderLoadingState(binding: FragmentSelectPaymentMethodBinding) {
        binding.container.isVisible = false
        binding.pbLoading.isVisible = true
        binding.learnMoreIppPaymentMethodsTv.learnMore.isVisible = false
    }

    private fun renderSuccessfulState(
        binding: FragmentSelectPaymentMethodBinding,
        state: Success
    ) {
        binding.container.isVisible = true
        binding.pbLoading.isVisible = false
        binding.learnMoreIppPaymentMethodsTv.learnMore.isVisible = true
        requireActivity().title = getString(
            R.string.simple_payments_take_payment_button,
            state.orderTotal
        )

        binding.textCash.setOnClickListener {
            viewModel.onCashPaymentClicked()
        }

        with(binding.clTapToPay) {
            isVisible = state.isPaymentCollectableWithTapToPay
            setOnClickListener {
                viewModel.onTapToPayClicked()
            }
        }

        with(binding.clCardReader) {
            isVisible = state.isPaymentCollectableWithExternalCardReader
            setOnClickListener {
                viewModel.onBtReaderClicked()
            }
        }

        with(binding.textShare) {
            isVisible = state.paymentUrl.isNotEmpty()
            setOnClickListener {
                viewModel.onSharePaymentUrlClicked()
            }
        }

        with(binding.tvScanToPay) {
            isVisible = state.isScanToPayAvailable && state.paymentUrl.isNotEmpty()
            setOnClickListener { viewModel.onScanToPayClicked() }
        }
        binding.dividerAfterScanToPay.isVisible = state.isScanToPayAvailable

        with(binding.learnMoreIppPaymentMethodsTv) {
            learnMore.setOnClickListener { state.learMoreIpp.onClick.invoke() }
            UiHelpers.setTextOrHide(binding.learnMoreIppPaymentMethodsTv.learnMore, state.learMoreIpp.label)
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun handleEvents(binding: FragmentSelectPaymentMethodBinding) {
        viewModel.event.observe(
            viewLifecycleOwner
        ) { event ->
            when (event) {
                is ShowDialog -> {
                    event.showDialog()
                }

                is ShowSnackbar -> {
                    Snackbar.make(
                        binding.container,
                        event.message,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                }

                is SharePaymentUrl -> {
                    sharePaymentUrl(event.storeName, event.paymentUrl)
                }

                is SharePaymentUrlViaQr -> {
                    val action =
                        SelectPaymentMethodFragmentDirections
                            .actionSelectPaymentMethodFragmentToScanToPayDialogFragment(
                                event.paymentUrl
                            )
                    findNavController().navigate(action)
                }

                is NavigateToCardReaderPaymentFlow -> {
                    val action =
                        SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToCardReaderPaymentFlow(
                            event.cardReaderFlowParam,
                            event.cardReaderType
                        )
                    findNavController().navigate(action)
                }

                is NavigateToCardReaderHubFlow -> {
                    val action =
                        SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToCardReaderHubFlow(
                            event.cardReaderFlowParam
                        )
                    findNavController().navigate(action)
                }

                is NavigateToCardReaderRefundFlow -> {
                    val action =
                        SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToCardReaderRefundFlow(
                            event.cardReaderFlowParam,
                            event.cardReaderType
                        )
                    findNavController().navigate(action)
                }

                is NavigateBackToOrderList -> {
                    val action = SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToOrderList()
                    findNavController().navigateSafely(action)
                }

                is NavigateBackToHub -> {
                    val action =
                        SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToCardReaderHubFragment(
                            event.cardReaderFlowParam
                        )
                    findNavController().navigateSafely(action)
                }

                is OpenGenericWebView -> {
                    ChromeCustomTabUtils.launchUrl(requireContext(), event.url)
                }

                is NavigateToOrderDetails -> {
                    val action =
                        SelectPaymentMethodFragmentDirections.actionSelectPaymentMethodFragmentToOrderDetailFragment(
                            orderId = event.orderId
                        )
                    findNavController().navigateSafely(action)
                }

                is NavigateToTapToPaySummary -> {
                    findNavController().navigateSafely(
                        SelectPaymentMethodFragmentDirections
                            .actionSelectPaymentMethodFragmentToTapToPaySummaryFragment(
                                TapToPaySummaryFragment.TestTapToPayFlow.AfterPayment(
                                    order = event.order
                                )
                            )
                    )
                }
            }
        }
    }

    private fun setupResultHandlers() {
        handleDialogResult<Boolean>(
            key = CardReaderConnectDialogFragment.KEY_CONNECT_TO_READER_RESULT,
            entryId = R.id.selectPaymentMethodFragment
        ) { connected ->
            viewModel.onConnectToReaderResultReceived(connected)
        }

        handleDialogNotice(
            key = CardReaderPaymentDialogFragment.KEY_CARD_PAYMENT_RESULT,
            entryId = R.id.selectPaymentMethodFragment
        ) {
            viewModel.onCardReaderPaymentCompleted()
        }

        handleDialogNotice(
            key = ScanToPayDialogFragment.KEY_SCAN_TO_PAY_RESULT,
            entryId = R.id.selectPaymentMethodFragment
        ) {
            viewModel.onScanToPayCompleted()
        }
    }

    private fun sharePaymentUrl(storeName: String, paymentUrl: String) {
        val title = getString(R.string.simple_payments_share_payment_dialog_title, storeName)
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, paymentUrl)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        sharePaymentUrlLauncher.launch(Intent.createChooser(shareIntent, title))
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onStop() {
        super.onStop()
        WooDialog.onCleared()
    }

    override fun onRequestAllowBackPress(): Boolean {
        viewModel.onBackPressed()
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
