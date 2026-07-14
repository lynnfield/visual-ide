package com.genovich.visualide.analysis

/**
 * The engine's normalized view of a generated `ActionDefinition` class (design.md §4.5, D12) —
 * exactly the surface `ActionDefinition.parse` needs, built once by [KotlinAnalysis] from a UAST
 * `UClass` so the `actions` package never touches UAST/PSI itself.
 */
data class ClassInfo(
    val name: String?,
    val superTypeQualifiedNames: List<String>,
    /** The `invoke()` method's return expression, converted to [Expr]; null if absent/unparseable. */
    val invokeBody: Expr?,
    /** The `@Diagram(checksum = …)` value on the class, if the annotation is present. */
    val diagramChecksum: String?,
)
