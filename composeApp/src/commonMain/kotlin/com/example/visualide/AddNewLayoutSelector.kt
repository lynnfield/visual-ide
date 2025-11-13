package com.example.visualide

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AddNewLayoutSelector(actionLayoutState: MutableState<ActionLayout?>) {
    var showMenu by remember { mutableStateOf(false) }
    Button(
        modifier = Modifier.padding(horizontal = step),
        onClick = { showMenu = !showMenu }
    ) {
        Text("Body is empty. Click to add")
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Repeat while active") },
            onClick = { actionLayoutState.value = ActionLayout.RepeatWhileActive() }
        )
        DropdownMenuItem(
            text = { Text("Retry until result") },
            onClick = { actionLayoutState.value = ActionLayout.RetryUntilResult() }
        )
        DropdownMenuItem(
            text = { Text("Sequential") },
            onClick = { actionLayoutState.value = ActionLayout.Sequential() }
        )
        DropdownMenuItem(
            text = { Text("Action") },
            onClick = { actionLayoutState.value = ActionLayout.Action() }
        )
    }
}