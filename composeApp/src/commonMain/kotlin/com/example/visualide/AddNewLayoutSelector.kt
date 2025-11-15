package com.example.visualide

import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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
    Button(
        modifier = modifier,
        onClick = { showMenu = !showMenu }
    ) {
        Text(buttonText)
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Repeat while active") },
            onClick = { onAdd(ActionLayout.RepeatWhileActive()) },
        )
        DropdownMenuItem(
            text = { Text("Retry until result") },
            onClick = { onAdd(ActionLayout.RetryUntilResult()) },
        )
        DropdownMenuItem(
            text = { Text("Sequential") },
            onClick = { onAdd(ActionLayout.Sequential()) },
        )
        DropdownMenuItem(
            text = { Text("Action") },
            onClick = { onAdd(ActionLayout.Action()) },
        )
    }
}