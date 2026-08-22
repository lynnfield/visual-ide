package com.genovich.visualide.analysis

/**
 * The engine's normalized view of a generated `<Name>Assembly` top-level function (design.md
 * §2.8, §4.5, D12) — exactly the surface
 * [com.genovich.visualide.actions.ActionDefinition.parseAssembly] needs, built once by
 * [KotlinAnalysis] from a UAST `UMethod` (Kotlin surfaces a top-level function's UAST node as a
 * method of a synthetic `<FileName>Kt` facade class — see [KotlinAnalysis.parseFunction]) so the
 * `actions` package never touches UAST/PSI itself.
 */
data class FunctionInfo(
    val name: String?,
    /** Declaration-order parameters; a null [Parameter.defaultValue] means the parameter is required. */
    val parameters: List<Parameter>,
    /** The function's single-expression body (`= expr`), converted to [Expr]; null if absent/unparseable. */
    val bodyExpression: Expr?,
) {
    data class Parameter(val name: String?, val defaultValue: Expr?)
}
