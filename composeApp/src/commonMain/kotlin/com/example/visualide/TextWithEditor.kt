package com.example.visualide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

@Composable
fun TextWithEditor(
    state: MutableState<String>,
    modifier: Modifier = Modifier.Companion,
) {
    var editor by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clickable { editor = !editor },
        contentAlignment = Alignment.Center,
    ) {
        if (editor) {
            var text by remember { state }
            OutlinedTextField(
                value = text,
                singleLine = true,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { editor = false }
                ),
            )
        } else {
            Text(state.value)
        }
    }
}