package com.kudo.android

import com.kudo.android.network.DohNetworkState
import org.junit.Assert.*
import org.junit.Test

class DohNetworkPolicyTest {
    @Test fun defaultOffAndVpnOnlyPausesTheSavedIntent() {
        assertFalse(DohNetworkState().effective)
        val enabled = DohNetworkState(enabled = true)
        assertTrue(enabled.effective)
        val paused = enabled.copy(vpn = true)
        assertTrue(paused.enabled)
        assertFalse(paused.effective)
        assertTrue(paused.copy(vpn = false).effective)
        assertFalse(paused.copy(enabled = false, vpn = false).effective)
    }
    @Test fun systemProxyAlsoPausesAndErrorsNeverClaimEchAccepted() {
        assertFalse(DohNetworkState(enabled = true, proxy = true).effective)
        assertTrue(DohNetworkState(enabled = true, error = "失败").effective)
        assertEquals("失败", DohNetworkState(enabled = true, error = "失败").summary)
        assertEquals("DoH + ECH 已连接", DohNetworkState(enabled = true, echAccepted = true).summary)
    }
}
