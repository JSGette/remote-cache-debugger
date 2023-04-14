import com.gette.debugger.Protos.MergedSpawnExec

data class Report(
    val mergedSpawnExecs: List<MergedSpawnExec>,
    val totalExecutions: Int,
    val cacheHits: Int
) {
    private val cacheHitRate: Float
        get() = cacheHits.toFloat() / totalExecutions.toFloat() * 100

    fun printReport() {
        val reportText = """====================REPORT====================
Spawned Executions: ${totalExecutions}
Cache Hits: ${cacheHits}
Cache Hit Rate: ${
            "%.2f".format(cacheHitRate)
        }%
==============================================
    """.trimIndent()

        println(reportText)
    }

    fun printDiff() {
        mergedSpawnExecs.forEach { println(it.toString()) }
    }
}
