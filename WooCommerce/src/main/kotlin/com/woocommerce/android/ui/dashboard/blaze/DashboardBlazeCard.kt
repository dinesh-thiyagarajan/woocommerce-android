package com.woocommerce.android.ui.dashboard.blaze

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.woocommerce.android.NavGraphMainDirections
import com.woocommerce.android.R
import com.woocommerce.android.R.string
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.model.DashboardWidget
import com.woocommerce.android.ui.blaze.BlazeCampaignStat
import com.woocommerce.android.ui.blaze.BlazeCampaignUi
import com.woocommerce.android.ui.blaze.BlazeProductUi
import com.woocommerce.android.ui.blaze.BlazeUrlsHelper.BlazeFlowSource
import com.woocommerce.android.ui.blaze.CampaignStatusUi
import com.woocommerce.android.ui.blaze.campaigs.BlazeCampaignItem
import com.woocommerce.android.ui.blaze.creation.BlazeCampaignCreationDispatcher
import com.woocommerce.android.ui.compose.animations.SkeletonView
import com.woocommerce.android.ui.compose.component.ProductThumbnail
import com.woocommerce.android.ui.compose.component.WCOverflowMenu
import com.woocommerce.android.ui.compose.component.WCTextButton
import com.woocommerce.android.ui.compose.preview.LightDarkThemePreviews
import com.woocommerce.android.ui.compose.rememberNavController
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.ui.compose.viewModelWithFactory
import com.woocommerce.android.ui.dashboard.DashboardFragmentDirections
import com.woocommerce.android.ui.dashboard.DashboardViewModel
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardWidgetAction
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardWidgetMenu
import com.woocommerce.android.ui.dashboard.WidgetCard
import com.woocommerce.android.ui.dashboard.blaze.DashboardBlazeViewModel.DashboardBlazeCampaignState
import com.woocommerce.android.ui.dashboard.defaultHideMenuEntry
import com.woocommerce.android.util.FeatureFlag
import com.woocommerce.android.viewmodel.MultiLiveEvent
import kotlinx.coroutines.launch

@Composable
fun DashboardBlazeCard(
    blazeCampaignCreationDispatcher: BlazeCampaignCreationDispatcher,
    parentViewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    viewModel: DashboardBlazeViewModel = viewModelWithFactory { factory: DashboardBlazeViewModel.Factory ->
        factory.create(parentViewModel)
    }
) {
    viewModel.blazeViewState.observeAsState().value?.let { state ->
        if (state !is DashboardBlazeCampaignState.Hidden) {
            DashboardBlazeView(
                modifier = modifier,
                state = state,
                onDismissBlazeView = viewModel::onBlazeViewDismissed
            )
        }
    }

    HandleEvents(
        viewModel.event,
        blazeCampaignCreationDispatcher
    )
}

@Composable
private fun HandleEvents(
    event: LiveData<MultiLiveEvent.Event>,
    blazeCampaignCreationDispatcher: BlazeCampaignCreationDispatcher
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val campaignDetailsTitle = stringResource(id = R.string.blaze_campaign_details_title)

    DisposableEffect(event, navController, lifecycleOwner) {
        val observer = Observer { event: MultiLiveEvent.Event ->
            when (event) {
                is DashboardBlazeViewModel.LaunchBlazeCampaignCreation -> coroutineScope.launch {
                    blazeCampaignCreationDispatcher.startCampaignCreation(
                        source = BlazeFlowSource.MY_STORE_SECTION,
                        productId = event.productId
                    )
                }

                is DashboardBlazeViewModel.ShowAllCampaigns -> {
                    navController.navigateSafely(
                        DashboardFragmentDirections.actionDashboardToBlazeCampaignListFragment()
                    )
                }

                is DashboardBlazeViewModel.ShowCampaignDetails -> {
                    navController.navigateSafely(
                        NavGraphMainDirections.actionGlobalWPComWebViewFragment(
                            urlToLoad = event.url,
                            urlsToTriggerExit = arrayOf(event.urlToTriggerExit),
                            title = campaignDetailsTitle
                        )
                    )
                }
            }
        }

        event.observe(lifecycleOwner, observer)

        onDispose {
            event.removeObserver(observer)
        }
    }
}

@Composable
private fun BlazeFrame(
    modifier: Modifier,
    state: DashboardBlazeCampaignState,
    onDismissBlazeView: () -> Unit,
    content: @Composable () -> Unit
) {
    if (FeatureFlag.DYNAMIC_DASHBOARD.isEnabled()) {
        WidgetCard(
            modifier = modifier,
            titleResource = DashboardWidget.Type.BLAZE.titleResource,
            iconResource = R.drawable.ic_blaze,
            menu = state.menu,
            button = state.createCampaignButton,
        ) {
            content()
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column {
                BlazeCampaignHeader(onDismissBlazeView)
                content()
                BlazeCampaignFooter(state)
            }
        }
    }
}

@Composable
fun DashboardBlazeView(
    state: DashboardBlazeCampaignState,
    onDismissBlazeView: () -> Unit,
    modifier: Modifier = Modifier
) {
    BlazeFrame(modifier, state, onDismissBlazeView) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(id = R.dimen.major_100),
                )
        ) {
            when (state) {
                is DashboardBlazeCampaignState.Loading -> BlazeCampaignLoading(
                    modifier = Modifier.padding(bottom = dimensionResource(id = R.dimen.major_100))
                )

                is DashboardBlazeCampaignState.Campaign -> {
                    BlazeCampaignItem(
                        campaign = state.campaign,
                        onCampaignClicked = state.onCampaignClicked,
                    )
                }

                is DashboardBlazeCampaignState.NoCampaign -> {
                    Text(
                        modifier = Modifier.padding(
                            end = dimensionResource(id = R.dimen.major_300)
                        ),
                        text = stringResource(id = R.string.blaze_campaign_subtitle),
                        style = MaterialTheme.typography.body1,
                    )
                    BlazeProductItem(
                        product = state.product,
                        onProductSelected = state.onProductClicked,
                        modifier = Modifier.padding(top = dimensionResource(id = R.dimen.major_100))
                    )
                }

                else -> error("Invalid state")
            }
        }
    }
}

@Composable
private fun BlazeCampaignFooter(state: DashboardBlazeCampaignState) {
    Box(modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.major_100))) {
        when (state) {
            is DashboardBlazeCampaignState.Loading -> CampaignFooterLoading(
                Modifier.padding(dimensionResource(id = R.dimen.major_100))
            )

            is DashboardBlazeCampaignState.Campaign -> {
                ShowAllOrCreateCampaignFooter(
                    onShowAllClicked = state.onViewAllCampaignsClicked,
                    onCreateCampaignClicked = state.createCampaignButton.action
                )
            }

            is DashboardBlazeCampaignState.NoCampaign -> {
                WCTextButton(
                    onClick = state.createCampaignButton.action,
                    contentPadding = PaddingValues(
                        top = ButtonDefaults.TextButtonContentPadding.calculateTopPadding(),
                        bottom = ButtonDefaults.TextButtonContentPadding.calculateBottomPadding(),
                    )
                ) {
                    Text(stringResource(id = R.string.blaze_campaign_promote_button))
                }
            }

            else -> error("Invalid state")
        }
    }
}

@Composable
private fun ShowAllOrCreateCampaignFooter(
    onShowAllClicked: () -> Unit,
    onCreateCampaignClicked: () -> Unit,
) {
    Row {
        WCTextButton(
            onClick = onShowAllClicked
        ) {
            Text(stringResource(id = R.string.blaze_campaign_show_all_button))
        }
        WCTextButton(
            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.major_100)),
            onClick = onCreateCampaignClicked
        ) {
            Text(stringResource(id = R.string.blaze_campaign_promote_button))
        }
    }
}

@Composable
private fun BlazeCampaignHeader(onDismissBlazeView: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.ic_blaze),
            contentDescription = "", // Blaze icon, no relevant content desc
            modifier = Modifier
                .padding(horizontal = dimensionResource(id = R.dimen.major_100))
                .size(dimensionResource(id = R.dimen.major_125))
        )
        Text(
            text = stringResource(id = R.string.blaze_campaign_title),
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        WCOverflowMenu(
            items = listOf(stringResource(id = R.string.blaze_overflow_menu_hide_blaze)),
            onSelected = { onDismissBlazeView() },
        )
    }
}

@Composable
fun BlazeProductItem(
    product: BlazeProductUi,
    onProductSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(
                width = dimensionResource(id = R.dimen.minor_10),
                color = colorResource(R.color.divider_color),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100))
            )
            .clip(shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)))
            .clickable { onProductSelected() }
            .padding(dimensionResource(id = R.dimen.major_100))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductThumbnail(imageUrl = product.imgUrl)
            Text(
                modifier = Modifier
                    .padding(start = dimensionResource(id = R.dimen.major_100))
                    .weight(1f),
                text = product.name,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun BlazeCampaignLoading(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = dimensionResource(id = R.dimen.minor_10),
                color = colorResource(R.color.divider_color),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100))
            )
            .clip(shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)))
            .padding(dimensionResource(id = R.dimen.major_100))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SkeletonView(
                    width = dimensionResource(id = R.dimen.major_275),
                    height = dimensionResource(id = R.dimen.major_275)
                )

                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.major_100)))

                SkeletonView(
                    width = dimensionResource(id = R.dimen.skeleton_text_large_width),
                    height = dimensionResource(id = R.dimen.skeleton_text_height_100),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.major_275)))
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.major_100)))
                SkeletonView(
                    width = dimensionResource(id = R.dimen.skeleton_text_medium_width),
                    height = dimensionResource(id = R.dimen.skeleton_text_height_100),
                )
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.major_100)))

                SkeletonView(
                    width = dimensionResource(id = R.dimen.skeleton_text_medium_width),
                    height = dimensionResource(id = R.dimen.skeleton_text_height_100),
                )
            }
        }
    }
}

@Composable
private fun CampaignFooterLoading(
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        SkeletonView(
            width = dimensionResource(id = R.dimen.skeleton_text_medium_width),
            height = dimensionResource(id = R.dimen.skeleton_text_button_height)
        )

        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.major_100)))

        SkeletonView(
            width = dimensionResource(id = R.dimen.skeleton_text_medium_width),
            height = dimensionResource(id = R.dimen.skeleton_text_button_height)
        )
    }
}

@LightDarkThemePreviews
@Composable
fun MyStoreBlazeViewCampaignPreview() {
    val product = BlazeProductUi(
        name = "Product name",
        imgUrl = "",
    )
    DashboardBlazeView(
        state = DashboardBlazeCampaignState.Campaign(
            campaign = BlazeCampaignUi(
                product = product,
                status = CampaignStatusUi.Active,
                stats = listOf(
                    BlazeCampaignStat(
                        name = string.blaze_campaign_status_impressions,
                        value = 100.toString()
                    ),
                    BlazeCampaignStat(
                        name = string.blaze_campaign_status_clicks,
                        value = 10.toString()
                    ),
                    BlazeCampaignStat(
                        name = string.blaze_campaign_status_budget,
                        value = 1000.toString()
                    ),
                ),
            ),
            onCampaignClicked = {},
            onViewAllCampaignsClicked = {},
            menu = DashboardWidgetMenu(
                items = listOf(
                    DashboardWidget.Type.BLAZE.defaultHideMenuEntry(
                        onHideClicked = { }
                    )
                )
            ),
            createCampaignButton = DashboardWidgetAction(
                titleResource = R.string.blaze_campaign_promote_button,
                action = {}
            )
        ),
        onDismissBlazeView = {}
    )
}

@LightDarkThemePreviews
@Composable
fun MyStoreBlazeViewNoCampaignPreview() {
    DashboardBlazeView(
        state = DashboardBlazeCampaignState.NoCampaign(
            product = BlazeProductUi(
                name = "Product name",
                imgUrl = "",
            ),
            onProductClicked = {},
            menu = DashboardWidgetMenu(
                items = listOf(
                    DashboardWidget.Type.BLAZE.defaultHideMenuEntry(
                        onHideClicked = { }
                    )
                )
            ),
            createCampaignButton = DashboardWidgetAction(
                titleResource = R.string.blaze_campaign_promote_button,
                action = {}
            )
        ),
        onDismissBlazeView = {}
    )
}

@LightDarkThemePreviews
@Composable
fun DashboardBlazeLoadingPreview() {
    WooThemeWithBackground {
        DashboardBlazeView(
            state = DashboardBlazeCampaignState.Loading,
            onDismissBlazeView = {}
        )
    }
}