package dev.waypad.android.core.screen

import dev.waypad.android.core.model.RemoteScreenConnectionState

class RemoteScreenSessionMachine(
    initial: RemoteScreenConnectionState = RemoteScreenConnectionState.Idle,
) {
    var state: RemoteScreenConnectionState = initial
        private set

    fun transition(event: RemoteScreenSessionEvent): RemoteScreenConnectionState {
        state = when (event) {
            RemoteScreenSessionEvent.Start ->
                RemoteScreenConnectionState.Connecting
            RemoteScreenSessionEvent.Negotiated ->
                if (state == RemoteScreenConnectionState.Connecting ||
                    state == RemoteScreenConnectionState.Reconnecting
                ) {
                    RemoteScreenConnectionState.Negotiating
                } else {
                    state
                }
            RemoteScreenSessionEvent.FirstFrame ->
                if (state == RemoteScreenConnectionState.Negotiating ||
                    state == RemoteScreenConnectionState.Connecting ||
                    state == RemoteScreenConnectionState.Reconnecting
                ) {
                    RemoteScreenConnectionState.Streaming
                } else {
                    state
                }
            RemoteScreenSessionEvent.Retry ->
                if (state == RemoteScreenConnectionState.Streaming ||
                    state == RemoteScreenConnectionState.Negotiating ||
                    state == RemoteScreenConnectionState.Failed
                ) {
                    RemoteScreenConnectionState.Reconnecting
                } else {
                    state
                }
            RemoteScreenSessionEvent.Fail ->
                RemoteScreenConnectionState.Failed
            RemoteScreenSessionEvent.Close ->
                RemoteScreenConnectionState.Closed
        }
        return state
    }
}

enum class RemoteScreenSessionEvent {
    Start,
    Negotiated,
    FirstFrame,
    Retry,
    Fail,
    Close,
}
