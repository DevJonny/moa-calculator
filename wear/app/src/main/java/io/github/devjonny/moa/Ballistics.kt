package io.github.devjonny.moa

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Mirrors ballistics.js in the web app: point-mass trajectory with G1 drag, standard air, no wind.
object Ballistics {
    private const val G = 9.80665
    private const val AIR_DENSITY = 1.225
    private const val SPEED_OF_SOUND = 340.29
    private const val KG_PER_M2_PER_LB_PER_IN2 = 703.0696
    private const val TIME_STEP = 0.0001
    private const val MAX_FLIGHT_TIME = 5.0

    const val FPS_TO_MPS = 0.3048
    const val INCH_TO_M = 0.0254
    const val YARD_TO_M = 0.9144

    // G1 drag coefficient by Mach number. Air rifles stay well under Mach 1, so the table stops there.
    private val G1 = arrayOf(
        0.0 to 0.2629, 0.05 to 0.2558, 0.1 to 0.2487, 0.15 to 0.2413, 0.2 to 0.2344, 0.25 to 0.2278,
        0.3 to 0.2214, 0.35 to 0.2155, 0.4 to 0.2104, 0.45 to 0.2061, 0.5 to 0.2032, 0.55 to 0.202,
        0.6 to 0.2034, 0.7 to 0.2165, 0.725 to 0.223, 0.75 to 0.2313, 0.775 to 0.2417, 0.8 to 0.2546,
        0.825 to 0.2706, 0.85 to 0.2901, 0.875 to 0.3136, 0.9 to 0.3415, 0.925 to 0.3734, 0.95 to 0.4084,
        0.975 to 0.4448, 1.0 to 0.4805,
    )

    data class Setup(val muzzleVelocityMps: Double, val bc: Double, val sightHeightM: Double, val zeroM: Double)

    data class Hold(val rangeM: Double, val heightM: Double, val mils: Double)

    private fun g1DragCoefficient(mach: Double): Double {
        if (mach <= G1.first().first) return G1.first().second
        for (i in 1 until G1.size) {
            val (m1, cd1) = G1[i]
            if (mach <= m1) {
                val (m0, cd0) = G1[i - 1]
                return cd0 + (cd1 - cd0) * (mach - m0) / (m1 - m0)
            }
        }
        return G1.last().second
    }

    // Height relative to the line of sight (m, negative = below) at each ascending range; NaN where it falls short.
    private fun flightPath(angle: Double, velocity: Double, bcKgPerM2: Double, sightHeight: Double, rangesM: List<Double>): List<Double> {
        var x = 0.0
        var y = -sightHeight
        var vx = velocity * cos(angle)
        var vy = velocity * sin(angle)
        val heights = mutableListOf<Double>()
        var t = 0.0
        while (heights.size < rangesM.size && t < MAX_FLIGHT_TIME) {
            val v = hypot(vx, vy)
            val drag = AIR_DENSITY * v * v * g1DragCoefficient(v / SPEED_OF_SOUND) * PI / (8 * bcKgPerM2)
            val nx = x + vx * TIME_STEP
            val ny = y + vy * TIME_STEP
            vx -= drag * vx / v * TIME_STEP
            vy -= (drag * vy / v + G) * TIME_STEP
            while (heights.size < rangesM.size && nx >= rangesM[heights.size]) {
                val f = (rangesM[heights.size] - x) / (nx - x)
                heights += y + (ny - y) * f
            }
            x = nx
            y = ny
            t += TIME_STEP
        }
        while (heights.size < rangesM.size) heights += Double.NaN
        return heights
    }

    private fun zeroAngle(velocity: Double, bcKgPerM2: Double, sightHeight: Double, zeroM: Double): Double {
        var lo = -0.05
        var hi = 0.1
        repeat(60) {
            val mid = (lo + hi) / 2
            val height = flightPath(mid, velocity, bcKgPerM2, sightHeight, listOf(zeroM)).first()
            if (height.isNaN() || height < 0) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** Holdover at each ascending range; positive mils = hold over. Null when the setup can't be solved. */
    fun holdovers(setup: Setup, rangesM: List<Double>): List<Hold>? {
        with(setup) {
            if (!(muzzleVelocityMps > 0 && bc > 0 && sightHeightM >= 0 && zeroM > 0)) return null
            val bcKgPerM2 = bc * KG_PER_M2_PER_LB_PER_IN2
            val angle = zeroAngle(muzzleVelocityMps, bcKgPerM2, sightHeightM, zeroM)
            val heights = flightPath(angle, muzzleVelocityMps, bcKgPerM2, sightHeightM, rangesM)
            return rangesM.mapIndexed { i, r -> Hold(r, heights[i], -atan2(heights[i], r) * 1000) }
        }
    }

    // A second focal plane reticle only reads true at one magnification; elsewhere each dot covers more or less.
    fun reticleDots(mils: Double, magnification: Double, trueAtMagnification: Double): Double =
        mils * magnification / trueAtMagnification
}
