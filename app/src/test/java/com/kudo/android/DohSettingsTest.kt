package com.kudo.android

import com.kudo.android.data.*
import org.junit.Assert.*
import org.junit.Test

class DohSettingsTest {
    @Test fun defaultsOffAndRepairsCorruptOrDanglingSelection() {
        assertFalse(decodeDohConfiguration(null).enabled)
        assertEquals(DohConfiguration(), decodeDohConfiguration("invalid"))
        assertEquals(BuiltInDohServer, decodeDohConfiguration("""{"version":1,"enabled":true,"selectedId":"missing"}""").selected)
    }
    @Test fun customServersRoundTripEditSelectAndDelete() {
        val server = validateDohServer("测试", "https://dns.example/dns-query?key=private", "1.1.1.1,2606:4700:4700::1111")
        val value = DohConfiguration(enabled = true).saveServer(server).select(server.id)
        assertEquals(value, decodeDohConfiguration(encodeDohConfiguration(value)))
        val edited = value.saveServer(server.copy(name = "修改", url = "https://dns.example/new"))
        assertEquals("修改", edited.selected.name)
        assertEquals(1, edited.customServers.size)
        assertEquals(BuiltInDohServer, edited.removeServer(server.id).selected)
        assertTrue(edited.removeServer(server.id).enabled)
    }
    @Test fun rejectsInsecureCredentialsFragmentsAndNonIpBootstrap() {
        for (url in listOf("http://dns.example/", "https://user:password@dns.example/", "https://dns.example/#fragment", "garbage")) {
            assertTrue(url, runCatching { validateDohServer("测试", url) }.isFailure)
        }
        for (ip in listOf("dns.example", "256.1.1.1", "01.1.1.1", "fe80::1%wlan0", "[::1]")) {
            assertTrue(ip, runCatching { validateDohServer("测试", "https://dns.example/", ip) }.isFailure)
        }
        assertTrue(runCatching { DohConfiguration().removeServer(BuiltInDohServer.id) }.isFailure)
        assertTrue(runCatching { DohConfiguration().saveServer(BuiltInDohServer) }.isFailure)
    }
    @Test fun invalidSavedServerCannotOverrideBuiltInOrRemainSelected() {
        val raw = """{"version":1,"enabled":false,"selectedId":"bad","servers":[{"id":"bad","name":"bad","url":"http://example/"},{"id":"linuxdo-community","name":"override","url":"https://evil.example/"}]}"""
        assertEquals(DohConfiguration(), decodeDohConfiguration(raw))
    }
}
