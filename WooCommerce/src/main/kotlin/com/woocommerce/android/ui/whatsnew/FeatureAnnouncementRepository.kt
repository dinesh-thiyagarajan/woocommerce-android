@file:Suppress("MaximumLineLength")

package com.woocommerce.android.ui.whatsnew

import com.woocommerce.android.model.FeatureAnnouncement
import com.woocommerce.android.model.FeatureAnnouncementItem
import com.woocommerce.android.util.BuildConfigWrapper
import org.wordpress.android.fluxc.model.whatsnew.WhatsNewAnnouncementModel
import org.wordpress.android.fluxc.store.WhatsNewStore
import org.wordpress.android.util.StringUtils
import javax.inject.Inject

@Suppress("MaxLineLength")

class FeatureAnnouncementRepository @Inject constructor(
    private val whatsNewStore: WhatsNewStore,
    private val buildConfigWrapper: BuildConfigWrapper
) {
    val exampleAnnouncement = FeatureAnnouncement(
        appVersionName = "14.2",
        announcementVersion = 1337,
        minimumAppVersion = "14.2",
        maximumAppVersion = "14.3",
        appVersionTargets = listOf("alpha-centauri-1", "alpha-centauri-2"),
        detailsUrl = "https://woocommerce.com/",
        features = listOf(
            FeatureAnnouncementItem(
                title = "Super Publishing",
                subtitle = "Super Publishing is here! Publish using the power of your mind.",
                iconBase64 = "",
                iconUrl = "https://s0.wordpress.com/i/store/mobile/plans-personal.png"
            ),
            FeatureAnnouncementItem(
                title = "Amazing Feature",
                subtitle = "That's right! They are right in the app! They require pets right now.",
                iconBase64 = "",
                iconUrl = "https://s0.wordpress.com/i/store/mobile/plans-premium.png"
            ),
            /* Test case where URL is empty, but iconBase64 exists */
            FeatureAnnouncementItem(
                title = "We like long feature announcements that why this one is going to be extra long",
                subtitle = "Super Publishing is here! Publish using the power of your mind.",
                iconBase64 = "data:image/png;base64,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",
                iconUrl = ""
            ),
            /* Test case where both icon url and iconBase64 are empty */
            FeatureAnnouncementItem(
                title = "Fourth feature",
                subtitle = "Sorry we forgot to include an image here!",
                iconBase64 = "",
                iconUrl = ""
            )
        )
    )

    suspend fun getLatestFeatureAnnouncement(fromCache: Boolean): FeatureAnnouncement? {
        return getFeatureAnnouncements(fromCache).firstOrNull()
    }

    suspend fun getFeatureAnnouncements(fromCache: Boolean): List<FeatureAnnouncement> {
        val featureAnnouncements = mutableListOf<FeatureAnnouncement>()

        val onWhatsNewFetched = if (fromCache) {
            whatsNewStore.fetchCachedAnnouncements()
        } else {
            whatsNewStore.fetchRemoteAnnouncements(
                buildConfigWrapper.versionName, WhatsNewStore.WhatsNewAppId.WOO_ANDROID
            )
        }
        onWhatsNewFetched.whatsNewItems?.map { featureAnnouncements.add(it.build()) }?.toList()
        return featureAnnouncements
    }

    fun WhatsNewAnnouncementModel.build(): FeatureAnnouncement {
        return FeatureAnnouncement(
            appVersionName,
            announcementVersion,
            minimumAppVersion,
            maximumAppVersion,
            appVersionTargets,
            detailsUrl,
            isLocalized,
            features.map {
                it.build()
            }
        )
    }

    fun WhatsNewAnnouncementModel.WhatsNewAnnouncementFeature.build(): FeatureAnnouncementItem {
        return FeatureAnnouncementItem(
            StringUtils.notNullStr(title),
            StringUtils.notNullStr(subtitle),
            StringUtils.notNullStr(iconBase64),
            StringUtils.notNullStr(iconUrl)
        )
    }
}
