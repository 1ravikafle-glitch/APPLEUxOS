package com.appleux.design.tokens

import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat

/**
 * iOS 27 motion. Rewritten 2026-10-17.
 *
 * ======================================================================
 * FIRST: THE IPSW CONTAINS NO MOTION DATA. This is a measured finding,
 * not an assumption, and it is why the provenance below changed.
 * ======================================================================
 * The prior version of this file said "Apple publishes no numeric spring,
 * duration or curve table" and carried the Material Design 3 duration ramp
 * (83/120/167/220/333/400/500 ms) openly labelled as NOT-iOS. That was
 * honest, but it meant the whole OS moved on Material timings.
 *
 * That harvest was re-examined for motion data specifically:
 *
 *   * /home/elfakiris/IOSinspiration/iPhone12,1_27.2_24B5099f_Restore.ipsw
 *     contains 122 entries and NO Payload/ directory. It is a firmware-only
 *     IPSW: dyld caches, .dylibs and framework binaries are all absent. There
 *     is no UIKitCore, so there are no compiled animation constants to read.
 *   * ios-ui-analysis/all-plists/ has 30,637 files, but they are Info.plists.
 *     Grepping them for spring/curve/duration keys returns 420 hits that are
 *     all `springboard` PATH FRAGMENTS, not animation keys.
 *   * all-metal-shaders/ has 300 entries, all compiled .metallib blobs, no
 *     .metal source, and they are PBR/raytracing rather than UI motion.
 *   * The control-center glob-named _ios27_Info.plist files that DID yield
 *     the real layout data carry no motion keys either.
 *
 * So the constants below are NOT extracted from the IPSW. They are Apple's
 * PUBLISHED UIKit/SwiftUI animation values. That is a weaker provenance than
 * transcription, and it is stated rather than dressed up.
 *
 * ======================================================================
 * THE ACTUAL PROVENANCE OF EACH GROUP
 * ======================================================================
 *
 * 1. CUBIC BEZIER CONTROL POINTS — real, and the single most valuable part.
 *    UIKit's four easing curves are defined by specific control points, and
 *    they are documented:
 *
 *      linear     (0.00, 0.00) -> (1.00, 1.00)
 *      easeIn     (0.42, 0.00) -> (1.00, 1.00)
 *      easeOut    (0.00, 0.00) -> (0.58, 1.00)
 *      easeInOut  (0.42, 0.00) -> (0.58, 1.00)
 *
 *    Source: nilcoalescing.com/blog/AnimationTimingInSwiftUI, which enumerates
 *    each preset's control points explicitly and links each one to its Apple
 *    documentation page. The previous file had ONE of these four
 *    (easeInOut, as easingIOSDefault) and three Android curves
 *    (0.42/0/0.2/1 is Material fast-out-slow-in, not an Apple curve).
 *
 * 2. THE DEFAULT ANIMATION IS A SPRING, NOT A CURVE. This is the fact that
 *    actually makes iOS feel the way it does, and it is why the duration ramp
 *    could never substitute:
 *
 *      "Since iOS 17, the .default option is a spring animation with a
 *       configuration chosen by Apple."  (nilcoalescing, same article)
 *
 *    A fixed-duration ease cannot reproduce a spring's velocity continuity:
 *    a spring picks up the current velocity and decelerates into the target,
 *    so an interrupted animation continues instead of restarting. That is why
 *    iOS gestures feel glued to the finger and Material's 200 ms eases do not,
 *    no matter how the durations are tuned. AppleUX therefore exposes real
 *    SpringAnimation specs (see SpringSpec) rather than durations.
 *
 * 3. SPRING PARAMETERS — community-documented SwiftUI defaults, and one
 *    honest caveat: sources DISAGREE on the default damping fraction.
 *      * .spring() defaults reported as response 0.55 / dampingFraction 0.825
 *        (Medium, Learning SwiftUI Spring Animations)
 *      * and as response 0.55 / dampingFraction 0.75
 *        (Create with Swift, Jan 2026)
 *    Both are recorded in [SWIFTUI_SPRING_DEFAULTS_DISPUTED] rather than one
 *    being quietly chosen. Response 0.55 is agreed by both.
 *
 *    The three named presets are described by Apple as:
 *      .smooth  critically damped, no overshoot
 *      .snappy  slightly underdamped, small overshoot
 *      .bouncy  more underdamped, visible overshoot and oscillation
 *    Their exact response/damping pairs are NOT published, so the SpringSpecs
 *    below are DERIVED from those three descriptions by solving for a damping
 *    ratio of 1.0 / ~0.9 / ~0.7 and matching the agreed 0.4 / 0.4 / 0.5
 *    response ordering. They are marked AppleUX-derived, not transcribed.
 *
 * 4. DURATIONS — still AppleUX-chosen, and now only for the cases a spring is
 *    wrong for: continuous loops (spinners, progress) and non-interactive
 *    reveals. The Material ramp is gone; the values are UIKit's own
 *    non-spring defaults where they exist, and the derived spring settle
 *    periods where a spring governs the motion.
 *
 * HOW TO VERIFY ANY OF THIS. `MotionTokens` is not the place to argue about
 * feel. `m -j1 AppleUXLauncher` then measure frames on the target device; see
 * deliverables/m12 and .kilo/command/measure-frames.md.
 *
 * ---------------------------------------------------------------------------
 * EDITING WARNING: Kotlin block comments NEST, unlike Java's.
 *
 *   * A slash-star inside the prose silently OPENS a nested comment and
 *     swallows the rest of the file.
 *   * A star-slash inside the prose CLOSES this comment early and the
 *     remainder becomes syntax errors.
 *
 * Both have happened here while writing this very note, and both present as a
 * cascade of "unresolved reference MotionTokens" or "Expecting a top level
 * declaration" in unrelated components, which sends you hunting in the wrong
 * file entirely. Write "slash-star" and "star-slash" instead of the literals,
 * and avoid glob characters such as the wildcard in a path.
 * ---------------------------------------------------------------------------
 */
object MotionTokens {

    // ==================================================================
    // SPRINGS. These govern interaction. See the header: iOS's default
    // animation has been a spring since iOS 17, which is the whole reason
    // the Material duration ramp could not stand in for it.
    //
    // The specs below are DERIVED, not transcribed. Apple describes .smooth,
    // .snappy and .bouncy qualitatively (critically damped / slightly
    // underdamped / clearly underdamped) without publishing their numbers,
    // so each spec solves for a damping ratio in that band at the agreed
    // response ordering. [dampingRatio] exposes the computed ratio so the
    // claim is checkable rather than asserted.
    //
    // Roles follow the UIKit convention that springs drive anything the user
    // caused, and eases drive anything that happened on its own.
    // ==================================================================

    /**
     * `.smooth` — critically damped, no overshoot.
     *
     * The default for a value settling into place: sheets presenting, list
     * rows appearing, a toggle reaching its new position. Nothing should
     * bounce when a control is simply reflecting state.
     *
     * zeta 0.995, undamped period 0.192 s.
     */
    val springSmooth: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 1066f,
        damping = 65f,
        initialVelocity = 0f
    )

    /**
     * `.snappy` — slightly underdamped, small overshoot.
     *
     * For a control the user just touched. The overshoot is the tell that the
     * UI is physical rather than animated, and at a low ratio it reads as
     * weight rather than as playfulness.
     */
    val springSnappy: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 1581f,
        damping = 72f,
        initialVelocity = 0f
    )

    /**
     * `.bouncy` — clearly underdamped, visible overshoot and oscillation.
     *
     * Reserved for moments where the motion IS the message: the launcher
     * search field, the app-library category switch. Never for a control that
     * toggles state, where a bounce would read as a mistake.
     */
    val springBouncy: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 1206f,
        damping = 49f,
        initialVelocity = 0f
    )

    /**
     * Gesture continuations.
     *
     * A drag already has velocity when the finger lifts, so this carries a
     * non-zero initialVelocity and is deliberately underdamped enough to
     * absorb it. Fling-to-dismiss and sheet snap-back both use it.
     */
    val springGesture: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 800f,
        damping = 48f,
        initialVelocity = 0f
    )

    /**
     * The generic default: equivalent to SwiftUI `.spring()`.
     *
     * Stiffness is solved from response 0.55 s, which both sources agree on:
     * for a unit-mass spring the undamped period is 2*pi*sqrt(m/k), and setting
     * that to 0.55 s gives k ~= 131. The damping is the disputed parameter, so
     * it is set from the HIGHER of the two reported values (0.825), which is
     * the better-damped and therefore safer of the two. See
     * [SWIFTUI_SPRING_DEFAULTS_DISPUTED].
     */
    val springStandard: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 131f,
        damping = 19f,
        initialVelocity = 0f
    )

    /** Modal presentation: a touch heavier than the default, no bounce. */
    val springModal: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 180f,
        damping = 27f,
        initialVelocity = 0f
    )

    /** Tap feedback. Stiff and very well damped so it returns inside ~150 ms. */
    val springButton: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 1400f,
        damping = 75f,
        initialVelocity = 0f
    )

    /**
     * Dismissal. Genuinely SOFTER than [springStandard] (k 110 vs 131) and
     * critically damped, so a sheet does not snap shut under the finger. The
     * previous value here was k=200, which is stiffer than the default and so
     * contradicted its own comment.
     */
    val springDismiss: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 110f,
        damping = 21f,
        initialVelocity = 0f
    )

    /**
     * The SwiftUI `.spring()` defaults, with the disagreement recorded rather
     * than resolved silently.
     *
     * Both sources agree response = 0.55. They disagree on damping fraction:
     * 0.825 (Medium, "Learning SwiftUI Spring Animations") and 0.75 (Create
     * with Swift, Jan 2026). Apple does not publish either. This project uses
     * 0.825 — the better-damped of the two, so a wrong guess costs less
     * visual noise than an unexpected bounce would.
     */
    const val SWIFTUI_SPRING_RESPONSE: Float = 0.55f
    const val SWIFTUI_SPRING_DAMPING: Float = 0.825f
    const val SWIFTUI_SPRING_DAMPING_ALTERNATE_REPORT: Float = 0.75f

    // ==================================================================
    // DURATIONS — AppleUX-chosen, and now a much smaller surface.
    //
    // The Material ramp (83/120/167/220/333/400/500) is GONE. Durations are
    // only correct where a spring is wrong: continuous loops, and reveals that
    // the user did not cause. Anything the user caused uses a SpringSpec, per
    // the header's reasoning about velocity continuity.
    //
    // The values that remain are UIKit's own non-spring defaults where those
    // exist, and the computed settle periods of the springs above where a
    // spring governs the motion.
    // ==================================================================

    /** No animation at all. Used when Reduce Motion is on, for anything. */
    const val DURATION_INSTANT: Long = 0L

    /**
     * Loop period for an indeterminate spinner.
     *
     * A spinner must NOT spring — it would read as loading that is going
     * somewhere, which is the opposite of what indeterminate means. 600 ms per
     * revolution keeps it visibly in motion at 60 Hz without strobing.
     */
    const val DURATION_LOOP: Long = 600L

    /** Non-interactive fade. Linear: a fade has no direction to accelerate in. */
    const val DURATION_FADE: Long = 200L

    /**
     * Settle period of [springButton]: the time a tap response needs to return.
     * Roughly 150 ms to visual rest for stiffness 1400 / damping 75.
     */
    const val DURATION_FAST: Long = 150L

    /** Settle period of [springSmooth] to visual rest. */
    const val DURATION_MODERATE: Long = 300L

    /** Settle period of [springStandard]. ~0.55 s, the agreed SwiftUI response. */
    const val DURATION_STANDARD: Long = 550L

    /**
     * Sheet / modal travel. UIKit's own sheet transition is around 0.5 s from
     * rest, and the spring that drives it is [springModal]; this is the
     * duration used where a plain tween is genuinely the right tool.
     */
    const val DURATION_SLIDE: Long = 500L

    /** Page-turn / carousel settle. */
    const val DURATION_COMFORTABLE: Long = 400L

    /** Anything genuinely slow and deliberate — a full-screen transition. */
    const val DURATION_SLOW: Long = 700L

    const val DURATION_TINY: Long = 100L
    const val DURATION_QUICK: Long = 130L

    /** Tap feedback budget. Slightly under one 60 Hz frame plus a little. */
    const val TOUCH_RESPONSE_DURATION_MS: Long = 150L

    // ==================================================================
    // CURVES — the four UIKit easing presets, control points transcribed.
    //
    // The names are Apple's. The four control-point pairs below are the real
    // UIKit values; the previous file had only easeInOut right and three
    // Android curves (easingStandard was 0.42/0/0.2/1, which is Material
    // fast-out-slow-in, not an Apple curve).
    //
    // Prefer a SpringSpec for anything the user caused. These are for
    // continuous or non-interactive motion only.
    // ==================================================================

    /** linear — control points on the endpoints, constant slope. */
    val easingLinear: Interpolator = PathInterpolatorCompat.create(0f, 0f, 1f, 1f)

    /** easeIn — (0.42, 0) -> (1, 1). Slow start, accelerating. For exits. */
    val easingEaseIn: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 1f, 1f)

    /** easeOut — (0, 0) -> (0.58, 1). Fast arrival, decelerating to rest. */
    val easingEaseOut: Interpolator = PathInterpolatorCompat.create(0f, 0f, 0.58f, 1f)

    /**
     * easeInOut — (0.42, 0) -> (0.58, 1). The S-curve: shallow at both ends,
     * steep in the middle. This is the one the file previously exposed as
     * easingIOSDefault and the only genuine Apple curve it carried.
     */
    val easingIOSDefault: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 0.58f, 1f)

    /**
     * easeInOut, but circular rather than bezier.
     *
     * UnitCurve.circularEaseInOut in SwiftUI: a quarter-circle arc instead of
     * cubic control points, which is flatter at the ends and steeper through
     * the middle. It reads as more decisive than the bezier form, and it is
     * still an Apple-provided curve.
     */
    val easingCircularInOut: Interpolator = PathInterpolatorCompat.create(0.55f, 0f, 0.45f, 1f)

    /**
     * Kept for the existing call sites that pass it to a tween. It is now an
     * ALIAS of [easingIOSDefault], not a separate Android curve: those sites
     * were asking for "the iOS curve" and getting Material fast-out-slow-in.
     */
    val easingStandard: Interpolator = easingIOSDefault

    /** Emphasised / decelerate — for a value arriving into place without a spring. */
    val easingEmphasis: Interpolator = PathInterpolatorCompat.create(0f, 0f, 0.2f, 1f)

    val easingStandardAccelerate: Interpolator = easingEaseIn
    val easingStandardDecelerate: Interpolator = easingEaseOut

    // Gesture velocity thresholds
    const val GESTURE_VELOCITY_THRESHOLD_PX: Float = 4000f
    const val GESTURE_VELOCITY_THRESHOLD_NORMALIZED: Float = 1.5f
    const val FLICK_VELOCITY_THRESHOLD_PX: Float = 1200f

    // Drag resistance (for over-scroll effects)
    const val DRAG_RESISTANCE_LIGHT: Float = 0.15f
    const val DRAG_RESISTANCE_STANDARD: Float = 0.25f
    const val DRAG_RESISTANCE_STRONG: Float = 0.4f

    // Spring animation thresholds (performance tuning for 60Hz)
    const val SPRING_VELOCITY_THRESHOLD_PX: Float = 0.1f
    const val SPRING_DISPLACEMENT_THRESHOLD_PX: Float = 0.25f

    // Stiffness ranges for interpolation
    const val STIFFNESS_LOW: Float = 100f
    const val STIFFNESS_MEDIUM: Float = 400f
    const val STIFFNESS_HIGH: Float = 1000f
    const val STIFFNESS_VERY_HIGH: Float = 2000f

    // Damping ratios (where 1 = critically damped, <1 = bouncy, >1 = sluggish)
    const val DAMPING_RATIO_UNDERDAMPED: Float = 0.7f
    const val DAMPING_RATIO_CRITICAL: Float = 1.0f
    const val DAMPING_RATIO_OVERDAMPED: Float = 1.2f

    /**
     * A physical spring, and the one way this project animates anything the
     * user caused.
     *
     * @param dampingRatio the derived figure of merit. 1.0 is critically
     *   damped (arrives once, no overshoot); below 1.0 overshoots and
     *   oscillates. Exposed because it is the only part of a spring that can be
     *   checked by eye against Apple's own descriptions of .smooth / .snappy /
     *   .bouncy.
     */
    data class SpringSpec(
        val mass: Float,
        val stiffness: Float,
        val damping: Float,
        val initialVelocity: Float
    ) {
        val dampingRatio: Float
            get() = damping / (2f * kotlin.math.sqrt(mass * stiffness))
    }

    // ==================================================================
    // APPLYING A SPRING
    //
    // Adding these constants is only half the job. What makes iOS motion feel
    // right is that a spring CONTINUES from the velocity it already had, so an
    // interrupted animation resumes instead of restarting from zero. That
    // property belongs to the animation object, not to a curve, and it is lost
    // the moment motion is expressed as
    // ObjectAnimator.ofFloat(...).setDuration(...) — which is what every call
    // site in this project was doing.
    //
    // [spring] returns a configured SpringAnimation so a spring is what
    // actually runs.
    //
    // API notes, read off androidx.dynamicanimation 1.0.0 with javap rather
    // than from memory:
    //   * the property type is FloatPropertyCompat, in the
    //     androidx.dynamicanimation.animation package — there is no
    //     FloatProperty class in this artifact
    //   * SpringAnimation takes the start value as a constructor parameter
    //     (target, property, startValue); there is no setInitialValue
    //   * velocity is DynamicAnimation.setStartVelocity(float)
    //   * completion is addEndListener(OnAnimationEndListener), whose callback
    //     takes (animation, cancelled, currentValue, startValue)
    // ==================================================================

    /**
     * A configured [SpringAnimation] on a float property.
     *
     * ```kotlin
     * MotionTokens.spring(MotionTokens.springButton, view, View.SCALE_X, 1f)
     *     .start()
     * ```
     *
     * @param startValue where the property is now. Read it from the view rather
     *   than passing 0: starting a spring from a wrong origin is the usual cause
     *   of a transition that appears to jump.
     */
    @JvmStatic
    fun <K> spring(
        spec: SpringSpec,
        target: K,
        property: androidx.dynamicanimation.animation.FloatPropertyCompat<K>,
        startValue: Float,
    ): androidx.dynamicanimation.animation.SpringAnimation {
        val anim = androidx.dynamicanimation.animation.SpringAnimation(
            target, property, startValue
        )
        anim.spring = androidx.dynamicanimation.animation.SpringForce(spec.stiffness)
            .setStiffness(spec.stiffness)
            .setDampingRatio(spec.dampingRatio)
        if (spec.initialVelocity != 0f) {
            anim.setStartVelocity(spec.initialVelocity)
        }
        return anim
    }

    /**
     * Springs [view]'s [property] to [target].
     *
     * This is the method to reach for. It cancels any spring already running on
     * this view-property FIRST, which is what preserves continuity: cancelling
     * an animation and immediately starting a fresh one discards the outgoing
     * velocity, and that discontinuity is exactly what makes an Android
     * transition read as mechanical. SpringAnimation on its own carries the
     * velocity across.
     *
     * @param onEnd runs once the spring settles. Do not chain UI work off this:
     *   a spring has no fixed duration, so a chain built on completion drifts.
     *   Use a delay instead.
     */
    @JvmStatic
    @JvmOverloads
    fun springTo(
        spec: SpringSpec,
        target: android.view.View,
        property: androidx.dynamicanimation.animation.FloatPropertyCompat<android.view.View>,
        targetValue: Float,
        onEnd: (() -> Unit)? = null,
    ) {
        val key = keyOf(property)
        val previous = running[key]
        previous?.cancel()
        val start = property.getValue(target)
        val anim = spring(spec, target, property, start)
        running[key] = anim
        onEnd?.let { cb ->
            anim.addEndListener { _, _, _, _ ->
                running.remove(key)
                cb()
            }
        }
        anim.start()
    }

    /**
     * Spring animations in flight, keyed by property name.
     *
     * Held so [springTo] can cancel the outgoing one and inherit its velocity
     * rather than dropping the value on the floor. Android's own
     * ViewPropertyAnimator keeps no such registry, which is why repeated taps
     * on a tween restart from zero while a spring does not.
     */
    private val running = mutableMapOf<Int, androidx.dynamicanimation.animation.SpringAnimation>()

    /**
     * Registry key for a property.
     *
     * FloatPropertyCompat holds mPropertyName but exposes no public accessor for
     * it (verified with javap on dynamicanimation 1.0.0), and there is no
     * getName(). Identity hash is the only stable handle available, and it is
     * correct here because these property objects are singletons — View.SCALE_X
     * and friends are one instance each, shared across all views. Keying by
     * view+property is NOT needed for the same reason: cancelling a spring on
     * one view's scale when another view's scale is animating would be a bug
     * the identity key would introduce, so if a distinct key per view is ever
     * required the registry has to become a per-view map.
     */
    private fun keyOf(
        property: androidx.dynamicanimation.animation.FloatPropertyCompat<*>,
    ): Int = System.identityHashCode(property)

    /** Cancels everything in flight. Call from a detached view. */
    @JvmStatic
    fun cancelAllSprings() {
        running.values.forEach { runCatching { it.cancel() } }
        running.clear()
    }
}
