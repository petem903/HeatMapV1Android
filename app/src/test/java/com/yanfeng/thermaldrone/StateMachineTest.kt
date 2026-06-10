package com.yanfeng.thermaldrone

import com.yanfeng.thermaldrone.model.ConnectionState
import com.yanfeng.thermaldrone.model.StateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {

    @Test
    fun `same-state transition is always allowed - idempotent`() {
        ConnectionState.entries.forEach { s ->
            assertTrue("$s -> $s", StateMachine.canTransition(s, s))
        }
    }

    @Test
    fun `happy path lifecycle`() {
        assertTrue(StateMachine.canTransition(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING))
        assertTrue(StateMachine.canTransition(ConnectionState.CONNECTING, ConnectionState.CONNECTED))
        assertTrue(StateMachine.canTransition(ConnectionState.CONNECTED, ConnectionState.ARMED))
        assertTrue(StateMachine.canTransition(ConnectionState.ARMED, ConnectionState.FLYING))
        assertTrue(StateMachine.canTransition(ConnectionState.FLYING, ConnectionState.LANDING))
        assertTrue(StateMachine.canTransition(ConnectionState.LANDING, ConnectionState.DISCONNECTED))
    }

    @Test
    fun `illegal jumps rejected`() {
        assertFalse(StateMachine.canTransition(ConnectionState.DISCONNECTED, ConnectionState.FLYING))
        assertFalse(StateMachine.canTransition(ConnectionState.DISCONNECTED, ConnectionState.ARMED))
        assertFalse(StateMachine.canTransition(ConnectionState.CONNECTING, ConnectionState.FLYING))
        assertFalse(StateMachine.canTransition(ConnectionState.CONNECTED, ConnectionState.FLYING))
        assertFalse(StateMachine.canTransition(ConnectionState.LANDING, ConnectionState.FLYING))
    }

    @Test
    fun `usb pull from any state can reach DISCONNECTED`() {
        listOf(
            ConnectionState.CONNECTING, ConnectionState.CONNECTED,
            ConnectionState.ARMED, ConnectionState.FLYING, ConnectionState.LANDING
        ).forEach { s ->
            assertTrue("$s -> DISCONNECTED", StateMachine.canTransition(s, ConnectionState.DISCONNECTED))
        }
    }
}
