import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MainTest {

    @Test
    fun `Calculated SHA-256 hash is correct`() {
        val testString = "testStringToCalculateSHA256Hash"
        val expectedHash = "9794754e527299b8d3a53bdc825f63821da5841a6f7adfdac16570435231879e"
        val calculatedHash = calculateExecHash(testString)
        assertEquals(expectedHash, calculatedHash)
    }

    @Test
    fun `calculateDiff returns matched keys with different values`() {

    }

    @Test
    fun `calculateDiff returns orphaned keys present in aMap but not in bMap`() {

    }

    @Test
    fun `calculateDiff returns orphaned keys present in bMap but not in aMap`() {

    }

    @Test
    fun `mergeSpawnExecs correctly merges 2 lists of SpawnExecs`() {
        //produce output binary log, parse it and check the correctness
    }
}
