@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.ui

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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
        AddNewLayoutSelector(
            buttonText = "+",
            listState = remember(body.size) { SelectableLazyListState(LazyListState()) },
            onAdd = { body += it },
        )
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
fun App() {
    isSystemInDarkTheme()

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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.Center)
                .horizontalScroll(ScrollState(0))
        ) {
            currentActionId?.let { actions[it] }?.Render(Modifier.wrapContentSize())
                ?: DefaultButton(
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