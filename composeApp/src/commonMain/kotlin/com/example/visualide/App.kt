@file:OptIn(ExperimentalUuidApi::class)

package com.example.visualide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
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
    data class RepeatUntilActive(val body: ActionLayout) : ActionLayout()
    data class RetryUntilResult(val body: ActionLayout) : ActionLayout()
    data class Sequential(val body: List<ActionLayout>) : ActionLayout()
    data class Action(val name: String) : ActionLayout()
}

data class ActionDefinition(
    val name: MutableState<String>,
    val body: MutableState<ActionLayout?> = mutableStateOf(null),
    val id: Uuid = Uuid.random(),
)
//endregion

//region blocks width calculations
@Suppress("UnusedReceiverParameter")
val ActionLayout.Action.width: Int
    get() = 3
val ActionLayout.Sequential.width: Int
    get() = body.sumOf { 1 + it.width + 1 }
val ActionLayout.RepeatUntilActive.width: Int
    get() = 1 + body.width + 1
val ActionLayout.RetryUntilResult.width: Int
    get() = 1 + body.width + 1
val ActionLayout.width: Int
    get() = when (this) {
        is ActionLayout.Action -> width
        is ActionLayout.RepeatUntilActive -> width
        is ActionLayout.RetryUntilResult -> width
        is ActionLayout.Sequential -> width
    }
val ActionDefinition.width: Int
    get() = 1 + (body.value?.width ?: 1) + 1
//endregion

//region render
val step = 48.dp

@Composable
fun ActionLayout.Action.render(modifier: Modifier = Modifier) {
    Text(
        name,
        modifier = modifier
            .size(3 * step, step)
            .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun ActionLayout.Sequential.render(modifier: Modifier = Modifier) {
    Text("sequential", modifier = modifier)
}

@Composable
fun ActionLayout.RepeatUntilActive.render(modifier: Modifier = Modifier) {
    Text(
        "repeat until active",
        modifier = modifier.size(width * step, step)
            .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
        textAlign = TextAlign.Center
    )
}

@Composable
fun ActionLayout.RetryUntilResult.render(modifier: Modifier = Modifier) {
    Text(
        "retry until result",
        modifier = Modifier.size(width * step, step)
            .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
        textAlign = TextAlign.Center
    )
}

@Composable
fun ActionLayout.render(modifier: Modifier = Modifier) = when (this) {
    is ActionLayout.Action -> render(modifier)
    is ActionLayout.RepeatUntilActive -> render(modifier)
    is ActionLayout.RetryUntilResult -> render(modifier)
    is ActionLayout.Sequential -> render(modifier)
}

@Composable
fun ActionDefinition.render(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
    ) {
        TextWithEditor(
            state = name,
            modifier = Modifier
                .fillMaxWidth()
                .height(step)
                .border(Dp.Hairline, MaterialTheme.colorScheme.primary)
        )
        body.value?.render(Modifier.padding(horizontal = step)) ?: run {
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
                    text = { Text("Repeat until active") },
                    onClick = { println("add repeat until active") }
                )
            }
        }
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
                    currentActionId?.let { actions[it] }?.render(Modifier.wrapContentSize())
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