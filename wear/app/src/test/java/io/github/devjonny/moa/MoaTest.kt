package io.github.devjonny.moa

import org.junit.Assert.assertEquals
import org.junit.Test

class MoaTest {
    @Test
    fun `1 MOA at 100 yards is 1_047 inches`() = assertEquals(1.047, Moa.moaInInches(100.0), 0.001)

    @Test
    fun `quarter MOA click at 50 yards is 0_131 inches`() = assertEquals(0.131, Moa.clickInInches(0.25, 50.0), 0.001)

    @Test
    fun `100 metres is 109_36 yards`() = assertEquals(109.361, Moa.toYards(100.0, metric = true), 0.001)

    @Test
    fun `8 quarter MOA clicks at 50 yards moves 1_047 inches`() =
        assertEquals(1.047, Moa.offsetForClicks(8, 0.25, 50.0), 0.001)

    @Test
    fun `2 inches at 100 yards with quarter MOA clicks is 7_6 clicks`() =
        assertEquals(7.639, Moa.clicksForOffset(2.0, 0.25, 100.0), 0.001)

    @Test
    fun `zero distance gives zero clicks rather than infinity`() =
        assertEquals(0.0, Moa.clicksForOffset(2.0, 0.25, 0.0), 0.0)
}
