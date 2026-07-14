package com.genovich.visualide.actions

import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr

object TodoStub : ActionLayout.ExpressionParser<ActionLayout?> {
    fun generate(): String = """TODO("implement body")"""

    override fun parse(expression: Expr): Result<Nothing?> = runCatching {
        checkNotNull(expression as? Call) { "is not a call expression" }
            .also { check(it.methodName == "TODO") { "should be named `TODO`" } }
        null
    }.recoverCatching {
        throw Exception("failed to parse TODO()", it)
    }
}