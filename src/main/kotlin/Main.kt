import com.gette.debugger.Protos
import com.gette.debugger.Protos.ExecutionEnvironmentVariables
import com.gette.debugger.Protos.ExecutionInputs
import com.gette.debugger.Protos.MergedSpawnExec
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
    val report = mergeSpawnExecs(first_exec_log, second_exec_log)
    println(report.toString())
}

/**
 * Compares 2 execution logs and merges actions
 * that are present in both executions but
 * have different envVars or inputs
 *
 * @param pathA  path to first execution log to compare
 * @param pathB  path to second execution log to compare with
 *
 * @return  list of merged spawn executions, total executions, cache hits and hit rate
 * @see [Protos.Report]
 */
fun mergeSpawnExecs(pathA: String, pathB: String): Protos.Report {
    var mergedSpawnExecs: MutableList<MergedSpawnExec> = arrayListOf()
    var bExecCounter: Int = 0
    var cacheHits: Int = 0
    val aSpawnExecs: HashMap<String, SpawnExec> = HashMap()
    val insFileA = File(pathA).inputStream()
    readExecutionLog(insFileA) { aSpawnExecs[it.first] = it.second }
    val aExecCounter = aSpawnExecs.size
    val insFileB = File(pathB).inputStream()
    readExecutionLog(insFileB) {
        bExecCounter++
        if (!it.second.remoteCacheHit) {
            val aEnvVars: Map<String, String> =
                aSpawnExecs[it.first]!!.environmentVariablesList.associate { envVar -> envVar.name to envVar.value }
            val bEnvVars: Map<String, String> =
                it.second.environmentVariablesList.associate { it.name to it.value }
            val mergedEnvVars =
                calculateDiff(aEnvVars, bEnvVars).map { entry ->
                    ExecutionEnvironmentVariables.newBuilder().setName(entry.key).setAValue(entry.value.first)
                        .setBValue(entry.value.second).build()
                }
            val aInputs = aSpawnExecs[it.first]!!.inputsList.associate { input -> input.path to input.digest }
            val bInputs = it.second.inputsList.associate { input -> input.path to input.digest }
            val mergedInputs = calculateDiff(aInputs, bInputs).map { entry ->
                ExecutionInputs.newBuilder().setPath(entry.key).setAHash(entry.value.first.hash)
                    .setBHash(entry.value.second.hash).build()
            }
            val mergedSpawnExec = MergedSpawnExec.newBuilder().setExecutionHash(it.first)
                .addAllListedOutputs(it.second.listedOutputsList)
                .addAllEnvVars(mergedEnvVars.toMutableList())
                .addAllInputs(mergedInputs.toMutableList())
                .build()
            mergedSpawnExecs.add(mergedSpawnExec)
        } else {
            cacheHits++
        }
    }
    return Protos.Report.newBuilder()
        .addAllMergedSpawnExecs(mergedSpawnExecs)
        .setCacheHits(cacheHits)
        .setTotalExecutions(aExecCounter)
        .setCacheHitRate(cacheHits.toFloat() / aExecCounter.toFloat() * 100)
        .build()
}

/**
 * Walks through binary execution log and executes
 * arbitrary post-processing function
 *
 * @param inputStream  inputStream of execution log file
 * @param processSpawnExec  arbitrary function to post-process returned pair of exec hash and SpawnExec
 */
fun readExecutionLog(inputStream: InputStream, processSpawnExec: (Pair<String, SpawnExec>) -> Unit) {
    inputStream.use { ins ->
        while (ins.available() > 0) {
            val spawnExec = SpawnExec.parseDelimitedFrom(ins)
            val execHash = calculateExecHash(spawnExec.listedOutputsList.toString())
            processSpawnExec(Pair(execHash, spawnExec))
        }
    }
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
