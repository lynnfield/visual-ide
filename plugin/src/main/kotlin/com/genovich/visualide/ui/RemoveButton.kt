package com.genovich.visualide.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun RemoveButton(
    name: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    IconActionButton(
        modifier = modifier,
        key = AllIconsKeys.General.Close,
        contentDescription = "delete $name",
        onClick = onRemove,
    )
}