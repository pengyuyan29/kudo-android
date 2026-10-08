package com.kudo.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kudo.android.ui.topic.GalleryTransformState
import com.kudo.android.ui.topic.fitGalleryImage
import org.junit.Assert.*
import org.junit.Test

class GalleryTransformTest {
    @Test fun pinchingKeepsTheImagePointUnderTheFingerCentroid() {
        val state = GalleryTransformState(1f)
        state.resize(Size(1000f, 1000f))
        state.transform(Offset(300f, 400f), Offset.Zero, 2f)
        assertEquals(2f, state.scale, 0.001f)
        assertEquals(200f, state.offset.x, 0.001f)
        assertEquals(100f, state.offset.y, 0.001f)
        state.transform(Offset(300f, 400f), Offset.Zero, 0.5f)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.zoomed)
    }

    @Test fun panCannotMoveTheImageOutsideItsRealContentBounds() {
        val portrait = GalleryTransformState(0.25f)
        portrait.resize(Size(1000f, 1000f))
        portrait.transform(Offset(500f, 500f), Offset(9000f, 9000f), 2f)
        assertEquals(0f, portrait.offset.x, 0.001f)
        assertEquals(500f, portrait.offset.y, 0.001f)
        val wide = GalleryTransformState(5f)
        wide.resize(Size(1000f, 1000f))
        wide.transform(Offset(500f, 500f), Offset(-9000f, -9000f), 2f)
        assertEquals(-500f, wide.offset.x, 0.001f)
        assertEquals(0f, wide.offset.y, 0.001f)
    }

    @Test fun fittingKeepsWideAndTallImagesUndistorted() {
        val wide = fitGalleryImage(Size(1000f, 2000f), 5f)
        assertEquals(1000f, wide.width, 0.001f)
        assertEquals(200f, wide.height, 0.001f)
        val tall = GalleryTransformState(0.05f)
        tall.resize(Size(1000f, 1000f))
        assertTrue("长图应允许放大到屏幕宽度", tall.maxScale >= 20f)
        assertEquals(50f, tall.fitBounds.width, 0.001f)
        assertEquals(1000f, tall.fitBounds.height, 0.001f)
    }

    @Test fun releasingTheLastFingerCannotIntroduceAnUnspecifiedOffset() {
        val state = GalleryTransformState(1f)
        state.resize(Size(1000f, 1000f))
        state.transform(Offset(500f, 500f), Offset.Zero, 2f)
        state.transform(Offset.Unspecified, Offset.Zero, 1f)
        assertEquals(Offset.Zero, state.offset)
        assertEquals(2f, state.scale, 0.001f)
    }
}
