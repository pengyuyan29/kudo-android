package com.kudo.android

import com.kudo.android.ui.LinuxDoDestination.*
import com.kudo.android.ui.LinuxDoLink
import org.junit.Assert.*
import org.junit.Test

class LinuxDoLinkTest {
    @Test fun topicsAcceptSlugShortPathAndFloor() {
        assertEquals(Topic(3001000), LinuxDoLink.parse("https://linux.do/t/topic/3001000"))
        assertEquals(Topic(3001000, 8), LinuxDoLink.parse("https://linux.do/t/topic/3001000/8"))
        assertEquals(Topic(3001000, 8), LinuxDoLink.parse("https://linux.do/t/3001000/8/?foo=1#post_8"))
        assertEquals(Topic(3001000), LinuxDoLink.parse(" https://linux.do/t/3001000/ "))
    }

    @Test fun userSubpathsAlwaysOpenTheProfile() {
        listOf("", "/", "/summary/", "/activity/replies/").forEach {
            assertEquals(User("stingysheep"), LinuxDoLink.parse("https://linux.do/u/stingysheep$it"))
        }
        assertEquals(User("中文用户"), LinuxDoLink.parse("https://linux.do/u/%E4%B8%AD%E6%96%87%E7%94%A8%E6%88%B7/summary"))
    }

    @Test fun otherSitePathsAndMalformedTargetsGoHome() {
        listOf("/", "/latest", "/c/4", "/u/", "/t/topic/0", "/t/topic/999999999999",
            "/t/topic/12/0", "/t/topic/12/nope", "/t/topic/12/8/extra").forEach {
            assertEquals(it, Home, LinuxDoLink.parse("https://linux.do$it"))
        }
        assertEquals(Home, LinuxDoLink.parse("http://linux.do/latest"))
    }

    @Test fun unrelatedOriginsAndEncodedSlashesAreNotRoutedAsProfiles() {
        listOf("https://linux.do.evil.test/t/topic/1", "https://example.com/u/test", "javascript:alert(1)",
            "https://user@linux.do/t/1", "https://linux.do:444/t/1").forEach { assertNull(LinuxDoLink.parse(it)) }
        assertEquals(Home, LinuxDoLink.parse("https://linux.do/u/bad%2Fname"))
    }
}
