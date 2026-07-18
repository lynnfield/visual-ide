package com.genovich.visualide.analysis

import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.findIsInstanceAnd
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.tryResolveNamed

/**
 * The `KotlinAnalysis` host adapter (design.md §4.5, D12; `docs/implementation-plan.md` Step 4,
 * H5): `UAST/PSI → engine IR`. This is the **only** place in the plugin that converts UAST into
 * [Expr]/[ClassInfo] — everything in `com.genovich.visualide.actions` consumes the IR only, never
 * UAST directly, so the engine is reusable unchanged behind a different host adapter later.
 */
object KotlinAnalysis {
    private const val INVOKE_METHOD_NAME = "invoke"
    private const val DIAGRAM_ANNOTATION_FQN = "com.genovich.components.Diagram"
    private const val CHECKSUM_ATTRIBUTE_NAME = "checksum"

    fun parseClass(uClass: UClass): ClassInfo = ClassInfo(
        name = uClass.name,
        superTypeQualifiedNames = uClass.uastSuperTypes.mapNotNull { it.getQualifiedName() },
        invokeBody = uClass.uastDeclarations
            .findIsInstanceAnd<UMethod> { it.name == INVOKE_METHOD_NAME }
            ?.uastBody
            ?.asSafely<UBlockExpression>()
            ?.expressions
            ?.firstIsInstanceOrNull<UReturnExpression>()
            ?.returnExpression
            ?.let { toExpr(it) },
        diagramChecksum = uClass.findAnnotation(DIAGRAM_ANNOTATION_FQN)
            ?.findAttributeValue(CHECKSUM_ATTRIBUTE_NAME)
            ?.evaluate() as? String,
    )

    fun toExpr(expression: UExpression): Expr {
        val sourceText = expression.asSourceString()
        val resolved = expression.tryResolveNamed()
        val resolvedName = resolved?.name
        val resolvedQualifiedName = resolved?.kotlinFqName?.asString()

        return when (expression) {
            is UQualifiedReferenceExpression -> QualifiedCall(
                sourceText = sourceText,
                resolvedName = resolvedName,
                resolvedQualifiedName = resolvedQualifiedName,
                receiver = toExpr(expression.receiver),
                selector = toExpr(expression.selector),
            )

            is UCallExpression -> Call(
                sourceText = sourceText,
                resolvedName = resolvedName,
                resolvedQualifiedName = resolvedQualifiedName,
                methodName = expression.methodName,
                receiver = expression.receiver?.let { toExpr(it) },
                arguments = expression.valueArguments.map { toExpr(it) },
            )

            is ULambdaExpression -> Lambda(
                sourceText = sourceText,
                statements = expression.body
                    .asSafely<UBlockExpression>()
                    ?.expressions
                    ?.map { toStmt(it) }
                    ?: emptyList(),
            )

            is USwitchExpression -> {
                val subject = localVariableOf(expression.expression)
                WhenExpr(
                    sourceText = sourceText,
                    subjectName = subject?.name,
                    subjectInitializer = subject?.uastInitializer?.let { toExpr(it) },
                    cases = expression.body.expressions
                        .filterIsInstance<USwitchClauseExpressionWithBody>()
                        .map { entry ->
                            val caseType = entry.caseValues
                                .singleOrNull()
                                .asSafely<UBinaryExpressionWithType>()
                                ?.typeReference
                                ?.getQualifiedName()
                            WhenCase(
                                sourceText = entry.asSourceString(),
                                caseTypeQualifiedName = caseType,
                                statements = entry.body.expressions.map { toStmt(it) },
                            )
                        },
                )
            }

            else -> Reference(
                sourceText = sourceText,
                resolvedName = resolvedName,
                resolvedQualifiedName = resolvedQualifiedName,
            )
        }
    }

    /**
     * Converts one statement of a block body (design.md §2.7's SSA-like normal form: named `val`
     * bindings, then a trailing expression). Total/best-effort like [toExpr] — an unrecognized
     * shape falls back to an [ExprStmt] wrapping the whole statement rather than throwing, so a
     * malformed or hand-written block never aborts the wider conversion.
     *
     * A block's trailing expression is wrapped by UAST — as an implicit [UReturnExpression] for a
     * lambda body, or as a [UYieldExpression] for a `when`-arm body (its equivalent of "this arm's
     * result") — either way unwrapped down to the expression underneath.
     */
    private fun toStmt(expression: UExpression): Stmt {
        val sourceText = expression.asSourceString()
        val local = localVariableOf(expression)
        val initializer = local?.uastInitializer

        return when {
            local != null && initializer != null ->
                ValStmt(sourceText, local.name, toExpr(initializer))

            expression is UReturnExpression ->
                ExprStmt(sourceText, toExpr(expression.returnExpression ?: expression))

            expression is UYieldExpression ->
                ExprStmt(sourceText, toExpr(expression.expression ?: expression))

            else -> ExprStmt(sourceText, toExpr(expression))
        }
    }

    /** `val name = …` as a single-variable [UDeclarationsExpression], if [expression] is one. */
    private fun localVariableOf(expression: UExpression?): ULocalVariable? =
        expression.asSafely<UDeclarationsExpression>()
            ?.declarations
            ?.singleOrNull()
            ?.asSafely<ULocalVariable>()
}
