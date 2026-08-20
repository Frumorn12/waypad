package dev.waypad.android.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material 3 Expressive motion scheme, reimplemented on public APIs.
 *
 * material3 1.4.0 ships `MotionScheme` and `MaterialExpressiveTheme`, but both are `internal` in
 * that release, so they cannot be referenced from application code. The spring constants below are
 * the exact values of `androidx.compose.material3.tokens.ExpressiveMotionTokens`, which gives the
 * same feel through `androidx.compose.animation.core.spring`.
 *
 * *Spatial* springs move things (position, size, corner radius) and are deliberately slightly
 * under-damped so motion overshoots; *effects* springs change non-spatial properties (colour,
 * alpha) and are critically damped so they never overshoot.
 */
@Immutable
class WaypadMotion {
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = DEFAULT_SPATIAL_DAMPING, stiffness = DEFAULT_SPATIAL_STIFFNESS)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = FAST_SPATIAL_DAMPING, stiffness = FAST_SPATIAL_STIFFNESS)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SLOW_SPATIAL_DAMPING, stiffness = SLOW_SPATIAL_STIFFNESS)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = DEFAULT_EFFECTS_STIFFNESS)

    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = FAST_EFFECTS_STIFFNESS)

    fun <T> slowEffects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = SLOW_EFFECTS_STIFFNESS)

    private companion object {
        const val DEFAULT_SPATIAL_DAMPING = 0.8f
        const val DEFAULT_SPATIAL_STIFFNESS = 380f
        const val FAST_SPATIAL_DAMPING = 0.6f
        const val FAST_SPATIAL_STIFFNESS = 800f
        const val SLOW_SPATIAL_DAMPING = 0.8f
        const val SLOW_SPATIAL_STIFFNESS = 200f
        const val EFFECTS_DAMPING = 1.0f
        const val DEFAULT_EFFECTS_STIFFNESS = 1600f
        const val FAST_EFFECTS_STIFFNESS = 3800f
        const val SLOW_EFFECTS_STIFFNESS = 800f
    }
}

val LocalWaypadMotion = staticCompositionLocalOf { WaypadMotion() }
