package com.iospring.launcher.core

import android.view.Choreographer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Pure-Kotlin iOS CASpringAnimation / SwiftUI spring physics engine.
 * Parameterized by Apple's (response, dampingFraction) model for authentic
 * 60/90/120Hz OPPO LTPO display smoothness without extra library overhead.
 */
data class SpringConfig(
    val response: Float,
    val dampingFraction: Float
) {
    val stiffness: Float
        get() {
            val omega = (2.0 * PI / response.coerceAtLeast(0.05f)).toFloat()
            return omega * omega
        }

    val damping: Float
        get() {
            val omega = (2.0 * PI / response.coerceAtLeast(0.05f)).toFloat()
            return 2f * dampingFraction.coerceAtLeast(0.1f) * omega
        }

    companion object {
        val PAGE_SNAP = SpringConfig(response = 0.34f, dampingFraction = 0.88f)
        val FOLDER_OPEN = SpringConfig(response = 0.30f, dampingFraction = 0.84f)
        val ICON_PRESS = SpringConfig(response = 0.20f, dampingFraction = 0.82f)
        val SPOTLIGHT = SpringConfig(response = 0.28f, dampingFraction = 0.92f)
        val SNAPPY = SpringConfig(response = 0.24f, dampingFraction = 0.86f)
    }
}

class SpringState(
    var value: Float = 0f,
    var velocity: Float = 0f
) {
    /**
     * Advances the damped harmonic oscillator by [dtSeconds] toward [target].
     * Uses fixed 4ms sub-steps to guarantee stability across variable refresh rates (60/90/120Hz).
     * @return true if the spring has settled within epsilon.
     */
    fun step(
        target: Float,
        dtSeconds: Float,
        config: SpringConfig,
        positionEpsilon: Float = 0.25f,
        velocityEpsilon: Float = 0.5f
    ): Boolean {
        if (!target.isFinite()) {
            velocity = 0f
            return true
        }
        if (!value.isFinite()) value = target
        if (!velocity.isFinite()) velocity = 0f

        val safeDt = if (dtSeconds.isFinite()) dtSeconds else 1f / 120f
        val clampedDt = safeDt.coerceIn(0.001f, 0.05f)
        val stiffness = config.stiffness
        val damping = config.damping

        var remaining = clampedDt
        val maxSubStep = 0.004f
        while (remaining > 0f) {
            val dt = min(remaining, maxSubStep)
            val displacement = value - target
            val force = -stiffness * displacement - damping * velocity
            velocity += force * dt
            value += velocity * dt
            remaining -= dt
        }

        if (!value.isFinite() || !velocity.isFinite()) {
            value = target
            velocity = 0f
            return true
        }

        val settled = abs(value - target) <= positionEpsilon && abs(velocity) <= velocityEpsilon
        if (settled) {
            value = target
            velocity = 0f
        }
        return settled
    }
}

object SpringMath {
    /**
     * Authentic UIScrollView rubber-band formula for overscroll resistance.
     */
    fun rubberBand(offset: Float, dimension: Float, coefficient: Float = 0.55f): Float {
        if (dimension <= 0f || offset == 0f) return 0f
        val absOffset = abs(offset)
        val result = (1f - (1f / ((absOffset * coefficient / dimension) + 1f))) * dimension
        return result * sign(offset)
    }

    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction.coerceIn(0f, 1f)
    }
}

/**
 * Choreographer-driven 1D spring animator with zero allocations per frame.
 */
class ChoreographerSpring(
    private val config: SpringConfig = SpringConfig.PAGE_SNAP,
    private val positionEpsilon: Float = 0.5f,
    private val velocityEpsilon: Float = 1.5f,
    private val onUpdate: (value: Float) -> Unit,
    private val onEnd: (() -> Unit)? = null
) : Choreographer.FrameCallback {

    private val state = SpringState()
    private var target: Float = 0f
    private var running: Boolean = false
    private var lastFrameNanos: Long = 0L

    val currentValue: Float get() = state.value
    val isRunning: Boolean get() = running

    fun setCurrentValue(value: Float) {
        if (!value.isFinite()) return
        state.value = value
        state.velocity = 0f
        target = value
    }

    fun snapTo(value: Float) {
        if (!value.isFinite()) return
        cancel()
        state.value = value
        state.velocity = 0f
        target = value
        onUpdate(value)
    }

    fun animateTo(newTarget: Float, initialVelocity: Float? = null) {
        if (!newTarget.isFinite()) return
        target = newTarget
        if (initialVelocity != null && initialVelocity.isFinite()) {
            state.velocity = initialVelocity
        } else if (!state.velocity.isFinite()) {
            state.velocity = 0f
        }
        if (!state.value.isFinite()) {
            state.value = newTarget
        }
        if (!running) {
            running = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun cancel() {
        if (running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dtSeconds = if (lastFrameNanos == 0L) {
            1f / 120f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat()
        }
        lastFrameNanos = frameTimeNanos

        val settled = state.step(
            target = target,
            dtSeconds = dtSeconds,
            config = config,
            positionEpsilon = positionEpsilon,
            velocityEpsilon = velocityEpsilon
        )
        onUpdate(state.value)

        if (settled) {
            running = false
            onEnd?.invoke()
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
