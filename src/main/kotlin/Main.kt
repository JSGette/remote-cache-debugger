import com.google.devtools.build.lib.exec.Protos.SpawnExec
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

val sha256 = MessageDigest.getInstance("SHA-256")

fun main(args: Array<String>) {
    mergeSpawnExecs("D:/exec2.log", "D:/exec3.log")
}

fun mergeSpawnExecs(pathA: String, pathB: String) {
    val mergedSpawnExecs: HashMap<String, MergedSpawnExec> = HashMap()
    var ins = File(pathA).inputStream()

    while (ins.available() > 0) {
        val spawnExec = getNextSpawnExec(ins)
        val mergedSpawnExec: MergedSpawnExec =
            MergedSpawnExec(
                spawnExec.second.listedOutputsList,
                spawnExec.second.environmentVariablesList.associate { it.name to it.value },
                spawnExec.second.inputsList.associate { it.path to it.digest }
            )
        mergedSpawnExecs[spawnExec.first] = mergedSpawnExec
    }

    ins = File(pathB).inputStream()
    while (ins.available() > 0) {
        val spawnExec = getNextSpawnExec(ins)
        if (mergedSpawnExecs.contains(spawnExec.first)) {
            mergedSpawnExecs[spawnExec.first]!!.presentInBothExecs = true
            mergedSpawnExecs[spawnExec.first]!!.bEnvVars =
                spawnExec.second.environmentVariablesList.associate { it.name to it.value }
            mergedSpawnExecs[spawnExec.first]!!.bInputs = spawnExec.second.inputsList.associate { it.path to it.digest }
        } else {
            mergedSpawnExecs[spawnExec.first] = MergedSpawnExec(
                spawnExec.second.listedOutputsList,
                HashMap(),
                HashMap(),
                spawnExec.second.environmentVariablesList.associate { it.name to it.value },
                spawnExec.second.inputsList.associate { it.path to it.digest },
                false
            )
        }
    }

    mergedSpawnExecs.forEach {
        println("=============================")
        println(it.value.listedOutputs)
        it.value.calculateDiffEnv().forEach {
            println("ENVVAR: ${it.key}")
            println("FIRST RUN: ${it.value.first}")
            println("SECOND RUN: ${it.value.second}")
        }
        println(it.value.calculateDiffInputs().size)
        it.value.calculateDiffInputs().forEach {
            println(it.value.first)
            println(it.value.second)
        }
    }
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
