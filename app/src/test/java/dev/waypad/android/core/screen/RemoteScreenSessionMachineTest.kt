package dev.waypad.android.core.screen

import dev.waypad.android.core.model.RemoteScreenConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteScreenSessionMachineTest {
    @Test
    fun normalStreamLifecycleReachesStreamingThenClosed() {
        val machine = RemoteScreenSessionMachine()

        assertEquals(RemoteScreenConnectionState.Connecting, machine.transition(RemoteScreenSessionEvent.Start))
        assertEquals(RemoteScreenConnectionState.Negotiating, machine.transition(RemoteScreenSessionEvent.Negotiated))
        assertEquals(RemoteScreenConnectionState.Streaming, machine.transition(RemoteScreenSessionEvent.FirstFrame))
        assertEquals(RemoteScreenConnectionState.Closed, machine.transition(RemoteScreenSessionEvent.Close))
    }

    @Test
    fun failedStreamCanEnterReconnecting() {
        val machine = RemoteScreenSessionMachine(RemoteScreenConnectionState.Streaming)

        assertEquals(RemoteScreenConnectionState.Failed, machine.transition(RemoteScreenSessionEvent.Fail))
        assertEquals(RemoteScreenConnectionState.Reconnecting, machine.transition(RemoteScreenSessionEvent.Retry))
    }
}
