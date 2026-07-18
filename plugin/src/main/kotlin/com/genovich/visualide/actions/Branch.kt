package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.WhenExpr
import com.genovich.visualide.analysis.singleReturnExpression
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.RemoveButton
import com.genovich.visualide.ui.TextBlock

/**
 * Branch (design.md §1.5/D2): domain branching over a **named sealed type**, one labeled out-port
 * per case, exhaustive and flat (no `OneOfN` nesting). [cases] preserve declaration order
 * (design.md §2.7 — `when` clauses in declaration order); the subject is always bound to a fixed
 * `val branch = …` (canonical form, so parsing doesn't need to recover a user-chosen name).
 *
 * **Typing note:** every case body is inferred against the *same* [input] type (the sealed
 * supertype), not the `is Case ->` narrowed subtype — this engine's structural type inference has
 * no flow-sensitive smart-casting, only per-port-name placeholder types (see
 * `docs/implementation-plan.md`'s "repeated port names" caveat). This is still sound generated
 * Kotlin: real smart-casting inside each arm only *narrows* the compiler's view of the value, and
 * passing a narrower type where the (uniform, supertype) port expects the wider type always
 * type-checks. [Branch]'s own result type is the *first* case's inferred type (no unification
 * across cases) — correct whenever, as in the `GuessGame` specimen, every case actually produces
 * the same type.
 *
 * **Exhaustiveness note:** [generate] always appends a trailing `else -> TODO(...)` arm. Real
 * compiler-enforced exhaustiveness (design.md §1.5 — "incomplete until every case is wired") would
 * need the `when` subject typed as the actual sealed supertype; this engine's structural type
 * inference only ever mints opaque type variables (rung 1, H2), never real bounded domain types,
 * so Kotlin can't prove exhaustiveness from `is Case ->` checks against a bare type parameter. The
 * diagram's own completeness invariant (every [Case] has a wired [Case.body]) is enforced here, at
 * the model level, instead — the `else` arm is regenerated boilerplate, not user data, and is
 * dropped again on [parse].
 */
data class Branch(
    val cases: SnapshotStateList<Case> = mutableStateListOf()
) : ActionLayout {

    constructor(cases: List<Case>) : this(mutableStateListOf(*cases.toTypedArray()))

    data class Case(
        val caseTypeQualifiedName: MutableState<String>,
        val body: MutableState<ActionLayout?> = mutableStateOf(null),
    ) {
        constructor(caseTypeQualifiedName: String, body: ActionLayout? = null) :
            this(mutableStateOf(caseTypeQualifiedName), mutableStateOf(body))
    }

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Branch)
        cases.forEach { case -> case.body.value?.also { yieldAll(it) } }
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Column(modifier) {
            TextBlock(text = "branch", onRemove = onRemove)
            cases.forEach { case ->
                Row {
                    TextBlock(case.caseTypeQualifiedName)
                    case.body.value?.Render(onRemove = { case.body.value = null }, modifier = Modifier)
                        ?: AddNewLayoutButton(onAdd = case.body::value::set)
                    RemoveButton(case.caseTypeQualifiedName.value, onRemove = { cases.remove(case) })
                }
            }
            AddNewLayoutButton { layout -> cases.add(Case("NewCase", layout)) }
        }
    }

    override fun generate(input: String): String {
        if (cases.isEmpty()) return TodoStub.generate()
        val clauseLines = cases.map { case ->
            val body = case.body.value?.generate(SUBJECT_NAME) ?: TodoStub.generate()
            "    is ${case.caseTypeQualifiedName.value} -> $body"
        } + "    else -> ${TodoStub.generate()}"
        return clauseLines.joinToString(
            separator = "\n",
            prefix = "when (val $SUBJECT_NAME = $input) {\n",
            postfix = "\n}",
        )
    }

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String = cases
        .map { case -> case.body.value?.inferType(input, fresh, ports, scope) ?: TYPE_NOTHING }
        .firstOrNull()
        ?: TYPE_NOTHING

    companion object : ActionLayout.ExpressionParser<Branch> {
        const val SUBJECT_NAME = "branch"

        override fun parse(expression: Expr): Result<Branch> = runCatching {
            checkNotNull(expression as? WhenExpr) { "not a when expression" }
                .also { check(it.subjectInitializer != null) { "when subject should bind a named `val`" } }
                .cases
                // The trailing `else -> TODO(...)` arm is regenerated boilerplate (see the class
                // KDoc's "Exhaustiveness note"), not modeled data — its caseValues are empty, so
                // it has no resolvable `is Type`; drop it rather than failing the whole parse.
                .filter { it.caseTypeQualifiedName != null }
                .also { check(it.isNotEmpty()) { "when should have at least one case" } }
                .map { case ->
                    val caseType = checkNotNull(case.caseTypeQualifiedName) { "case should have a resolvable `is` type: ${case.sourceText}" }
                    val body = checkNotNull(case.singleReturnExpression) { "case body should be a single expression: ${case.sourceText}" }
                        .let { checkNotNull(ActionLayout.parse(it).getOrThrow()) { "failed to convert case body to an ActionLayout" } }
                    Case(caseType, body)
                }
                .let { Branch(it) }
        }.recoverCatching {
            throw Exception("failed to parse ${Branch::class.qualifiedName}", it)
        }
    }
}
