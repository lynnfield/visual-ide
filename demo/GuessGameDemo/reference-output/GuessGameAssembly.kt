package com.example

            fun <Input, T1, T2, T3> `GuessGameAssembly`(
    `uiStateFlow`: `GuessGameUiStateFlow`<Input, T1, T2, T3> = `GuessGameUiStateFlow`(),
    `askGuess`: com.genovich.components.Action<Input, T1> = com.genovich.components.Show(`uiStateFlow`.`askGuessFlow`),
    `compare`: com.genovich.components.Action<kotlin.Pair<Input, T1>, T2>,
    `showResult`: com.genovich.components.Action<T2, T3> = com.genovich.components.Show(`uiStateFlow`.`showResultFlow`),
): `GuessGame`<Input, T1, T2, T3> =
                `GuessGame`(
    `askGuess` = `askGuess`,
    `compare` = `compare`,
    `showResult` = `showResult`,
)
