import com.gette.debugger.Protos
import com.gette.debugger.Protos.ExecutionEnvironmentVariables
import com.gette.debugger.Protos.ExecutionInputs
import com.google.devtools.build.lib.exec.Protos.SpawnExec
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

val sha256 = MessageDigest.getInstance("SHA-256")

fun main(args: Array<String>) {
    val parser = ArgParser("debugger")
    val first_exec_log by parser.option(
        ArgType.String,
        shortName = "first",
        description = "Path to first execution log"
    ).required()
    val second_exec_log by parser.option(
        ArgType.String,
        shortName = "second",
        description = "Path to second execution log"
    ).required()
    val output_binary_log by parser.option(
        ArgType.String,
        shortName = "ob",
        description = "Path to save output log in binary format"
    )
    val output_text_log by parser.option(
        ArgType.String,
        shortName = "o",
        description = "Path to save output log in text format"
    )
    parser.parse(args)
    mergeSpawnExecs(first_exec_log, second_exec_log, output_binary_log, output_text_log)
}

/**
 * Reads execution log files in binary format and merges
 * environment variables and inputs that are different
 * between 2 logs
 *
 * @param pathA  path to first execution log
 * @param pathB  path to second execution log
 * @param outputBinaryLogPath  path to save output in binary format
 * please refer to output.proto located in src/main/proto/ for more information
 * @param outputTextLogPath
 */
fun mergeSpawnExecs(pathA: String, pathB: String, outputBinaryLogPath: String?, outputTextLogPath: String?) {
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
    val textLogWriter = if (!outputTextLogPath.isNullOrEmpty()) File(outputTextLogPath) else null
    val binaryLogFile = if (!outputBinaryLogPath.isNullOrEmpty()) File(outputBinaryLogPath) else null

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
                    .build()
            println(mergedSpawnExec.toString())
            textLogWriter?.appendText(mergedSpawnExec.toString())
            if (binaryLogFile != null) FileOutputStream(binaryLogFile, true).use {
                mergedSpawnExec.writeDelimitedTo(
                    it
                )
            }
        } else {
            cacheHits++
        }
    }
    if (aExecCounter != bExecCounter) {
        val warning = "WARNING! Number of executions isn't the same across builds so results may be not correct!"
        println(warning)
        textLogWriter?.appendText(warning)
    }
    val reportText = """====================REPORT====================
Spawned Executions: ${aExecCounter}
Cache Hits: ${cacheHits}
Cache Hit Rate: ${"%.2f".format(cacheHits.toFloat() / aExecCounter.toFloat() * 100)}%
==============================================
    """.trimIndent()
    println(reportText)
    textLogWriter?.appendText(reportText)
}

/**
 * Reads inputStream and returns next SpawnExec
 * @param ins  inputStream of execution log file
 *
 * @return pair of execution hash and [SpawnExec]
 * Execution Hash is calculated based on listed outputs
 * converted into a string
 */
fun getNextSpawnExec(ins: InputStream): Pair<String, SpawnExec> {
    val spawnExec = SpawnExec.parseDelimitedFrom(ins)
    val execHash = calculateExecHash(spawnExec.listedOutputsList.toString())
    return Pair(execHash, spawnExec)
}

/**
 * Calculate SHA256 of a string
 *
 * @param input  string to calculate SHA256 for
 *
 * @return calculated hash
 */
fun calculateExecHash(input: String): String {
    return sha256.digest(input.toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}

/**
 * Compares 2 hashmaps with string keys and values of arbitrary class
 *
 * @param aMap  first map to compare
 * @param bMap  second map to compare first map with
 *
 * @return new hashmap with string keys and pairs of values
 * that are different between first and second maps
 */
fun <T> calculateDiff(aMap: Map<String, T>, bMap: Map<String, T>): Map<String, Pair<T, T>> {
    return aMap.filterKeys { bMap.containsKey(it) }.filter { (key, _) ->
        aMap[key] != bMap[key]
    }.mapValues { Pair(aMap[it.key]!!, bMap[it.key]!!) }
}
