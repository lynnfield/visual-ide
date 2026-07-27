package com.example

        class `GuessGameUiStateFlow`<Input, T1, T2, T3> {
            val `askGuessFlow`: com.genovich.components.MutableStateFlow<com.genovich.components.UiState<Input, T1>?> =
    com.genovich.components.MutableStateFlow(null)

val `showResultFlow`: com.genovich.components.MutableStateFlow<com.genovich.components.UiState<T2, T3>?> =
    com.genovich.components.MutableStateFlow(null)

            sealed interface Screen<Input, T1, T2, T3> {
                data class `AskGuess`<Input, T1, T2, T3>(val state: com.genovich.components.UiState<Input, T1>) : Screen<Input, T1, T2, T3>
    data class `ShowResult`<Input, T1, T2, T3>(val state: com.genovich.components.UiState<T2, T3>) : Screen<Input, T1, T2, T3>
            }

            val screen: com.genovich.components.StateFlow<Screen<Input, T1, T2, T3>?> =
                com.genovich.components.combine(
                    com.genovich.components.emitSelfWhenHaveValue(`askGuessFlow`) { Screen.`AskGuess`(it) },
        com.genovich.components.emitSelfWhenHaveValue(`showResultFlow`) { Screen.`ShowResult`(it) }
                ) { cases -> cases.firstOrNull { it != null } }
        }
