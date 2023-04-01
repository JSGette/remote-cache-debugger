import com.gette.debugger.Protos
import com.gette.debugger.Protos.ExecutionEnvironmentVariables
import com.gette.debugger.Protos.ExecutionInputs
import com.google.devtools.build.lib.exec.Protos.SpawnExec
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

val sha256 = MessageDigest.getInstance("SHA-256")

fun main(args: Array<String>) {
    mergeSpawnExecs("D:/exec2.log", "D:/exec3.log")
}

fun mergeSpawnExecs(pathA: String, pathB: String) {
    val aExecCounter: Int
    var bExecCounter: Int = 0
    var cacheHits: Int = 0
    val aSpawnExecs: HashMap<String, SpawnExec> = HashMap()
    var ins = File(pathA).inputStream()
    while (ins.available() > 0) {
        val spawnExec = getNextSpawnExec(ins)
        aSpawnExecs[spawnExec.first] = spawnExec.second
    }
    aExecCounter = aSpawnExecs.size

    ins = File(pathB).inputStream()
    while (ins.available() > 0) {
        val spawnExec = getNextSpawnExec(ins)
        bExecCounter++
        if (!spawnExec.second.remoteCacheHit) {
            val aEnvVars: Map<String, String> =
                aSpawnExecs[spawnExec.first]!!.environmentVariablesList.associate { it.name to it.value }
            val bEnvVars: Map<String, String> =
                spawnExec.second.environmentVariablesList.associate { it.name to it.value }
            val mergedEnvVars =
                calculateDiff(aEnvVars, bEnvVars).map {
                    ExecutionEnvironmentVariables.newBuilder().setName(it.key).setAValue(it.value.first)
                        .setBValue(it.value.second).build()
                }
            val aInputs = aSpawnExecs[spawnExec.first]!!.inputsList.associate { it.path to it.digest }
            val bInputs = spawnExec.second.inputsList.associate { it.path to it.digest }
            val mergedInputs = calculateDiff(aInputs, bInputs).map {
                ExecutionInputs.newBuilder().setPath(it.key).setAHash(it.value.first.hash)
                    .setBHash(it.value.second.hash).build()
            }
            var mergedSpawnExec =
                Protos.MergedSpawnExec.newBuilder().setExecutionHash(spawnExec.first)
                    .addAllListedOutputs(spawnExec.second.listedOutputsList)
                    .addAllEnvVars(mergedEnvVars.toMutableList())
                    .addAllInputs(mergedInputs.toMutableList())
            println(mergedSpawnExec.toString())

        } else {
            cacheHits++
        }
    }
    if (aExecCounter != bExecCounter) {
        throw UnsupportedOperationException("Number of executions isn't the same. Unsupported analysis!")
    }
    println("====================REPORT====================")
    println("Spawned Executions: ${aExecCounter}")
    println("Cache Hits: ${cacheHits}")
    println("Cache Hit Rate: ${"%.2f".format(cacheHits.toFloat() / aExecCounter.toFloat() * 100)}%")
    println("==============================================")
}

fun getNextSpawnExec(ins: InputStream): Pair<String, SpawnExec> {
    val spawnExec = SpawnExec.parseDelimitedFrom(ins)
    val execHash = calculateExecHash(spawnExec.listedOutputsList.toString())
    return Pair(execHash, spawnExec)
}

fun calculateExecHash(input: String): String {
    return sha256.digest(input.toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}

fun <T> calculateDiff(aMap: Map<String, T>, bMap: Map<String, T>): Map<String, Pair<T, T>> {
    return aMap.filterKeys { bMap.containsKey(it) }.filter { (key, value) ->
        aMap[key] != bMap[key]
    }.map { Triple(it.key, aMap[it.key], bMap[it.key]) }.associate { it.first to Pair(it.second!!, it.third!!) }
        .filter { it.value.toList().isNotEmpty() }
}
