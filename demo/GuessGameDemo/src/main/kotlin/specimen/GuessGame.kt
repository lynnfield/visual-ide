package specimen

@com.genovich.components.Node
@com.genovich.components.Diagram(version = 1, checksum = "sha256:14dbca001aa1caa7d2ea1e11a989a265a538f72a588d8806f39dd63991ef6501")
class `GuessGame`<Input, T1, T2, T3>(
    val `askGuess`: com.genovich.components.Action<Input, T1>,
    val `compare`: com.genovich.components.Action<kotlin.Pair<Input, T1>, T2>,
    val `showResult`: com.genovich.components.Action<T2, T3>,
) : com.genovich.components.Action<Input, Nothing>() {
    override suspend operator fun invoke(input: Input): Nothing =
        com.genovich.components.repeatWhileActive {
            run {
                val step1 = `askGuess`(input)
                val step2 = input.to(step1)
                val step3 = `compare`(step2)
                val step4 = when (val branch = step3) {
                    is specimen.TooLow -> `showResult`(branch)
                    is specimen.TooHigh -> `showResult`(branch)
                    is specimen.Correct -> `showResult`(branch)
                    else -> TODO("implement body")
                }
                step4
            }
        }
}
