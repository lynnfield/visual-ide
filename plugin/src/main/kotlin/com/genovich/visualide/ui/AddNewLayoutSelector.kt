package com.genovich.visualide.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Suppress("ParamsComparedByRef")
@OptIn(ExperimentalJewelApi::class)
@Composable
fun AddNewLayoutButton(
    modifier: Modifier = Modifier,
    onAdd: (ActionLayout) -> Unit,
) {
    val state = remember(onAdd) { ContextMenuState() }
    ContextMenuArea(
        state = state,
        items = {
            listOf(
                ContextMenuItem("Repeat while active") { onAdd(ActionLayout.RepeatWhileActive()) },
                ContextMenuItem("Retry until result") { onAdd(ActionLayout.RetryUntilResult()) },
                ContextMenuItem("Passing") { onAdd(ActionLayout.Passing()) },
                ContextMenuItem("Action") { onAdd(ActionLayout.Action()) },
            )
        },
    ) {
        IconActionButton(
            key = AllIconsKeys.General.Add,
            contentDescription = "add new action", // todo translate???
            modifier = modifier.sizeIn(minWidth = step, minHeight = step),
            onClick = { state.status = ContextMenuState.Status.Open(Rect(Offset.Zero, Size(100))) },
        )
    }
}