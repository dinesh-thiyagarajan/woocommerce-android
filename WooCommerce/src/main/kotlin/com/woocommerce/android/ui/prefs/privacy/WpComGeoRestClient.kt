package com.woocommerce.android.ui.prefs.privacy

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.annotations.SerializedName
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooPayload
import org.wordpress.android.fluxc.network.rest.wpcom.wc.toWooError
import javax.inject.Inject
import javax.inject.Named

class WpComGeoRestClient @Inject constructor(
    private val wpComGsonRequestBuilder: WPComGsonRequestBuilder,
    dispatcher: Dispatcher,
    appContext: Context?,
    @Named("regular") requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent,
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {

    suspend fun fetchCountryCode(): WooPayload<String?> {
        val url = "https://public-api.wordpress.com/geo/"
        val response = wpComGsonRequestBuilder.syncGetRequest(
            this,
            url,
            emptyMap(),
            GeoResponse::class.java
        )
        return when (response) {
            is WPComGsonRequestBuilder.Response.Success -> WooPayload(response.data.countryCode)
            is WPComGsonRequestBuilder.Response.Error -> WooPayload(response.error.toWooError())
        }
    }
}

data class GeoResponse(
    @SerializedName("country_short")
    val countryCode: String? = null
)
