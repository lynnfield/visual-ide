package com.genovich.visualide.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jetbrains.uast.UExpression

interface ActionLayout : Iterable<ActionLayout> {

    @Composable
    fun Render(onRemove: () -> Unit, modifier: Modifier = Modifier.Companion)

    fun generate(input: String): String

    fun interface UExpressionParser<out T : ActionLayout?> {
        fun parse(expression: UExpression): Result<T>
    }

    companion object {
        fun parse(expression: UExpression): Result<ActionLayout?> = runCatching {
            runBlockingCancellable {
                val parsers = listOf(
                    Action,
                    RetryUntilResult,
                    RepeatWhileActive,
                    Passing,
                    TodoStub,
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
        }.recoverCatching { throw Exception("while parsing ${expression.asSourceString()}", it) }
    }
}