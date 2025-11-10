package com.example.visualide

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastMap
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Define the color palette
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

//region data model
sealed class ActionLayout {
    data class RepeatUntilActive(val body: ActionLayout) : ActionLayout()
    data class RetryUntilResult(val body: ActionLayout) : ActionLayout()
    data class Sequential(val body: List<ActionLayout>) : ActionLayout()
    data class Action(val name: String) : ActionLayout()
}

data class ActionDefinition(
    val name: String,
    val input: String?,
    val output: String?,
    val body: ActionLayout,
)
//endregion

//region blocks width calculations
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
    get() = 1 + body.width + 1
//endregion

//region render
val step = 48.dp

val stepSpacer: @Composable () -> Unit = { Spacer(Modifier.size(step)) }

typealias RenderTable = List<List<@Composable () -> Unit>>

fun ActionLayout.Action.render(): RenderTable = listOf(
    listOf(
        {
            Text(
                name,
                modifier = Modifier
                    .size(width * step, step)
                    .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
                textAlign = TextAlign.Center,
            )
        }
    )
)

fun ActionLayout.Sequential.render(): RenderTable =
    body.fastFold(listOf()) { acc, list ->
        val rendered = list.render()
        List(max(acc.size, rendered.size)) {
            acc.getOrElse(it) { listOf() } + rendered.getOrElse(it) { listOf() }
        }
    }

fun ActionLayout.RepeatUntilActive.render(): RenderTable = buildList {
    add(
        listOf(
            {
                Text(
                    "repeat until active",
                    modifier = Modifier.size(width * step, step)
                        .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
                    textAlign = TextAlign.Center
                )
            }
        )
    )
    addAll(
        body.render().fastMap {
            buildList {
                add(stepSpacer)
                addAll(it)
                add(stepSpacer)
            }
        }
    )
}

fun ActionLayout.RetryUntilResult.render(): RenderTable = buildList {
    add(
        listOf(
            {
                Text(
                    "retry until result",
                    modifier = Modifier.size(width * step, step)
                        .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
                    textAlign = TextAlign.Center
                )
            }
        )
    )
    addAll(
        body.render().fastMap {
            buildList {
                add(stepSpacer)
                addAll(it)
                add(stepSpacer)
            }
        }
    )
}

fun ActionLayout.render(): RenderTable = when (this) {
    is ActionLayout.Action -> render()
    is ActionLayout.RepeatUntilActive -> render()
    is ActionLayout.RetryUntilResult -> render()
    is ActionLayout.Sequential -> render()
}

fun ActionDefinition.render(): RenderTable = buildList {
    add(
        listOf(
            {
                Text(
                    name,
                    modifier = Modifier.size(width * step, step)
                        .border(Dp.Hairline, MaterialTheme.colorScheme.primary),
                    textAlign = TextAlign.Center
                )
            }
        )
    )
    addAll(
        body.render().fastMap {
            buildList {
                add(stepSpacer)
                addAll(it)
                add(stepSpacer)
            }
        }
    )
}
//endregion

@OptIn(ExperimentalTime::class)
@Composable
@Preview
fun App() {
    println(Clock.System.now().toString())
    val useDarkTheme = isSystemInDarkTheme()
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

    val actions = remember { mutableStateMapOf<String, ActionDefinition>() }
    var currentActionName by remember(actions) { mutableStateOf(actions.keys.firstOrNull()) }
    val toRender =
        remember(actions, currentActionName) { currentActionName?.let { actions[it] }?.render() }

    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .horizontalScroll(ScrollState(0))
            ) {
                if (toRender.isNullOrEmpty()) {
                    Button(
                        onClick = {
                            actions["New action"] = ActionDefinition(
                                "New action",
                                null,
                                null,
                                ActionLayout.Sequential(listOf())
                            )

                            currentActionName = "New action"
                        }
                    ) {
                        Text("Nothing to show. Click to add an action.")
                    }
                } else {
                    toRender.forEach { row ->
                        Row { row.forEach { it() } }
                    }
                }
            }
        }
    }
}