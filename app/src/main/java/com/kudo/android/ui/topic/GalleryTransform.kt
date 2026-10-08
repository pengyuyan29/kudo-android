package com.kudo.android.ui.topic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun fitGalleryImage(viewport: Size, ratio: Float?): Rect {
    if (viewport.isEmpty()) return Rect.Zero
    val aspect = ratio?.takeIf { it.isFinite() && it > 0 } ?: (viewport.width / viewport.height)
    val width = min(viewport.width, viewport.height * aspect)
    val height = width / aspect
    return Rect(Offset((viewport.width - width) / 2, (viewport.height - height) / 2), Size(width, height))
}

@Stable
internal class GalleryTransformState(initialRatio: Float? = null) {
    var viewport by mutableStateOf(Size.Zero)
        private set
    var ratio by mutableStateOf(initialRatio)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var dismissOffset by mutableStateOf(Offset.Zero)
        internal set
    var dragging by mutableStateOf(false)
        internal set
    val fitBounds get() = fitGalleryImage(viewport, ratio)
    val zoomed get() = scale > 1.001f
    val maxScale get() = if (fitBounds.width > 0) (viewport.width / fitBounds.width * 2).coerceIn(5f, 20f) else 5f
    val dismissFraction get() = if (viewport.height > 0) (dismissOffset.y / viewport.height).coerceIn(0f, 1f) else 0f
    val dismissScale get() = 1f - dismissFraction * 0.45f
    val scrimAlpha get() = (1f - dismissFraction * 1.4f).coerceIn(0f, 1f)

    fun resize(size: Size) { viewport = size; offset = constrained(offset, scale) }
    fun decodedRatio(value: Float) {
        if (value.isFinite() && value > 0) { ratio = value; offset = constrained(offset, scale) }
    }

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        if (viewport.isEmpty() || !zoom.isFinite() || zoom <= 0 ||
            !centroid.x.isFinite() || !centroid.y.isFinite() || !pan.x.isFinite() || !pan.y.isFinite()) return
        val next = (scale * zoom).coerceIn(1f, maxScale)
        val pivot = centroid - Offset(viewport.width / 2, viewport.height / 2)
        offset = constrained((offset - pivot) * (next / scale) + pivot + pan, next)
        scale = next
        if (!zoomed) { scale = 1f; offset = Offset.Zero }
    }

    private fun constrained(value: Offset, zoom: Float): Offset {
        val image = fitBounds
        val horizontal = max(0f, (image.width * zoom - viewport.width) / 2)
        val vertical = max(0f, (image.height * zoom - viewport.height) / 2)
        return Offset(value.x.coerceIn(-horizontal, horizontal), value.y.coerceIn(-vertical, vertical))
    }

    fun resetZoom() { scale = 1f; offset = Offset.Zero }
    fun accessibleZoom() { transform(Offset(viewport.width / 2, viewport.height / 2), Offset.Zero, 2f) }
    fun dragBy(delta: Offset) { dismissOffset = (dismissOffset + delta).let { Offset(it.x, max(0f, it.y)) } }
    fun shouldDismiss(velocityY: Float, flingVelocity: Float): Boolean =
        dismissFraction >= 0.18f || (dismissFraction >= 0.04f && velocityY >= flingVelocity)

    suspend fun restoreDismiss() {
        Animatable(dismissOffset, Offset.VectorConverter).animateTo(Offset.Zero, spring(dampingRatio = 0.9f, stiffness = 500f)) {
            dismissOffset = value
        }
    }

    fun displayedBounds(): Rect {
        val image = fitBounds
        val center = Offset(viewport.width / 2, viewport.height / 2)
        val zoom = scale * dismissScale
        return Rect((image.topLeft - center) * zoom + center + offset + dismissOffset,
            (image.bottomRight - center) * zoom + center + offset + dismissOffset)
    }
}

private enum class GalleryGesture { UNDECIDED, PAGER, TRANSFORM, DISMISS, IGNORE }

internal fun Modifier.galleryGestures(
    state: GalleryTransformState, enabled: Boolean, flingVelocity: Float,
    start: () -> Unit, finish: (Boolean) -> Unit,
): Modifier = pointerInput(state, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        start()
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var mode = GalleryGesture.UNDECIDED
        var total = Offset.Zero
        var completed = false
        try {
            do {
                val event = awaitPointerEvent()
                val pointers = event.changes.count { it.pressed }
                event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                if (pointers >= 2) {
                    mode = GalleryGesture.TRANSFORM
                    state.dismissOffset = Offset.Zero
                    state.dragging = false
                }
                val pan = event.calculatePan()
                if (mode == GalleryGesture.UNDECIDED) {
                    if (event.changes.any { it.isConsumed && it.positionChanged() }) mode = GalleryGesture.IGNORE
                    else {
                        total += pan
                        if (total.getDistance() > viewConfiguration.touchSlop) mode = when {
                            state.zoomed -> GalleryGesture.TRANSFORM
                            abs(total.x) > abs(total.y) -> GalleryGesture.PAGER
                            total.y > 0 -> GalleryGesture.DISMISS
                            else -> GalleryGesture.IGNORE
                        }
                    }
                }
                when (mode) {
                    GalleryGesture.TRANSFORM -> {
                        state.transform(event.calculateCentroid(useCurrent = false), pan, event.calculateZoom())
                        event.changes.forEach { it.consume() }
                    }
                    GalleryGesture.DISMISS -> {
                        state.dragging = true
                        state.dragBy(pan)
                        event.changes.forEach { it.consume() }
                    }
                    GalleryGesture.IGNORE -> if (abs(total.y) > viewConfiguration.touchSlop) event.changes.forEach { it.consume() }
                    else -> Unit
                }
            } while (event.changes.any { it.pressed })
            completed = true
            if (mode == GalleryGesture.DISMISS) finish(state.shouldDismiss(tracker.calculateVelocity().y, flingVelocity))
        } finally {
            if (!completed && mode == GalleryGesture.DISMISS) finish(false)
            state.dragging = false
        }
    }
}
