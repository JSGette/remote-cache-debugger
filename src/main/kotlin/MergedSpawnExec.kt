import com.google.devtools.build.lib.exec.Protos.Digest

class MergedSpawnExec(
    val listedOutputs: List<String>,
    val aEnvVars: Map<String, String>?,
    val aInputs: Map<String, Digest>?
) {
    var bEnvVars: Map<String, String> = HashMap()
    var bInputs: Map<String, Digest> = HashMap()
    var presentInBothExecs: Boolean = false

    constructor(
        _listedOuputs: List<String>,
        _aEnvVars: Map<String, String>?,
        _aInputs: Map<String, Digest>?,
        _bEnvVars: Map<String, String>,
        _bInputs: Map<String, Digest>,
        _presentInBothExecs: Boolean
    ) : this(_listedOuputs, _aEnvVars, _aInputs) {
        bEnvVars = _bEnvVars
        bInputs = _bInputs
        presentInBothExecs = _presentInBothExecs
    }
}