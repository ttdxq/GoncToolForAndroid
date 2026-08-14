package cyou.ttdxq.gonctool.android

import cyou.ttdxq.gonctool.android.util.formatElapsedDuration
import cyou.ttdxq.gonctool.android.util.formatTrafficBytes
import cyou.ttdxq.gonctool.android.util.formatTrafficRate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatterTest {
    @Test
    fun formatsRatesUsingBinaryUnits() {
        assertEquals("0 B/s", formatTrafficRate(Double.NaN, Locale.US))
        assertEquals("842 B/s", formatTrafficRate(842.0, Locale.US))
        assertEquals("1.5 KiB/s", formatTrafficRate(1536.0, Locale.US))
        assertEquals("2.4 MiB/s", formatTrafficRate(2.4 * 1024 * 1024, Locale.US))
    }

    @Test
    fun formatsAccumulatedBytesUsingBinaryUnits() {
        assertEquals("0 B", formatTrafficBytes(-1, Locale.US))
        assertEquals("1.5 KiB", formatTrafficBytes(1536, Locale.US))
        assertEquals("1.3 GiB", formatTrafficBytes((1.3 * 1024 * 1024 * 1024).toLong(), Locale.US))
    }

    @Test
    fun formatsElapsedDurationWithOptionalHours() {
        assertEquals("00:00", formatElapsedDuration(-1))
        assertEquals("18:42", formatElapsedDuration(18 * 60 + 42L))
        assertEquals("1:18:42", formatElapsedDuration(60 * 60 + 18 * 60 + 42L))
    }
}
