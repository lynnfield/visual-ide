@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genovich.visualide.actions.ActionDefinition
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val step = 48.dp

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