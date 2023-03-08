package com.woocommerce.android.ui.login.storecreation.onboarding.launchstore

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.woocommerce.android.R
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewAuthenticator
import com.woocommerce.android.ui.compose.annotatedStringRes
import com.woocommerce.android.ui.compose.component.Toolbar
import com.woocommerce.android.ui.compose.component.WCColoredButton
import com.woocommerce.android.ui.compose.component.WCOutlinedButton
import com.woocommerce.android.ui.compose.component.WCWebView
import com.woocommerce.android.ui.compose.drawShadow
import com.woocommerce.android.ui.login.storecreation.onboarding.launchstore.LaunchStoreViewModel.LaunchStoreState
import org.wordpress.android.fluxc.network.UserAgent

@Composable
fun LaunchStoreScreen(viewModel: LaunchStoreViewModel) {
    viewModel.viewState.observeAsState().value?.let { state ->
        Scaffold(topBar = {
            Toolbar(
                title = if (!state.isStoreLaunched) stringResource(id = R.string.store_onboarding_launch_preview_title) else "",
                onNavigationButtonClick = viewModel::onBackPressed,
            )
        }) { padding ->
            LaunchStoreScreen(
                state = state,
                userAgent = viewModel.userAgent,
                authenticator = viewModel.wpComWebViewAuthenticator,
                onLaunchStoreClicked = viewModel::launchStore,
                onBannerClicked = viewModel::onUpgradePlanBannerClicked,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.surface)
                    .padding(padding)
            )
        }
    }
}

@Composable
fun LaunchStoreScreen(
    state: LaunchStoreState,
    userAgent: UserAgent,
    authenticator: WPComWebViewAuthenticator,
    onLaunchStoreClicked: () -> Unit,
    onBannerClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isTrialPlan) EcommerceTrialBanner(
                onBannerClicked = onBannerClicked,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.isStoreLaunched) {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100))) {
                    Text(
                        text = stringResource(id = R.string.store_onboarding_launched_title),
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.siteUrl,
                        style = MaterialTheme.typography.caption
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(color = colorResource(id = R.color.color_surface))
                            .clip(RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)))
                            .padding(
                                horizontal = dimensionResource(id = R.dimen.major_350),
                                vertical = dimensionResource(id = R.dimen.major_200)
                            )
                    ) {
                        SitePreview(
                            url = state.siteUrl,
                            userAgent = userAgent,
                            authenticator = authenticator,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .drawShadow(
                                    color = colorResource(id = R.color.color_on_surface),
                                    backgroundColor = colorResource(id = R.color.color_surface),
                                    borderRadius = dimensionResource(id = R.dimen.major_100)
                                )
                                .padding(dimensionResource(id = R.dimen.minor_100))
                                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)))
                                .border(
                                    dimensionResource(id = R.dimen.minor_10),
                                    colorResource(id = R.color.gray_0),
                                    shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)),
                                )
                        )
                    }
                }
            } else {
                SitePreview(
                    url = state.siteUrl,
                    userAgent = userAgent,
                    authenticator = authenticator,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Divider(
            color = colorResource(id = R.color.divider_color),
            thickness = dimensionResource(id = R.dimen.minor_10)
        )
        if (!state.isStoreLaunched) {
            WCColoredButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.major_100)),
                onClick = onLaunchStoreClicked,
                enabled = !state.isTrialPlan
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size = dimensionResource(id = R.dimen.major_150)),
                        color = colorResource(id = R.color.color_on_primary_surface),
                    )
                } else {
                    Text(text = stringResource(id = R.string.store_onboarding_launch_store_button))
                }
            }
        } else {
            WCColoredButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(id = R.dimen.major_100),
                        end = dimensionResource(id = R.dimen.major_100),
                        bottom = dimensionResource(id = R.dimen.minor_100)
                    ),
                onClick = { },
            ) {
                Text(text = stringResource(id = R.string.store_onboarding_launch_store_share_url_button))
            }
            WCOutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(id = R.dimen.major_100),
                        end = dimensionResource(id = R.dimen.major_100),
                        bottom = dimensionResource(id = R.dimen.major_100)
                    ),
                onClick = {}) {
                Text(text = stringResource(id = R.string.store_onboarding_launch_store_back_to_store_button))
            }
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
fun SitePreview(
    url: String,
    userAgent: UserAgent,
    authenticator: WPComWebViewAuthenticator,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        WCWebView(
            url = url,
            userAgent = userAgent,
            wpComAuthenticator = authenticator,
            onUrlLoaded = { },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(dimensionResource(id = R.dimen.minor_75)))
        )
    }
}

@Composable
fun EcommerceTrialBanner(
    onBannerClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(color = colorResource(id = R.color.woo_purple_10))
            .clickable { onBannerClicked() }
            .padding(dimensionResource(id = R.dimen.major_100)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_tintable_info_outline_24dp),
            contentDescription = "",
            colorFilter = ColorFilter.tint(color = colorResource(id = R.color.color_icon))
        )
        Text(
            text = annotatedStringRes(R.string.store_onboarding_upgrade_plan_to_launch_store_banner_text),
            style = MaterialTheme.typography.body1,
        )
    }
}

@ExperimentalFoundationApi
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "small screen", device = Devices.PIXEL)
@Preview(name = "mid screen", device = Devices.PIXEL_4)
@Preview(name = "large screen", device = Devices.NEXUS_10)
@Composable
fun LaunchStoreScreenPreview() {

}
