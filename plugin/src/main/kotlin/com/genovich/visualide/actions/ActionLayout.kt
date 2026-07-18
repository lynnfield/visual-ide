package com.genovich.visualide.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Expr
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

interface ActionLayout : Iterable<ActionLayout> {

    @Composable
    fun Render(onRemove: () -> Unit, modifier: Modifier = Modifier.Companion)

    fun generate(input: String): String

    /**
     * Structural type inference (design doc rung 1 / H2). Given the [input] type expression,
     * returns this node's output type expression, recording each leaf port's `<in, out>` types
     * into [ports] (keyed by port name; first occurrence wins). [fresh] mints unique
     * type-variable names. [scope] is the rung-5 addition backing [Ref] (design.md §2.5): every
     * named value reachable at this point in the body — the composite's own `input` (seeded by
     * [ActionDefinition.signature]) plus each `Passing` step's `stepN` — mapped to its inferred
     * type, so `Ref("someName")` can recover a real type instead of guessing. Nodes that don't
     * introduce or consume named values (most of them) just thread it through unchanged.
     */
    fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String

    /**
     * Matches this node's own generated shape against the engine's expression IR (design.md §4.5,
     * D12) — never UAST/PSI directly. [com.genovich.visualide.analysis.KotlinAnalysis] is the sole
     * adapter that builds an [Expr] tree; every node parser downstream of it is host-agnostic.
     */
    fun interface ExpressionParser<out T : ActionLayout?> {
        fun parse(expression: Expr): Result<T>
    }

    companion object {
        fun parse(expression: Expr): Result<ActionLayout?> = runCatching {
            runBlockingCancellable {
                val parsers = listOf(
                    Action,
                    RetryUntilResult,
                    RepeatWhileActive,
                    Passing,
                    TodoStub,
                    Ref,
                    Tuple,
                    Branch,
                )
                    .map { async(Dispatchers.Default) { it.parse(expression) } }

                val (success, fails) = awaitAll(*parsers.toTypedArray()).partition { it.isSuccess }

                when (success.size) {
                    0 -> throw Exception("failed to parse as anything").also { exception ->
                        fails.forEach { exception.addSuppressed(it.exceptionOrNull()) }
                    }

                    1 -> success.single().getOrThrow()

                    else -> throw Exception("parsed several layouts").also { exception ->
                        success.forEach { result ->
                            val name = result.getOrThrow()?.let { it::class }?.qualifiedName
                            exception.addSuppressed(Exception("$name was parsed"))
                        }
                        fails.forEach { exception.addSuppressed(it.exceptionOrNull()) }
                    }
                }
            }
        }.recoverCatching { throw Exception("while parsing ${expression.sourceText}", it) }
    }
}