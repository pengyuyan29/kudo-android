package com.kudo.android

import com.kudo.android.data.TopicReadingProgress
import org.junit.Assert.*
import org.junit.Test

class TopicReadingProgressTest {
    @Test fun onlyVisibleFloorsAccumulateAndBriefFlashesDoNotFlush() {
        val progress = TopicReadingProgress(12)
        progress.visible(setOf(8), 0)
        progress.tick(500)
        assertNull(progress.drain())
        progress.visible(emptySet(), 500)
        progress.tick(10_000)
        assertNull(progress.drain())
        progress.visible(setOf(8, 11), 10_000)
        progress.tick(11_000)
        val batch = progress.drain()!!
        assertEquals(12, batch.topicId)
        assertEquals(mapOf(8 to 1500L, 11 to 1000L), batch.timings)
        assertEquals(1500L, batch.topicTime)
        assertNull(progress.drain())
    }

    @Test fun pauseStopsAccumulationAndFlushesOnlyTheActualDeepLinkFloor() {
        val progress = TopicReadingProgress(12)
        progress.visible(setOf(38), 0)
        progress.tick(1000)
        progress.visible(emptySet(), 1500)
        progress.tick(120_000)
        assertEquals(mapOf(38 to 1500L), progress.drain()!!.timings)
        assertNull(progress.drain())
    }

    @Test fun idleAndStalledFramesDoNotManufactureReadingTime() {
        val progress = TopicReadingProgress(1)
        progress.visible(setOf(1), 0)
        for (time in 1000L..190_000L step 1000) progress.tick(time)
        assertEquals(180_000L, progress.drain()!!.timings[1])
        progress.visible(setOf(1), 200_000)
        progress.tick(240_000)
        assertEquals(1500L, progress.drain()!!.timings[1])
    }

    @Test fun aFloorHasASixMinuteSessionLimitAndEmptyExposureNeverWrites() {
        val progress = TopicReadingProgress(1)
        progress.visible(emptySet(), 0)
        progress.tick(1000)
        assertNull(progress.drain())
        progress.visible(setOf(0, -1, 3), 1000)
        for (time in 2000L..500_000L step 1000) { progress.visible(setOf(3), time); progress.tick(time) }
        assertEquals(mapOf(3 to 360_000L), progress.drain()!!.timings)
    }
}
