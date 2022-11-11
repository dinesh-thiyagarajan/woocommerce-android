package com.woocommerce.android.ui.common

import android.os.Parcelable
import com.woocommerce.android.OnChangedException
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivated
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginActivationFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstallFailed
import com.woocommerce.android.ui.common.PluginRepository.PluginStatus.PluginInstalled
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.dispatchAndAwait
import com.woocommerce.android.util.observeEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.PluginActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.plugin.SitePluginModel
import org.wordpress.android.fluxc.store.PluginStore
import org.wordpress.android.fluxc.store.PluginStore.ConfigureSitePluginError
import org.wordpress.android.fluxc.store.PluginStore.ConfigureSitePluginPayload
import org.wordpress.android.fluxc.store.PluginStore.FetchSitePluginErrorType
import org.wordpress.android.fluxc.store.PluginStore.FetchSitePluginPayload
import org.wordpress.android.fluxc.store.PluginStore.InstallSitePluginError
import org.wordpress.android.fluxc.store.PluginStore.InstallSitePluginPayload
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginConfigured
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginFetched
import org.wordpress.android.fluxc.store.PluginStore.OnSitePluginInstalled
import javax.inject.Inject

class PluginRepository @Inject constructor(
    private val dispatcher: Dispatcher,
    private val selectedSite: SelectedSite,
    @Suppress("unused") private val pluginStore: PluginStore
) {
    companion object {
        const val GENERIC_ERROR = "Unknown issue."
        const val ATTEMPT_LIMIT = 2
    }

    suspend fun fetchPlugin(name: String): Result<SitePluginModel?> {
        WooLog.d(WooLog.T.PLUGINS, "Fetching plugin $name")

        val action = PluginActionBuilder.newFetchSitePluginAction(
            FetchSitePluginPayload(selectedSite.get(), name)
        )
        val event: OnSitePluginFetched = dispatcher.dispatchAndAwait(action)
        return when {
            !event.isError ||
                event.error?.type == FetchSitePluginErrorType.PLUGIN_DOES_NOT_EXIST -> {
                WooLog.d(
                    WooLog.T.PLUGINS,
                    "Fetching plugin succeeded, plugin is: ${if (event.plugin != null) "Installed" else "Not Installed"}"
                )
                Result.success(event.plugin)
            }
            else -> {
                WooLog.w(
                    WooLog.T.PLUGINS,
                    "Fetching plugin failed, ${event.error.type} ${event.error.message}"
                )
                Result.failure(OnChangedException(event.error))
            }
        }
    }

    fun installPlugin(slug: String, name: String): Flow<PluginStatus> = flow {
        WooLog.d(WooLog.T.PLUGINS, "Installing plugin Slug: $slug, Name: $name")
        val plugin = fetchPlugin(name).getOrElse {
            throw it
        }

        if (plugin != null) {
            // Plugin is already installed, proceed to activation
            WooLog.d(WooLog.T.PLUGINS, "Plugin $slug is already installed, proceed to activation")
            emit(PluginInstalled(slug, selectedSite.get()))
            val payload = ConfigureSitePluginPayload(
                selectedSite.get(),
                name,
                slug,
                true,
                true
            )
            dispatcher.dispatch(PluginActionBuilder.newConfigureSitePluginAction(payload))
        } else {
            // This request will automatically proceed to activating the plugin after the installation
            val payload = InstallSitePluginPayload(selectedSite.get(), slug)
            dispatcher.dispatch(PluginActionBuilder.newInstallSitePluginAction(payload))

        }

        val installationEvents = dispatcher.observeEvents<OnSitePluginInstalled>()
            .map { event ->
                if (!event.isError) {
                    WooLog.d(WooLog.T.PLUGINS, "Plugin $slug installed successfully")
                    PluginInstalled(event.slug, event.site)
                } else {
                    WooLog.w(WooLog.T.PLUGINS, "Installation failed for plugin $slug, ${event.error.type}")
                    throw OnChangedException(
                        event.error,
                        event.error.message
                    )
                }
            }
        val activationEvents = dispatcher.observeEvents<OnSitePluginConfigured>()
            .map { event ->
                if (!event.isError) {
                    WooLog.d(WooLog.T.PLUGINS, "Plugin $slug activated successfully")
                    PluginActivated(event.pluginName, event.site)
                } else {
                    WooLog.w(WooLog.T.PLUGINS, "Activation failed for plugin $slug, ${event.error.type}")
                    throw OnChangedException(
                        event.error,
                        event.error.message
                    )
                }
            }

        emitAll(merge(installationEvents, activationEvents))
    }.retryWhen { cause, attempt ->
        (cause is OnChangedException && attempt < ATTEMPT_LIMIT).also {
            if (it) WooLog.d(WooLog.T.PLUGINS, "Retry plugin installation")
        }
    }.catch { cause ->
        if (cause !is OnChangedException) throw cause
        if (cause.error is InstallSitePluginError) {
            emit(
                PluginInstallFailed(
                    errorDescription = cause.message ?: GENERIC_ERROR,
                    errorType = cause.error.type.name
                )
            )
        } else if (cause.error is ConfigureSitePluginError) {
            emit(
                PluginActivationFailed(
                    errorDescription = cause.message ?: GENERIC_ERROR,
                    errorType = cause.error.type.name
                )
            )
        }
    }

    sealed class PluginStatus : Parcelable {
        @Parcelize
        data class PluginInstalled(val slug: String, val site: SiteModel) : PluginStatus()

        @Parcelize
        data class PluginInstallFailed(val errorDescription: String, val errorType: String) : PluginStatus()

        @Parcelize
        data class PluginActivated(val name: String, val site: SiteModel) : PluginStatus()

        @Parcelize
        data class PluginActivationFailed(val errorDescription: String, val errorType: String) : PluginStatus()
    }
}
