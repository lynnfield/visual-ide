package com.genovich.visualide.actions

import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

object TodoStub : ActionLayout.UExpressionParser<ActionLayout?> {
    fun generate(): String = """TODO("implement body")"""

    override fun parse(expression: UExpression): Result<Nothing?> = runCatching {
        checkNotNull(expression as? UCallExpression) { "is not a call expression" }
            .also { check(it.methodName == "TODO") { "should be named `TODO`" } }
        null
    }.recoverCatching {
        throw Exception("failed to parse TODO()", it)
    }
}