package dev.bmcreations.hinge

/**
 * The angle between the two halves of the device, in degrees. 180 is fully flat, 0 is shut.
 *
 * ## Do not lay out with this
 *
 * Apple's guidance for the Duo is explicit that the hinge angle is for *interaction*, not
 * layout, and the same reasoning applies on Android. Laying out from the angle produces
 * continuously-reflowing UI mid-fold, disagrees with the system's own chrome, and breaks on
 * every device whose detent behaviour differs from the one you tested on.
 *
 * Drive layout from [FoldPosture] and [FoldingState.regions]. Use the angle only for things
 * that genuinely track the physical motion: a parallax effect, a page-turn animation, a
 * "device is being opened" cue.
 *
 * Deliberately a `data class` rather than a `value class`: Kotlin/Native does not export
 * inline value classes to Objective-C, and this type is reachable from [FoldingState], which
 * the iOS framework must export. The allocation is not worth the interop hole.
 *
 * The angle is frequently unavailable — [FoldingState.hingeAngle] is nullable for that reason.
 * On Android it requires an OEM that publishes `Sensor.TYPE_HINGE_ANGLE`; many do not.
 */
public data class HingeAngle(val degrees: Float) {

    public val radians: Float get() = degrees * PI_OVER_180

    /** How far from flat the device is, in degrees. 0 when fully open. */
    public val deviationFromFlat: Float get() = FLAT_DEGREES - degrees

    /** Normalized 0..1 openness, where 0 is shut and 1 is flat. */
    public val fraction: Float
        get() = (degrees / FLAT_DEGREES).coerceIn(0f, 1f)

    public companion object {
        public const val FLAT_DEGREES: Float = 180f
        private const val PI_OVER_180: Float = 0.017453292f

        public val Flat: HingeAngle = HingeAngle(FLAT_DEGREES)
        public val Closed: HingeAngle = HingeAngle(0f)

        /** iOS reports `UIHinge.angle` in radians; this is the conversion for the bridge. */
        public fun ofRadians(radians: Float): HingeAngle =
            HingeAngle(radians / PI_OVER_180)
    }
}
