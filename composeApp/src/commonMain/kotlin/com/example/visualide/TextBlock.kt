package com.example.visualide

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TextBlock(text: MutableState<String>, modifier: Modifier = Modifier.Companion) {
    TextWithEditor(
        state = text,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = step)
            .border(Dp.Hairline, MaterialTheme.colorScheme.primary)
            .padding(8.dp)
    )
}

@Composable
fun TextBlock(text: String, modifier: Modifier = Modifier.Companion) {
    Box(
        modifier = modifier
            .heightIn(min = step)
            .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
        )
    }
}