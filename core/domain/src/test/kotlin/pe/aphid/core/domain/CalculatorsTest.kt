package pe.aphid.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pe.aphid.core.domain.calc.Calculators
import pe.aphid.core.domain.pests.PestCatalog
import pe.aphid.core.domain.repository.TdsScale
import pe.aphid.core.domain.rules.CompatibilityChecker
import pe.aphid.core.domain.usecase.StageCalendar
import pe.aphid.core.model.Crop
import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.ValueRange

class CalculatorsTest {
    @Test
    fun ecPpmConversion() {
        assertEquals(750.0, Calculators.ecToPpm(1.5, TdsScale.PPM_500), 1e-9)
        assertEquals(1050.0, Calculators.ecToPpm(1.5, TdsScale.PPM_700), 1e-9)
        assertEquals(700.0, Calculators.convertPpm(500.0, TdsScale.PPM_500, TdsScale.PPM_700), 1e-9)
        assertEquals(2.0, Calculators.ppmToEc(1400.0, TdsScale.PPM_700), 1e-9)
    }

    @Test
    fun phByTestIsLinear() {
        val d = Calculators.phCorrectionByTest(volumeLiters = 100.0, neededDeltaPh = 0.6, testMl = 1.0, testLiters = 10.0, testDeltaPh = 0.3)
        assertEquals(20.0, d.totalMl, 1e-9)
        assertEquals(10.0, d.firstStepMl, 1e-9)
    }

    @Test
    fun replenishment() {
        val water = Calculators.replenish(100.0, 80.0, 2.0, 1.6, fullStrengthEc = 1.6)
        assertEquals(0.0, water.doseFraction, 1e-9)
        val half = Calculators.replenish(100.0, 80.0, 1.2, 1.6, fullStrengthEc = 1.6)
        assertTrue(half.doseFraction > 1.0)
        val mix = Calculators.replenish(100.0, 50.0, 1.6, 1.6, fullStrengthEc = 1.6)
        assertEquals(1.0, mix.doseFraction, 1e-9)
    }

    @Test
    fun compatibility() {
        val aji = CropStageTarget("aji", GrowthStage.VEGETATIVA, ph = ValueRange(null, 6.0))
        val manzana = CropStageTarget("manzana", GrowthStage.VEGETATIVA, ph = ValueRange(6.0, null))
        val brocoli = CropStageTarget("brocoli", GrowthStage.VEGETATIVA, ec = ValueRange(2.0, 3.5))
        val espinaca = CropStageTarget("espinaca", GrowthStage.VEGETATIVA, ec = ValueRange(1.8, 2.0))
        val fresa = CropStageTarget("fresa", GrowthStage.VEGETATIVA, ec = ValueRange(1.3, 1.6))
        assertTrue(CompatibilityChecker.check(listOf("Ají" to aji, "Manzana" to manzana)).compatible) // se tocan en 6,0
        assertTrue(CompatibilityChecker.check(listOf("Brócoli" to brocoli, "Espinaca" to espinaca)).compatible)
        val bad = CompatibilityChecker.check(listOf("Brócoli" to brocoli, "Fresa" to fresa))
        assertFalse(bad.compatible)
        assertTrue(bad.issues.single().contains("Fresa"))
    }

    @Test
    fun calendarNeedsData() {
        val c = Crop("x", "X", "X x", "F")
        assertEquals(null, StageCalendar.windows(c, 0))
        val full = c.copy(daysPerStage = GrowthStage.entries.associateWith { 10 })
        assertEquals(40L, StageCalendar.harvestEpochDay(full, 0))
        assertEquals(GrowthStage.FLORACION, StageCalendar.stageAt(full, 0, 25))
    }

    @Test
    fun pestSheetsComplete() {
        assertEquals(setOf("mosquito_sustrato", "pulgon", "trips", "mosca_blanca", "arana_roja"), PestCatalog.sheets.map { it.id }.toSet())
        PestCatalog.sheets.forEach { assertTrue(it.physicalControl.isNotEmpty() && it.biologicalControl.isNotEmpty()) }
    }
}
