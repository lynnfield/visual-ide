@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        constructor(body: ActionLayout?) : this(mutableStateOf(body))

        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@RepeatWhileActive)
            body.value?.also { yieldAll(it) }
        }
    }

    data class RetryUntilResult(
        val body: MutableState<ActionLayout?> = mutableStateOf(null)
    ) : ActionLayout() {
        constructor(body: ActionLayout?) : this(mutableStateOf(body))

        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@RetryUntilResult)
            body.value?.also { yieldAll(it) }
        }
    }

    data class Passing(
        val body: SnapshotStateList<ActionLayout> = mutableStateListOf()
    ) : ActionLayout() {
        constructor(body: List<ActionLayout>) : this(mutableStateListOf(*body.toTypedArray()))

        override fun iterator(): Iterator<ActionLayout> = iterator {
            yield(this@Passing)
            body.forEach { yieldAll(it) }
        }
    }

    data class Action(
        val name: MutableState<String> = mutableStateOf("New Action")
    ) : ActionLayout() {

        constructor(name: String) : this(mutableStateOf(name))

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
fun ActionLayout.Action.Render(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    TextBlock(name, onRemove = onRemove, modifier = modifier)
}

@Composable
fun ActionLayout.Passing.Render(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.width(IntrinsicSize.Min)) {
        Row {
            body.forEachIndexed { index, actionLayout ->
                AddNewLayoutButton { body.add(index, it) }
                actionLayout.Render(
                    onRemove = { body.remove(actionLayout) },
                    modifier = Modifier.width(IntrinsicSize.Max),
                )
            }
            AddNewLayoutButton { body.add(it) }
        }
        RemoveButton("Passing", onRemove, Modifier.align(TopEnd))
    }
}

@Composable
fun ActionLayout.RepeatWhileActive.Render(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock(
            text = "repeat while active",
            onRemove = onRemove,
        )
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(
            onRemove = { body.value = null },
            modifier = paddings,
        ) ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
    }
}

@Composable
fun ActionLayout.RetryUntilResult.Render(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock(
            text = "retry until result",
            onRemove = onRemove,
        )
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(
            onRemove = { body.value = null },
            modifier = paddings,
        ) ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
    }
}

@Composable
fun ActionLayout.Render(onRemove: () -> Unit, modifier: Modifier = Modifier) = when (this) {
    is ActionLayout.Action -> Render(onRemove, modifier)
    is ActionLayout.RepeatWhileActive -> Render(onRemove, modifier)
    is ActionLayout.RetryUntilResult -> Render(onRemove, modifier)
    is ActionLayout.Passing -> Render(onRemove, modifier)
}

@Composable
fun ActionDefinition.Render(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        TextBlock(name) // todo should I allow self-delete???
        val paddings = Modifier.padding(horizontal = step)
        body.value?.Render(onRemove = { body.value = null }, modifier = paddings)
            ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
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