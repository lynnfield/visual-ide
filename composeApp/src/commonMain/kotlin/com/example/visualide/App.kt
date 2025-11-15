@file:OptIn(ExperimentalUuidApi::class)

package com.example.visualide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

//region color palette
private val DarkNeonColorScheme = darkColorScheme(
    background = Color(0xFF010203),      // Near-black background
    surface = Color(0xFF0A0A14),        // Slightly lighter surface for cards/dialogs
    onBackground = Color(0xFFE0E0E0),   // Light gray text on background
    onSurface = Color(0xFFE0E0E0),      // Light gray text on surface
    primary = Color(0xFF00FFFF),        // Electric Blue/Cyan for primary elements
    secondary = Color(0xFFFF00FF),      // Magenta for secondary elements
    tertiary = Color(0xFF6A00FF)         // Purple for accents
    // You can customize other colors like error, etc.
)

private val LightNeonColorScheme = lightColorScheme(
    background = Color(0xFFFFFFFF),      // White background
    surface = Color(0xFFF5F5FA),        // Light gray surface for cards/dialogs
    onBackground = Color(0xFF212121),   // Charcoal text on background
    onSurface = Color(0xFF212121),      // Charcoal text on surface
    primary = Color(0xFF39FF14),        // Lime Green for primary elements
    secondary = Color(0xFFFFAC1C),      // Intense Orange for secondary elements
    tertiary = Color(0xFF00BFFF)         // Sky Blue for accents
    // You can customize other colors like error, etc.
)
//endregion

//region data model
sealed class ActionLayout {
    data class RepeatWhileActive(
        val body: MutableState<ActionLayout?> = mutableStateOf(null)
    ) : ActionLayout()

    data class RetryUntilResult(
        val body: MutableState<ActionLayout?> = mutableStateOf(null)
    ) : ActionLayout()

    data class Sequential(
        val body: SnapshotStateList<ActionLayout> = mutableStateListOf()
    ) : ActionLayout()

    data class Action(
        val name: MutableState<String> = mutableStateOf("New Action")
    ) : ActionLayout()
}

data class ActionDefinition(
    val name: MutableState<String>,
    val body: MutableState<ActionLayout?> = mutableStateOf(null),
    val id: Uuid = Uuid.random(),
)
//endregion

//region render
val step = 48.dp

@Composable
fun ActionLayout.Action.Render(modifier: Modifier = Modifier) {
    TextBlock(name, modifier = modifier)
}

@Composable
fun ActionLayout.Sequential.Render(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.width(IntrinsicSize.Min),
    ) {
        if (body.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(step),
            ) {
                body.forEach { it.Render(Modifier.width(IntrinsicSize.Max)) }
            }
        }
        AddNewLayoutSelector("+") { body += it }
    }
}

@Composable
fun ActionLayout.RepeatWhileActive.Render(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock("repeat while active")
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(paddings)
            ?: AddNewLayoutSelector(modifier = paddings, onAdd = body::value::set)
    }
}

@Composable
fun ActionLayout.RetryUntilResult.Render(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock("retry until result")
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(paddings)
            ?: AddNewLayoutSelector(modifier = paddings, onAdd = body::value::set)
    }
}

@Composable
fun ActionLayout.Render(modifier: Modifier = Modifier) = when (this) {
    is ActionLayout.Action -> Render(modifier)
    is ActionLayout.RepeatWhileActive -> Render(modifier)
    is ActionLayout.RetryUntilResult -> Render(modifier)
    is ActionLayout.Sequential -> Render(modifier)
}

@Composable
fun ActionDefinition.Render(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock(name)
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(paddings)
            ?: AddNewLayoutSelector(modifier = paddings, onAdd = body::value::set)
    }
}
//endregion

@OptIn(ExperimentalTime::class)
@Composable
@Preview
fun App() {
    val useDarkTheme = isSystemInDarkTheme()
    println(useDarkTheme)
    val colors = if (useDarkTheme) DarkNeonColorScheme else LightNeonColorScheme

//    val actions = listOf(
//        ActionDefinition(
//            "EntryPoint",
//            null,
//            null,
//            ActionLayout.RetryUntilResult(
//                ActionLayout.RepeatUntilActive(
//                    ActionLayout.Sequential(
//                        listOf(
//                            ActionLayout.Action("login"),
//                            ActionLayout.Action("process anonymous state"),
//                            ActionLayout.Action("process authorised state"),
//                        )
//                    )
//                )
//            )
//        )
//    ).associateBy { it.name }

    val actions = remember { mutableStateMapOf<Uuid, ActionDefinition>() }
    var currentActionId by remember(actions) { mutableStateOf(actions.keys.firstOrNull()) }

    MaterialTheme(colorScheme = colors) {
        Scaffold {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .horizontalScroll(ScrollState(0))
                ) {
                    currentActionId?.let { actions[it] }?.Render(Modifier.wrapContentSize())
                        ?: Button(
                            onClick = {
                                val action = ActionDefinition(
                                    name = mutableStateOf("New action")
                                )
                                actions[action.id] = action
                                currentActionId = action.id
                            }
                        ) {
                            Text("Nothing to show. Click to add an action.")
                        }
                }
            }
        }
    }
}