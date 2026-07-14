package com.genovich.visualide

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

/**
 * Architecture guard for design.md §4.5 / D12 (host-abstraction boundary) and
 * `docs/implementation-plan.md` Step 4 (H5): the `actions` package is the host-agnostic engine and
 * must never import UAST directly — all UAST/PSI access goes through
 * `com.genovich.visualide.analysis.KotlinAnalysis`, the sole adapter, which converts to the
 * engine's own IR (`com.genovich.visualide.analysis.Expr`/`ClassInfo`) before anything in `actions`
 * sees it. If this test fails, some file under `actions` started importing `org.jetbrains.uast`
 * again — route it through `KotlinAnalysis`/`Expr` instead.
 */
class ActionsPackageHostAgnosticTest {

    @Test
    fun actionsPackageDoesNotImportUast() {
        val actionsDir = File("src/main/kotlin/com/genovich/visualide/actions")
        check(actionsDir.isDirectory) { "expected $actionsDir to exist (wrong working directory?)" }

        val offenders = actionsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import org.jetbrains.uast") } }
            .map { it.name }
            .toList()

        assertThat(offenders).describedAs("actions/*.kt files importing org.jetbrains.uast directly").isEmpty()
    }
}
