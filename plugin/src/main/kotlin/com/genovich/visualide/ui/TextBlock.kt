package com.genovich.visualide.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TextBlock(
    text: MutableState<String>,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ItemBox(modifier) {
        TextWithEditor(
            state = text,
            modifier = Modifier.padding(8.dp),
        )
        onRemove?.also { RemoveButton(text.value, onRemove, Modifier.align(TopEnd)) }
    }
}

@Composable
fun TextBlock(
    text: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ItemBox(modifier) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
        )
        onRemove?.also { RemoveButton(text, onRemove, Modifier.align(TopEnd)) }
    }
}