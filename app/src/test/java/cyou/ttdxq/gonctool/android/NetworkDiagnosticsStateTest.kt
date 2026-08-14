package cyou.ttdxq.gonctool.android

import cyou.ttdxq.gonctool.android.data.DiagnosticsRunStatus
import cyou.ttdxq.gonctool.android.data.diagnosticsCompletionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkDiagnosticsStateTest {
    @Test
    fun completionStatusDistinguishesCompletePartialFailedAndCancelled() {
        assertEquals(DiagnosticsRunStatus.COMPLETE, diagnosticsCompletionStatus(false, 4, 4))
        assertEquals(DiagnosticsRunStatus.PARTIAL, diagnosticsCompletionStatus(false, 2, 4))
        assertEquals(DiagnosticsRunStatus.FAILED, diagnosticsCompletionStatus(false, 0, 4))
        assertEquals(DiagnosticsRunStatus.CANCELLED, diagnosticsCompletionStatus(true, 2, 4))
    }
}
