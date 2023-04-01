import com.google.devtools.build.lib.exec.Protos.Digest
import java.io.PrintWriter

data class MergedSpawnExec(
    val listedOutputs: List<String>,
    val aEnvVars: Map<String, String>,
    val aInputs: Map<String, Digest>,
) {
    var bEnvVars: Map<String, String> = HashMap()
    var bInputs: Map<String, Digest> = HashMap()
    var presentInBothExecs: Boolean = false

    constructor(
        _listedOuputs: List<String>,
        _aEnvVars: Map<String, String>,
        _aInputs: Map<String, Digest>,
        _bEnvVars: Map<String, String>,
        _bInputs: Map<String, Digest>,
        _presentInBothExecs: Boolean
    ) : this(_listedOuputs, _aEnvVars!!, _aInputs!!) {
        bEnvVars = _bEnvVars
        bInputs = _bInputs
        presentInBothExecs = _presentInBothExecs
    }

    fun <T> printDiff(map: Map<String, Pair<T, T>>, out: PrintWriter) {
        map.map {
            """   ${it.key} {
            |               PREVIOUS VALUE: ${it.value.first}
            |                    NEW VALUE: ${it.value.second}
            |        }
        """.trimMargin()
        }.forEach { out.println(it) }
    }

    fun printEnvVarsDiff(out: PrintWriter) {
        out.println("Environment Variables {")
        printDiff(calculateDiffEnv(), out)
        out.println("}")
    }

    fun printInputsDiff(out: PrintWriter) {
        out.println("Inputs {")
        printDiff(calculateDiffInputs(), out)
        out.println("}")
    }

    fun calculateDiffEnv(): Map<String, Pair<String, String>> {
        return calculateDiff(aEnvVars, bEnvVars)
    }

    fun calculateDiffInputs(): Map<String, Pair<Digest, Digest>> {
        return calculateDiff(aInputs, bInputs)
    }

    private fun <T> calculateDiff(aMap: Map<String, T>, bMap: Map<String, T>): Map<String, Pair<T, T>> {
        return aMap.filterKeys { bMap.containsKey(it) }.filter { (key, value) ->
            aMap[key] != bMap[key]
        }.map { Triple(it.key, aMap[it.key], bMap[it.key]) }.associate { it.first to Pair(it.second!!, it.third!!) }
            .filter { it.value.toList().isNotEmpty() }
    }
}
