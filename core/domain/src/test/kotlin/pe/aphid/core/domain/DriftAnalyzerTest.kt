package pe.aphid.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pe.aphid.core.domain.rules.DriftAnalyzer
import pe.aphid.core.model.Reading

class DriftAnalyzerTest {
    private val h = 3_600_000L
    private val analyzer = DriftAnalyzer()
    private fun r(t: Long, ph: Double? = null, ec: Double? = null, vol: Double? = null) =
        Reading(systemId = 1, timestampMillis = t * h, ph = ph, ec = ec, volumeRemainingL = vol)

    @Test
    fun ecUpVolumeDownMeansWaterOnly() {
        val out = analyzer.explain(listOf(r(0, ec = 1.5, vol = 100.0), r(48, ec = 1.8, vol = 80.0)))
        assertEquals("Reponer con agua sola.", out.single().action)
    }

    @Test
    fun ecDownVolumeStableMeansHalfDose() {
        val out = analyzer.explain(listOf(r(0, ec = 1.8, vol = 100.0), r(48, ec = 1.5, vol = 98.0)))
        assertEquals("Reponer solución a media dosis.", out.single().action)
    }

    @Test
    fun phRiseWithNitrateIsExpected() {
        val out = analyzer.explain(listOf(r(0, ph = 5.8), r(24, ph = 6.2)), nitrateFraction = 0.95)
        assertTrue(out.any { it.id == "ph_sube_nitrato" })
        assertTrue(analyzer.explain(listOf(r(0, ph = 5.8), r(24, ph = 6.2)), nitrateFraction = 0.5).none { it.id == "ph_sube_nitrato" })
    }

    @Test
    fun phSwingOverOnePerDay() {
        val out = analyzer.explain(listOf(r(0, ph = 5.5), r(6, ph = 6.8), r(12, ph = 5.6)))
        assertTrue(out.any { it.id == "ph_oscila" })
    }

    @Test
    fun stableReadingsNoExplanation() {
        assertTrue(analyzer.explain(listOf(r(0, ph = 5.8, ec = 1.5, vol = 100.0), r(24, ph = 5.9, ec = 1.52, vol = 99.0))).isEmpty())
    }
}
