package specimen

/**
 * The guess-the-number specimen's domain sealed type (design doc §6.2) — `Branch`'s test case
 * (`docs/example-rung6.md`). Cases are plain top-level objects, not nested inside [Comparison]:
 * simpler `is specimen.TooLow` FQNs for [Branch]'s generated `when`, no dotted nested-class syntax
 * to recognize on parse.
 */
sealed interface Comparison

object TooLow : Comparison
object TooHigh : Comparison
object Correct : Comparison
