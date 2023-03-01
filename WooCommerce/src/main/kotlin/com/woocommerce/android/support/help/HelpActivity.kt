package com.woocommerce.android.support.help

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.woocommerce.android.AppPrefs
import com.woocommerce.android.AppUrls
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_HELP_CONTENT_URL
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_SOURCE_FLOW
import com.woocommerce.android.analytics.AnalyticsTracker.Companion.KEY_SOURCE_STEP
import com.woocommerce.android.databinding.ActivityHelpBinding
import com.woocommerce.android.extensions.exhaustive
import com.woocommerce.android.extensions.serializable
import com.woocommerce.android.extensions.show
import com.woocommerce.android.support.SSRActivity
import com.woocommerce.android.support.SupportHelper
import com.woocommerce.android.support.TicketType
import com.woocommerce.android.support.WooLogViewerActivity
import com.woocommerce.android.support.ZendeskHelper
import com.woocommerce.android.support.help.HelpViewModel.ContactSupportEvent
import com.woocommerce.android.support.help.HelpViewModel.ContactSupportEvent.CreateTicket
import com.woocommerce.android.support.help.HelpViewModel.ContactSupportEvent.ShowLoading
import com.woocommerce.android.support.requests.SupportRequestFormActivity
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.login.localnotifications.LoginNotificationScheduler
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.util.FeatureFlag
import com.woocommerce.android.util.PackageUtils
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.SiteStore
import javax.inject.Inject

@AndroidEntryPoint
class HelpActivity : AppCompatActivity() {
    private val viewModel: HelpViewModel by viewModels()

    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var siteStore: SiteStore
    @Inject lateinit var supportHelper: SupportHelper
    @Inject lateinit var zendeskHelper: ZendeskHelper
    @Inject lateinit var selectedSite: SelectedSite
    @Inject lateinit var loginNotificationScheduler: LoginNotificationScheduler

    private lateinit var binding: ActivityHelpBinding

    private val originFromExtras by lazy {
        intent.extras?.serializable(ORIGIN_KEY) ?: HelpOrigin.UNKNOWN
    }

    private val extraTagsFromExtras by lazy {
        intent.extras?.getStringArrayList(EXTRA_TAGS_KEY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar.toolbar as Toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.contactContainer.setOnClickListener { viewModel.contactSupport(TicketType.General) }
        binding.identityContainer.setOnClickListener { showIdentityDialog(TicketType.General) }
        binding.myTicketsContainer.setOnClickListener { showZendeskTickets() }
        binding.faqContainer.setOnClickListener {
            val loginFlow = intent.extras?.getString(LOGIN_FLOW_KEY)
            val loginStep = intent.extras?.getString(LOGIN_STEP_KEY)

            if (loginFlow != null && loginStep != null) {
                showLoginHelpCenter(originFromExtras, loginFlow, loginStep)
            } else {
                showZendeskFaq()
            }
        }
        binding.appLogContainer.setOnClickListener { showApplicationLog() }
        if (userIsLoggedIn() && selectedSite.exists()) {
            binding.ssrContainer.show()
            binding.ssrContainer.setOnClickListener { showSSR() }
        }

        with(binding.contactPaymentsContainer) {
            setOnClickListener {
                viewModel.contactSupport(TicketType.Payments)
            }
        }

        binding.textVersion.text = getString(R.string.version_with_name_param, PackageUtils.getVersionName(this))

        /**
         * If the user taps on a Zendesk notification, we want to show them the `My Tickets` page. However, this
         * should only be triggered when the activity is first created, otherwise if the user comes back from
         * `My Tickets` and rotates the screen (or triggers the activity re-creation in any other way) it'll navigate
         * them to `My Tickets` again since the `originFromExtras` will still be [Origin.ZENDESK_NOTIFICATION].
         */
        if (savedInstanceState == null && originFromExtras == HelpOrigin.ZENDESK_NOTIFICATION) {
            showZendeskTickets()
        }

        if (originFromExtras == HelpOrigin.LOGIN_HELP_NOTIFICATION) {
            loginNotificationScheduler.onNotificationTapped(extraTagsFromExtras?.first())
        }

        if (originFromExtras == HelpOrigin.SITE_PICKER_JETPACK_TIMEOUT) {
            viewModel.contactSupport(TicketType.General)
        }

        initObservers(binding)
    }

    private fun initObservers(binding: ActivityHelpBinding) {
        viewModel.event.observe(this) { event ->
            when (event) {
                is ContactSupportEvent -> {
                    when (event) {
                        is CreateTicket -> {
                            binding.helpLoading.visibility = View.GONE
                            createNewZendeskTicket(event.ticketType, extraTags = event.supportTags)
                        }
                        is ShowLoading -> {
                            binding.helpLoading.visibility = View.VISIBLE
                        }
                    }.exhaustive
                }
                else -> event.isHandled = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshContactEmailText()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onStart() {
        super.onStart()
        ChromeCustomTabUtils.connect(this, AppUrls.APP_HELP_CENTER)
    }

    override fun onStop() {
        super.onStop()
        ChromeCustomTabUtils.disconnect(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun userIsLoggedIn() = accountStore.hasAccessToken()

    private fun createNewZendeskTicket(ticketType: TicketType, extraTags: List<String> = emptyList()) {
        if (!AppPrefs.hasSupportEmail()) {
            showIdentityDialog(ticketType, extraTags, createNewTicket = true)
            return
        }

        if (FeatureFlag.NEW_SUPPORT_REQUESTS.isEnabled()) {
            val tags = extraTags + (extraTagsFromExtras ?: emptyList())
            openSupportRequestForm(tags)
        } else {
            zendeskHelper.createNewTicket(
                context = this,
                origin = originFromExtras,
                selectedSite = selectedSiteOrNull(),
                extraTags = extraTags + extraTagsFromExtras.orEmpty(),
                ticketType = ticketType,
                ssr = viewModel.ssr
            )
        }
    }

    private fun showIdentityDialog(
        ticketType: TicketType,
        extraTags: List<String> = emptyList(),
        createNewTicket: Boolean = false
    ) {
        val emailSuggestion = if (AppPrefs.hasSupportEmail()) {
            AppPrefs.getSupportEmail()
        } else {
            supportHelper
                .getSupportEmailAndNameSuggestion(accountStore.account, selectedSiteOrNull()).first
        }

        supportHelper.showSupportIdentityInputDialog(this, emailSuggestion) { email, _ ->
            zendeskHelper.setSupportEmail(email)
            AnalyticsTracker.track(AnalyticsEvent.SUPPORT_IDENTITY_SET)
            if (createNewTicket) createNewZendeskTicket(ticketType, extraTags)
        }
        AnalyticsTracker.track(AnalyticsEvent.SUPPORT_IDENTITY_FORM_VIEWED)
    }

    private fun refreshContactEmailText() {
        val supportEmail = AppPrefs.getSupportEmail()
        binding.identityContainer.optionValue = supportEmail.ifEmpty {
            getString(R.string.support_contact_email_not_set)
        }
    }

    /**
     * Help activity may have been called during the login flow before the selected site has been set
     */
    private fun selectedSiteOrNull(): SiteModel? {
        return if (selectedSite.exists()) {
            selectedSite.get()
        } else {
            null
        }
    }

    private fun showZendeskTickets() {
        AnalyticsTracker.track(AnalyticsEvent.SUPPORT_TICKETS_VIEWED)
        zendeskHelper.showAllTickets(this, originFromExtras, selectedSiteOrNull(), extraTagsFromExtras)
    }

    private fun showLoginHelpCenter(origin: HelpOrigin, loginFlow: String, loginStep: String) {
        val helpCenterUrl = AppUrls.LOGIN_HELP_CENTER_URLS[origin] ?: AppUrls.APP_HELP_CENTER
        AnalyticsTracker.track(
            stat = AnalyticsEvent.SUPPORT_HELP_CENTER_VIEWED,
            properties = mapOf(
                KEY_SOURCE_FLOW to loginFlow,
                KEY_SOURCE_STEP to loginStep,
                KEY_HELP_CONTENT_URL to helpCenterUrl
            )
        )

        ChromeCustomTabUtils.launchUrl(this, helpCenterUrl)
    }

    private fun showZendeskFaq() {
        AnalyticsTracker.track(AnalyticsEvent.SUPPORT_HELP_CENTER_VIEWED)
        ChromeCustomTabUtils.launchUrl(this, AppUrls.APP_HELP_CENTER)
        /* TODO: for now we simply link to the online woo mobile support documentation, but we should show the
        Zendesk FAQ once it's ready
        zendeskHelper
                .showZendeskHelpCenter(this, originFromExtras, selectedSiteOrNull(), extraTagsFromExtras)
        */
    }

    private fun showApplicationLog() {
        AnalyticsTracker.track(AnalyticsEvent.SUPPORT_APPLICATION_LOG_VIEWED)
        startActivity(Intent(this, WooLogViewerActivity::class.java))
    }

    private fun showSSR() {
        startActivity(Intent(this, SSRActivity::class.java))
    }

    private fun openSupportRequestForm(extraTags: List<String>) {
        startActivity(
            SupportRequestFormActivity.createIntent(
                this,
                originFromExtras,
                ArrayList(extraTags)
            )
        )
    }

    companion object {
        private const val ORIGIN_KEY = "ORIGIN_KEY"
        private const val EXTRA_TAGS_KEY = "EXTRA_TAGS_KEY"
        private const val LOGIN_FLOW_KEY = "LOGIN_FLOW_KEY"
        private const val LOGIN_STEP_KEY = "LOGIN_STEP_KEY"

        @JvmStatic
        fun createIntent(
            context: Context,
            origin: HelpOrigin,
            extraSupportTags: List<String>?,
            loginFlow: String? = null,
            loginStep: String? = null
        ): Intent {
            val intent = Intent(context, HelpActivity::class.java)
            intent.putExtra(ORIGIN_KEY, origin)
            if (extraSupportTags != null && extraSupportTags.isNotEmpty()) {
                intent.putStringArrayListExtra(EXTRA_TAGS_KEY, extraSupportTags as ArrayList<String>?)
            }

            if (loginFlow != null) intent.putExtra(LOGIN_FLOW_KEY, loginFlow)
            if (loginStep != null) intent.putExtra(LOGIN_STEP_KEY, loginStep)

            return intent
        }
    }
}
