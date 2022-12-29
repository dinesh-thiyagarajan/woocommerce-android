package com.woocommerce.android.ui.login.storecreation.profiler

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.woocommerce.android.R
import com.woocommerce.android.ui.compose.component.WCColoredButton
import com.woocommerce.android.ui.compose.theme.WooThemeWithBackground
import com.woocommerce.android.ui.login.storecreation.profiler.StoreProfilerViewModel.ProfilerOptionType.SITE_CATEGORY
import com.woocommerce.android.ui.login.storecreation.profiler.StoreProfilerViewModel.StoreProfilerOptionUi
import com.woocommerce.android.ui.login.storecreation.profiler.StoreProfilerViewModel.StoreProfilerState

@Composable
fun StoreProfilerScreen(viewModel: StoreProfilerViewModel) {
    viewModel.storeProfilerState.observeAsState().value?.let { state ->
        Scaffold(topBar = {
            Toolbar(
                onArrowBackPressed = viewModel::onArrowBackPressed,
                onSkipPressed = viewModel::onSkipPressed
            )
        }) {
            ProfilerContent(
                profilerStepContent = state,
                onContinueClicked = viewModel::onContinueClicked,
                onCategorySelected = viewModel::onCategorySelected,
                modifier = Modifier.background(MaterialTheme.colors.surface)
            )
        }
    }
}

@Composable
private fun Toolbar(
    onArrowBackPressed: () -> Unit,
    onSkipPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        backgroundColor = MaterialTheme.colors.surface,
        title = {},
        navigationIcon = {
            IconButton(onClick = onArrowBackPressed) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        },
        actions = {
            TextButton(onClick = onSkipPressed) {
                Text(text = stringResource(id = R.string.skip))
            }
        },
        elevation = 0.dp,
        modifier = modifier
    )
}

@Composable
private fun ProfilerContent(
    profilerStepContent: StoreProfilerState,
    onCategorySelected: (StoreProfilerOptionUi) -> Unit,
    onContinueClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(dimensionResource(id = R.dimen.major_100)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
        ) {
            Text(
                text = profilerStepContent.storeName.uppercase(),
                style = MaterialTheme.typography.caption,
                color = colorResource(id = R.color.color_on_surface_medium)
            )
            Text(
                text = profilerStepContent.title,
                style = MaterialTheme.typography.h5,
            )
            Text(
                text = profilerStepContent.description,
                style = MaterialTheme.typography.subtitle1,
                color = colorResource(id = R.color.color_on_surface_medium)
            )
            CategoryList(
                categories = profilerStepContent.options,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
        Divider(
            color = colorResource(id = R.color.divider_color),
            thickness = dimensionResource(id = R.dimen.minor_10)
        )
        WCColoredButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.major_100)),
            onClick = onContinueClicked,
            enabled = profilerStepContent.options.any { it.isSelected }
        ) {
            Text(text = stringResource(id = R.string.continue_button))
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<StoreProfilerOptionUi>,
    onCategorySelected: (StoreProfilerOptionUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(categories) { _, category ->
            CategoryItem(
                category = category,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(id = R.dimen.major_100))
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: StoreProfilerOptionUi,
    onCategorySelected: (StoreProfilerOptionUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = dimensionResource(id = if (category.isSelected) R.dimen.minor_25 else R.dimen.minor_10),
                color = colorResource(
                    if (category.isSelected) R.color.color_primary else R.color.divider_color
                ),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100))
            )
            .clip(shape = RoundedCornerShape(dimensionResource(id = R.dimen.minor_100)))
            .background(
                color = colorResource(
                    id = if (category.isSelected)
                        if (isSystemInDarkTheme()) R.color.color_surface else R.color.woo_purple_10
                    else R.color.color_surface
                )
            )
            .clickable { onCategorySelected(category) }
    ) {
        Text(
            text = category.name,
            color = colorResource(
                id = if (isSystemInDarkTheme() && category.isSelected) R.color.color_primary
                else R.color.color_on_surface
            ),
            modifier = Modifier.padding(
                start = dimensionResource(id = R.dimen.major_100),
                top = dimensionResource(id = R.dimen.major_75),
                bottom = dimensionResource(id = R.dimen.major_75),
                end = dimensionResource(id = R.dimen.major_100),
            )
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
fun CategoriesContentPreview() {
    WooThemeWithBackground {
        ProfilerContent(
            profilerStepContent = StoreProfilerState(
                storeName = "White Christmas Tree",
                title = "What’s your business about?",
                description = "Choose a category that defines your business the best.",
                options = CATEGORIES
            ),
            onContinueClicked = {},
            onCategorySelected = {}
        )
    }
}

// TODO remove when this are available from API
val CATEGORIES = listOf(
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Art & Photography",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Books & Magazines",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Electronics and Software",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Construction & Industrial",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Design & Marketing",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Fashion and Apparel",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Food and Drink",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Books & Magazines 2",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Electronics and Software 2",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Design & Marketing 2",
        isSelected = false
    ),
    StoreProfilerOptionUi(
        SITE_CATEGORY,
        name = "Fashion and Apparel 2",
        isSelected = false
    ),
)
