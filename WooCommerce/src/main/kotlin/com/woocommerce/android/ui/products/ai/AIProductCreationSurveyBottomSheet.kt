package com.woocommerce.android.ui.products.ai

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.AppUrls.CROWDSIGNAL_PRODCUT_CREATION_WITH_AI_SURVEY
import com.woocommerce.android.R
import com.woocommerce.android.ui.compose.component.BottomSheetHandle
import com.woocommerce.android.ui.compose.component.WCColoredButton
import com.woocommerce.android.ui.compose.component.WCOutlinedButton
import com.woocommerce.android.ui.compose.composeView
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.widgets.WCBottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AIProductCreationSurveyBottomSheet : WCBottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return composeView {
            SurveyBottomSheetContent(
                onStartSurveyClick = {
                    ChromeCustomTabUtils.launchUrl(
                        requireContext(),
                        CROWDSIGNAL_PRODCUT_CREATION_WITH_AI_SURVEY
                    )
                    findNavController().popBackStack()
                },
                onSkipPressed = { findNavController().popBackStack() },
            )
        }
    }
}

@Composable
private fun SurveyBottomSheetContent(
    onStartSurveyClick: () -> Unit,
    onSkipPressed: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = dimensionResource(id = R.dimen.minor_100),
            topEnd = dimensionResource(id = R.dimen.minor_100)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                start = dimensionResource(id = R.dimen.major_100),
                end = dimensionResource(id = R.dimen.major_100),
                bottom = dimensionResource(id = R.dimen.major_250),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.minor_100)))
            BottomSheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.major_175)))
            Box {
                Image(
                    painter = painterResource(id = R.drawable.ai_product_creation_survey_icon),
                    contentDescription = "", // No relevant content desc
                )
                Image(
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.major_175))
                        .align(Alignment.TopStart),
                    painter = painterResource(id = R.drawable.ic_ai),
                    colorFilter = ColorFilter.tint(colorResource(id = R.color.color_primary)),
                    contentDescription = "",
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.major_175)))
            Text(
                text = stringResource(id = R.string.product_creation_survey_title),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.major_100)))
            Text(
                modifier = Modifier.padding(
                    start = dimensionResource(id = R.dimen.minor_100),
                    end = dimensionResource(id = R.dimen.minor_100),
                ),
                text = stringResource(id = R.string.product_creation_survey_description),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.major_100)))
            WCColoredButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartSurveyClick,
                text = stringResource(id = R.string.product_creation_survey_button_open)
            )
            WCOutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSkipPressed,
                text = stringResource(id = R.string.product_creation_survey_button_skip)
            )
        }
    }
}

@Composable
@Preview(name = "light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SurveyBottomSheetContentPreview() {
    WooThemeWithBackground {
        SurveyBottomSheetContent(
            onStartSurveyClick = {},
            onSkipPressed = {}
        )
    }
}
