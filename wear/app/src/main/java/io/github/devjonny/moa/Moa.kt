package io.github.devjonny.moa

import kotlin.math.PI
import kotlin.math.tan

// Mirrors moa.js in the web app. 1 MOA is 1/60 of a degree: ~1.047" at 100 yards.
object Moa {
    private const val MOA_RADIANS = PI / (180 * 60)
    private const val INCHES_PER_YARD = 36.0
    private const val METRES_PER_YARD = 0.9144

    const val MM_PER_INCH = 25.4

    fun toYards(distance: Double, metric: Boolean): Double =
        if (metric) distance / METRES_PER_YARD else distance

    fun moaInInches(distanceYards: Double): Double =
        distanceYards * INCHES_PER_YARD * tan(MOA_RADIANS)

    fun clickInInches(clickMoa: Double, distanceYards: Double): Double =
        clickMoa * moaInInches(distanceYards)

    fun offsetForClicks(clicks: Int, clickMoa: Double, distanceYards: Double): Double =
        clicks * clickInInches(clickMoa, distanceYards)

    fun clicksForOffset(offsetInches: Double, clickMoa: Double, distanceYards: Double): Double {
        val perClick = clickInInches(clickMoa, distanceYards)
        return if (perClick > 0) offsetInches / perClick else 0.0
    }
}
