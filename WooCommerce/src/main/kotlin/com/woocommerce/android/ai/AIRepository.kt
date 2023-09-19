package com.woocommerce.android.ai

import com.woocommerce.android.util.WooLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.jetpackai.JetpackAIRestClient.JetpackAICompletionsResponse
import org.wordpress.android.fluxc.store.jetpackai.JetpackAIStore
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val jetpackAIStore: JetpackAIStore
) {
    companion object {
        const val PRODUCT_SHARING_FEATURE = "woo_android_share_product"
        const val PRODUCT_DESCRIPTION_FEATURE = "woo_android_product_description"
        const val PRODUCT_NAME_FEATURE = "woo_android_product_name"
    }

    suspend fun generateProductSharingText(
        site: SiteModel,
        productName: String,
        permalink: String,
        productDescription: String?,
        languageISOCode: String = "en"
    ): Result<String> {
        val prompt = AIPrompts.generateProductSharingPrompt(
            productName,
            permalink,
            productDescription.orEmpty(),
            languageISOCode
        )
        return fetchJetpackAICompletionsForSite(site, prompt, PRODUCT_SHARING_FEATURE)
    }

    suspend fun generateProductDescription(
        site: SiteModel,
        productName: String,
        features: String,
        languageISOCode: String = "en"
    ): Result<String> {
        val prompt = AIPrompts.generateProductDescriptionPrompt(
            productName,
            features,
            languageISOCode
        )
        return fetchJetpackAICompletionsForSite(site, prompt, PRODUCT_DESCRIPTION_FEATURE)
    }

    suspend fun identifyISOLanguageCode(site: SiteModel, text: String, feature: String): Result<String> {
        val prompt = AIPrompts.generateLanguageIdentificationPrompt(text)
        return fetchJetpackAICompletionsForSite(site, prompt, feature)
    }

    private suspend fun fetchJetpackAICompletionsForSite(
        site: SiteModel,
        prompt: String,
        feature: String
    ): Result<String> = withContext(Dispatchers.IO) {
        jetpackAIStore.fetchJetpackAICompletions(site, prompt, feature).run {
            when (this) {
                is JetpackAICompletionsResponse.Success -> {
                    WooLog.d(WooLog.T.AI, "Fetching Jetpack AI completions succeeded")
                    Result.success(completion)
                }
                is JetpackAICompletionsResponse.Error -> {
                    WooLog.w(WooLog.T.AI, "Fetching Jetpack AI completions failed: $message")
                    Result.failure(this.mapToException())
                }
            }
        }
    }
    data class JetpackAICompletionsException(
        val errorMessage: String,
        val errorType: String
    ) : Exception(errorMessage)

    private fun JetpackAICompletionsResponse.Error.mapToException() =
        JetpackAICompletionsException(
            errorMessage = message ?: "Unable to fetch AI completions",
            errorType = type.name
        )
}
