package com.woocommerce.android.ui.compose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons.Outlined
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.woocommerce.android.R.dimen
import com.woocommerce.android.R.string

@Composable
fun <T> WCOverflowMenu(
    items: Array<T>,
    onSelected: (T) -> Unit,
    mapper: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.padding(top = dimensionResource(id = dimen.minor_100))
    ) {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(
                imageVector = Outlined.MoreVert,
                contentDescription = stringResource(string.more_menu),
            )
        }
        DropdownMenu(
            offset = DpOffset(
                x = dimensionResource(id = dimen.major_100),
                y = 0.dp
            ),
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    modifier = Modifier.height(dimensionResource(id = dimen.major_175)),
                    onClick = {
                        showMenu = false
                        onSelected(item)
                    }
                ) {
                    Text(mapper(item))
                }
                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.height(dimensionResource(id = dimen.minor_100)))
                }
            }
        }
    }
}
