package io.github.devjonny.moa

import io.github.devjonny.moa.Ballistics.FPS_TO_MPS
import io.github.devjonny.moa.Ballistics.INCH_TO_M
import io.github.devjonny.moa.Ballistics.YARD_TO_M
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.tan

class BallisticsTest {
    private val airRifle = Ballistics.Setup(
        muzzleVelocityMps = 790 * FPS_TO_MPS,
        bc = 0.021,
        sightHeightM = 1.5 * INCH_TO_M,
        zeroM = 25 * YARD_TO_M,
    )

    private fun yards(vararg ys: Int) = ys.map { it * YARD_TO_M }

    @Test
    fun `holdover is zero at the zero range`() {
        val atZero = Ballistics.holdovers(airRifle, yards(25))!!.first()
        assertEquals(0.0, atZero.heightM, 0.0005)
    }

    @Test
    fun `holdover grows past the zero`() {
        val (at35, at45, at55) = Ballistics.holdovers(airRifle, yards(35, 45, 55))!!.map { it.mils }
        assertTrue("$at35, $at45, $at55", at35 > 0 && at35 < at45 && at45 < at55)
    }

    @Test
    fun `matches the web app at 40 yards`() {
        // ballistics.js gives 0.91 mil and 1.30" low for the same setup.
        val at40 = Ballistics.holdovers(airRifle, yards(40))!!.first()
        assertEquals(0.91, at40.mils, 0.01)
        assertEquals(-1.30, at40.heightM / INCH_TO_M, 0.01)
    }

    @Test
    fun `with negligible drag the path matches the vacuum formula`() {
        val setup = Ballistics.Setup(muzzleVelocityMps = 300.0, bc = 1e9, sightHeightM = 0.04, zeroM = 30.0)
        val at60 = Ballistics.holdovers(setup, listOf(60.0))!!.first()

        val g = 9.80665
        val v = setup.muzzleVelocityMps
        val h = setup.sightHeightM
        val z = setup.zeroM
        fun drop(x: Double, theta: Double) = g * x * x / (2 * v * v * cos(theta) * cos(theta))
        var theta = 0.0
        repeat(50) { theta = atan((h + drop(z, theta)) / z) }
        val expected = -h + 60 * tan(theta) - drop(60.0, theta)

        assertEquals(expected, at60.heightM, 0.001)
    }

    @Test
    fun `missing inputs give no result`() = assertNull(Ballistics.holdovers(airRifle.copy(bc = 0.0), listOf(10.0)))

    @Test
    fun `a reticle true at 10x reads half the dots at 5x`() = assertEquals(0.5, Ballistics.reticleDots(1.0, 5.0, 10.0), 0.0)
}
