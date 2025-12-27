package com.genovich.visualide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun ItemBox(modifier: Modifier = Modifier.Companion, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = step)
            .border(Dp.Hairline, JewelTheme.globalColors.borders.normal),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
