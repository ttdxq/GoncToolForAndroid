package cyou.ttdxq.gonctool.android

import cyou.ttdxq.gonctool.android.data.appendBoundedLog
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryOrderTest {
    @Test
    fun appendBoundedLogKeepsChronologicalOrder() {
        val logs = appendBoundedLog(listOf("first", "second"), "third", 3)

        assertEquals(listOf("first", "second", "third"), logs)
    }

    @Test
    fun appendBoundedLogDropsOldestEntryAtLimit() {
        val logs = appendBoundedLog(listOf("first", "second"), "third", 2)

        assertEquals(listOf("second", "third"), logs)
    }
}
