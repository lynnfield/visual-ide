package com.genovich.visualide.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text

@OptIn(ExperimentalJewelApi::class)
@Composable
fun AddNewLayoutSelector(
    buttonText: String = "Click to add",
    modifier: Modifier = Modifier,
    onAdd: (ActionLayout) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val onAdd: (ActionLayout) -> Unit = {
        showMenu = false
        onAdd(it)
    }
    DefaultButton(
        modifier = modifier,
        onClick = { showMenu = !showMenu }
    ) {
        Text(buttonText)
    }
    val items = listOf(
        ActionLayout.RepeatWhileActive(),
        ActionLayout.RetryUntilResult(),
        ActionLayout.Sequential(),
        ActionLayout.Action(),
    )
    ListComboBox(
        items = items,
        selectedIndex = 0,
        onSelectedItemChange = { onAdd(items[it]) },
        itemKeys = { _, item -> item.toString() },
    ) { item, _, _ ->
        when (item) {
            is ActionLayout.Action -> Text("Action")
            is ActionLayout.RepeatWhileActive -> Text("Repeat while active")
            is ActionLayout.RetryUntilResult -> Text("Retry until result")
            is ActionLayout.Sequential -> Text("Sequential")
        }
    }
}