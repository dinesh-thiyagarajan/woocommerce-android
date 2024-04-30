package com.woocommerce.android.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.woocommerce.android.R
import com.woocommerce.android.model.DashboardWidget
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.ui.blaze.creation.BlazeCampaignCreationDispatcher
import com.woocommerce.android.ui.compose.component.WCColoredButton
import com.woocommerce.android.ui.compose.component.WCOutlinedButton
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardEvent.OpenRangePicker
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardEvent.ShowStatsError
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardWidgetAction
import com.woocommerce.android.ui.dashboard.DashboardViewModel.DashboardWidgetMenu
import com.woocommerce.android.ui.dashboard.blaze.DashboardBlazeCard
import com.woocommerce.android.ui.dashboard.onboarding.DashboardOnboardingCard
import com.woocommerce.android.ui.dashboard.stats.DashboardStatsCard
import com.woocommerce.android.ui.dashboard.topperformers.DashboardTopPerformersWidgetCard

@Composable
fun DashboardContainer(
    dashboardViewModel: DashboardViewModel,
    blazeCampaignCreationDispatcher: BlazeCampaignCreationDispatcher
) {
    dashboardViewModel.dashboardWidgets.observeAsState().value?.let { widgets ->
        WidgetList(
            widgetUiModels = widgets,
            dashboardViewModel = dashboardViewModel,
            blazeCampaignCreationDispatcher = blazeCampaignCreationDispatcher
        )
    }
}

@Composable
private fun WidgetList(
    widgetUiModels: List<DashboardViewModel.DashboardWidgetUiModel>,
    dashboardViewModel: DashboardViewModel,
    blazeCampaignCreationDispatcher: BlazeCampaignCreationDispatcher
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
            .padding(vertical = dimensionResource(id = R.dimen.major_100))
    ) {
        val widgetModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        widgetUiModels.forEach {
            AnimatedVisibility(it.isVisible) {
                when (it) {
                    is DashboardViewModel.DashboardWidgetUiModel.ConfigurableWidget -> {
                        ConfigurableWidgetCard(
                            widgetUiModel = it,
                            dashboardViewModel = dashboardViewModel,
                            blazeCampaignCreationDispatcher = blazeCampaignCreationDispatcher,
                            modifier = widgetModifier
                        )
                    }

                    is DashboardViewModel.DashboardWidgetUiModel.ShareStoreWidget -> {
                        ShareStoreCard(
                            onShareClicked = it.onShareClicked,
                            modifier = widgetModifier
                        )
                    }

                    is DashboardViewModel.DashboardWidgetUiModel.FeedbackWidget -> {
                        FeedbackCard(
                            widget = it,
                            modifier = widgetModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurableWidgetCard(
    widgetUiModel: DashboardViewModel.DashboardWidgetUiModel.ConfigurableWidget,
    dashboardViewModel: DashboardViewModel,
    blazeCampaignCreationDispatcher: BlazeCampaignCreationDispatcher,
    modifier: Modifier
) {
    when (widgetUiModel.widget.type) {
        DashboardWidget.Type.STATS -> {
            DashboardStatsCard(
                onStatsError = {
                    dashboardViewModel.onDashboardWidgetEvent(ShowStatsError)
                },
                openDatePicker = { start, end, callback ->
                    dashboardViewModel.onDashboardWidgetEvent(OpenRangePicker(start, end, callback))
                },
                parentViewModel = dashboardViewModel,
                modifier = modifier
            )
        }

        DashboardWidget.Type.POPULAR_PRODUCTS -> DashboardTopPerformersWidgetCard(
            parentViewModel = dashboardViewModel,
            modifier = modifier
        )

        DashboardWidget.Type.BLAZE -> DashboardBlazeCard(
            blazeCampaignCreationDispatcher = blazeCampaignCreationDispatcher,
            parentViewModel = dashboardViewModel,
            modifier = modifier
        )

        DashboardWidget.Type.ONBOARDING -> DashboardOnboardingCard(
            parentViewModel = dashboardViewModel,
            modifier = modifier
        )
    }
}

@Composable
private fun ShareStoreCard(
    onShareClicked: () -> Unit,
    modifier: Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .border(
                width = 1.dp,
                color = colorResource(id = R.color.woo_gray_5),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.blaze_campaign_created_success),
            contentDescription = null
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = R.string.get_the_word_out),
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.share_your_store_message),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        WCColoredButton(
            onClick = onShareClicked,
            text = stringResource(id = R.string.share_store_button)
        )
    }
}


@Composable
fun FeedbackCard(
    widget: DashboardViewModel.DashboardWidgetUiModel.FeedbackWidget,
    modifier: Modifier
) {
    WidgetCard(
        titleResource = R.string.my_store_widget_feedback_title,
        menu = DashboardWidgetMenu(
            items = listOf(
                DashboardWidgetAction(
                    title = UiStringRes(
                        stringRes = R.string.dynamic_dashboard_hide_widget_menu_item,
                        listOf(UiStringRes(stringRes = R.string.my_store_widget_feedback_title)),
                    ),
                    action = widget.onDismiss
                )
            ),
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.feedback_request_title),
                style = MaterialTheme.typography.body1
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WCOutlinedButton(
                    onClick = widget.onNegativeClick,
                    text = stringResource(id = R.string.feedback_request_make_better),
                    modifier = Modifier.weight(1f)
                )
                WCColoredButton(
                    onClick = widget.onPositiveClick,
                    text = stringResource(id = R.string.feedback_request_like_it),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
