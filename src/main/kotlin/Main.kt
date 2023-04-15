import com.gette.debugger.Protos.ExecutionEnvironmentVariables
import com.gette.debugger.Protos.ExecutionInputs
import com.gette.debugger.Protos.MergedSpawnExec
import com.gette.debugger.Protos.Report
import com.google.devtools.build.lib.exec.Protos.SpawnExec
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")

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
    val report = generateReport(first_exec_log, second_exec_log)
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
 * @see [Report]
 */
fun generateReport(pathA: String, pathB: String): Report {
    val aSpawnExecs = FileInputStream(pathA).use { ins ->
        readExecutionLog(ins).toMap()
    }
    return FileInputStream(pathB).use { pathBStream ->
        val bSpawnExecSequence = readExecutionLog(pathBStream)
        generateReport(aSpawnExecs, bSpawnExecSequence)
    }
}

/**
 * Creates a sequence of [SpawnExec] from the [InputStream]
 *
 * @param ins  inputStream of execution log file
 */
fun readExecutionLog(ins: InputStream): Sequence<Pair<String, SpawnExec>> = generateSequence {
    if (ins.available() > 0) {
        val spawnExec = SpawnExec.parseDelimitedFrom(ins)
        val execHash = calculateExecHash(spawnExec.listedOutputsList.toString())
        execHash to spawnExec
    } else {
        null
    }
}

fun generateReport(aSpawnExecs: Map<String, SpawnExec>, bSpawnExecSequence: Sequence<Pair<String, SpawnExec>>): Report {
    val mergedSpawnExecs: MutableList<MergedSpawnExec> = arrayListOf()
    var cacheHits = 0

    bSpawnExecSequence.forEach { (bHash, bExec) ->
        if (bExec.remoteCacheHit) {
            cacheHits++
            return@forEach
        }
        val aExec = aSpawnExecs[bHash] ?: error("aSpawnExecs does not have the exec with hash '$bHash'")
        val aEnvVars: Map<String, String> = aExec.associateEnvVars()
        val bEnvVars: Map<String, String> = bExec.associateEnvVars()
        val mergedEnvVars = calculateDiff(aEnvVars, bEnvVars).map { (key, value) ->
            ExecutionEnvironmentVariables.newBuilder()
                .setName(key)
                .setAValue(value.first)
                .setBValue(value.second)
                .build()
        }
        val aInputs = aExec.associateInputs()
        val bInputs = bExec.associateInputs()
        val mergedInputs = calculateDiff(aInputs, bInputs).map { (key, value) ->
            ExecutionInputs.newBuilder()
                .setPath(key)
                .setAHash(value.first.hash)
                .setBHash(value.second.hash)
                .build()
        }
        val mergedSpawnExec = MergedSpawnExec.newBuilder()
            .setExecutionHash(bHash)
            .addAllListedOutputs(bExec.listedOutputsList)
            .addAllEnvVars(mergedEnvVars)
            .addAllInputs(mergedInputs)
            .build()
        mergedSpawnExecs.add(mergedSpawnExec)
    }

    return Report.newBuilder()
        .addAllMergedSpawnExecs(mergedSpawnExecs)
        .setCacheHits(cacheHits)
        .setTotalExecutions(aSpawnExecs.size)
        .setCacheHitRate(cacheHits.toFloat() / aSpawnExecs.size.toFloat() * 100)
        .build()
}

fun SpawnExec.associateEnvVars() = environmentVariablesList.associate { it.name to it.value }

fun SpawnExec.associateInputs() = inputsList.associate { it.path to it.digest }

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
    return aMap.filterKeys { bMap.containsKey(it) }
        .filter { (key, _) -> aMap[key] != bMap[key] }
        .mapValues { Pair(aMap[it.key]!!, bMap[it.key]!!) }
}
