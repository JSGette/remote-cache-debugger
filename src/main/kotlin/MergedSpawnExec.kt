import com.google.devtools.build.lib.exec.Protos.Digest

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
