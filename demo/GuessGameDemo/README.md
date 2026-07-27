# GuessGame demo project

A tiny, self-contained Kotlin project you can open inside the plugin's sandbox IDE to record a
demo. It's deliberately **outside** the main `VisualIDE` Gradle build (its own `settings.gradle.kts`
/ wrapper) so opening it doesn't touch the plugin's own build.

It contains the full rung-5 specimen from `docs/example-rung5.md`: a T-function-driven
guess-the-number game (`askGuess` → `Tuple(input, guess)` → `compare` → `Branch` on
`TooLow`/`TooHigh`/`Correct`, each case calling the `showResult` T-function).

## Layout

```
demo/GuessGameDemo/
├── src/main/kotlin/
│   ├── com/genovich/components/Components.kt   # stub "library" (Action, Show, repeatWhileActive, ...)
│   └── specimen/
│       ├── Comparison.kt                       # sealed Comparison { TooLow, TooHigh, Correct }
│       └── GuessGame.kt                        # the generated Action class — open this one
└── reference-output/
    ├── GuessGameAssembly.kt                    # what clicking "Save" should (re)generate
    └── GuessGameUiStateFlow.kt                 # ditto — for comparison, not compiled
```

`Components.kt`/`Comparison.kt` are the same fixtures the plugin's own tests use
(`plugin/src/test/testData/specimen/`) — just enough for UAST to resolve every symbol the
generated code calls, without depending on a real external library.

## Recording steps

1. From the repo root, launch the sandbox IDE: `./gradlew :plugin:runIde`.
2. In the sandbox instance: **File > Open…** and pick this folder (`demo/GuessGameDemo`). Open it
   as its own project/window — let Gradle sync once (it's just `kotlin("jvm")`, nothing exotic).
3. Open `src/main/kotlin/specimen/GuessGame.kt` in the editor.
4. Open the plugin's tool window (stripe button on the side, or **View > Tool Windows > VisualIDE**).
   Selecting/focusing the `GuessGame.kt` editor tab is what triggers the parse — the diagram should
   render automatically: a "repeat while active" node containing a pipeline of
   `askGuess → Tuple → compare → branch (TooLow / TooHigh / Correct → showResult)`, with the
   `askGuess`/`showResult` port checkboxes already showing as T-functions.
5. Good beats to show on camera:
   - The diagram appearing the moment `GuessGame.kt` gets focus (round-trip: code → diagram).
   - Toggling a "T-function" checkbox on/off for a port.
   - Adding a node via one of the "+" buttons in an empty slot.
   - Clicking **Save** — this regenerates `GuessGame.kt`, `GuessGameAssembly.kt`, and
     `GuessGameUiStateFlow.kt` (diagram → code). Compare the two generated files against
     `reference-output/` if you want to sanity-check them.

## Known rough edges (don't be surprised by these on camera)

- **Save always writes to `src/main/kotlin/com/example/`**, regardless of the original file's
  package (`specimen` here) — a hardcoded `// todo make configurable` in
  `VisualIdeToolWindowFactory.kt`. This demo project's layout (`src/main/kotlin` at the project
  root) exists specifically so that path resolves.
- The freshly generated files won't be reformatted (no trailing-comma/indentation cleanup) until
  you run **Code > Reformat Code** on them — `reference-output/*.kt` shows the raw shape you should
  expect.
- The loop never exits — `repeatWhileActive` has no break/return primitive yet (see
  `docs/example-rung5.md`'s limitations). This specimen demonstrates the diagram round-trip, not
  playable game logic.
