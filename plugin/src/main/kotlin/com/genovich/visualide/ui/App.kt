@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

//region data model
sealed class ActionLayout : Iterable<ActionLayout> {
    data class RepeatWhileActive(
        val body: MutableState<ActionLayout?> = mutableStateOf(null)
    ) : ActionLayout() {
        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@RepeatWhileActive)
            body.value?.also { yieldAll(it) }
        }
    }

    data class RetryUntilResult(
        val body: MutableState<ActionLayout?> = mutableStateOf(null)
    ) : ActionLayout() {
        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@RetryUntilResult)
            body.value?.also { yieldAll(it) }
        }
    }

    data class Sequential(
        val body: SnapshotStateList<ActionLayout> = mutableStateListOf()
    ) : ActionLayout() {
        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@Sequential)
            body.forEach { yieldAll(it) }
        }
    }

    data class Action(
        val name: MutableState<String> = mutableStateOf("New Action")
    ) : ActionLayout() {
        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@Action)
        }
    }
}

data class ActionDefinition(
    val name: MutableState<String>,
    val body: MutableState<ActionLayout?> = mutableStateOf(null),
    val id: Uuid = Uuid.random(),
) {
    constructor(
        name: String,
        body: ActionLayout? = null,
        id: Uuid = Uuid.random(),
    ) : this(
        name = mutableStateOf(name),
        body = mutableStateOf(body),
        id = id,
    )
}
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
            onAdd = { body.add(it) },
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
fun App(
    onSave: (ActionDefinition) -> Unit,
    actions: SnapshotStateMap<Uuid, ActionDefinition>,
    currentActionId: MutableState<Uuid?>,
) {
    val currentAction = currentActionId.value?.let { actions[it] }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.Center)
                .horizontalScroll(ScrollState(0))
        ) {
            currentAction?.Render(Modifier.wrapContentSize())
                ?: DefaultButton(
                    onClick = {
                        val action = ActionDefinition(
                            name = mutableStateOf("New action")
                        )
                        actions[action.id] = action
                        currentActionId.value = action.id
                    }
                ) {
                    Text("Nothing to show. Click to add an action.")
                }
        }

        DefaultButton(
            onClick = { currentAction?.also(onSave) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enabled = currentAction != null
        ) {
            Text(
                text = "Save",
                fontSize = 16.sp,
            )
        }
    }
}