package com.woocommerce.android.ui.woopos.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.woocommerce.android.ui.woopos.common.composeui.WooPosPreview
import com.woocommerce.android.ui.woopos.common.composeui.WooPosTheme
import com.woocommerce.android.ui.woopos.root.navigation.WooPosNavigationEvent
import com.woocommerce.android.ui.woopos.root.navigation.WooPosRootHost

@Composable
fun WooPosRootScreen(onNavigationEvent: (WooPosNavigationEvent) -> Unit) {
    val viewModel: WooPosRootViewModel = hiltViewModel()
    WooPosRootScreen(
        viewModel.bottomToolbarState.collectAsState(),
        onNavigationEvent = onNavigationEvent,
        viewModel::onUiEvent
    )
}

@Composable
private fun WooPosRootScreen(
    state: State<WooPosBottomToolbarState>,
    onNavigationEvent: (WooPosNavigationEvent) -> Unit,
    onUIEvent: (WooPosRootUIEvent) -> Unit
) {
    WooPosTheme {
        Column(modifier = Modifier.background(MaterialTheme.colors.background)) {
            WooPosRootHost(
                modifier = Modifier.weight(1f),
            )
            WooPosBottomToolbar(
                state,
                onNavigationEvent,
                onUIEvent,
            )
        }
    }
}

@WooPosPreview
@Composable
fun PreviewWooPosRootScreen() {
    val state = remember {
        mutableStateOf(WooPosBottomToolbarState(WooPosBottomToolbarState.CardReaderStatus.Unknown))
    }
    WooPosTheme {
        WooPosRootScreen(state, {}, {})
    }
}
