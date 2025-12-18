package com.genovich.visualide.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text

private enum class AddNewLayoutSelectorOptions {
    None,
    RepeatWhileActive,
    RetryUntilResult,
    Passing,
    Action,
}

@Suppress("ParamsComparedByRef")
@OptIn(ExperimentalJewelApi::class)
@Composable
fun AddNewLayoutSelector(
    buttonText: String = "Click to add",
    modifier: Modifier = Modifier,
    listState: SelectableLazyListState = rememberSelectableLazyListState(),
    onAdd: (ActionLayout) -> Unit,
) {
    val onAdd: (ActionLayout) -> Unit = { onAdd(it) }
    ListComboBox(
        modifier = modifier,
        items = AddNewLayoutSelectorOptions.entries,
        selectedIndex = 0,
        onSelectedItemChange = { index ->
            when (AddNewLayoutSelectorOptions.entries[index]) {
                AddNewLayoutSelectorOptions.None -> Unit
                AddNewLayoutSelectorOptions.RepeatWhileActive -> onAdd(ActionLayout.RepeatWhileActive())
                AddNewLayoutSelectorOptions.RetryUntilResult -> onAdd(ActionLayout.RetryUntilResult())
                AddNewLayoutSelectorOptions.Passing -> onAdd(ActionLayout.Passing())
                AddNewLayoutSelectorOptions.Action -> onAdd(ActionLayout.Action())
            }
        },
        itemKeys = { _, item -> item.name },
        listState = listState,
    ) { item, _, _ ->
        when (item) {
            AddNewLayoutSelectorOptions.None -> Text(buttonText)
            AddNewLayoutSelectorOptions.RepeatWhileActive -> Text("Repeat while active")
            AddNewLayoutSelectorOptions.RetryUntilResult -> Text("Retry until result")
            AddNewLayoutSelectorOptions.Passing -> Text("Passing")
            AddNewLayoutSelectorOptions.Action -> Text("Action")
        }
    }
}