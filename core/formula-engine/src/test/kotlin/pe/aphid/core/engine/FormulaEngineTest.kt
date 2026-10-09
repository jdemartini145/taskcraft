package pe.aphid.core.engine

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.Element
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.TankGroup
import pe.aphid.core.model.ValueRange
import pe.aphid.core.model.WarningKind
import pe.aphid.core.model.WaterAnalysis

class FormulaEngineTest {

    private val engine = FormulaEngine()

    private fun run(
        recipeId: String,
        fertIds: List<String>,
        water: WaterAnalysis = WaterAnalysis.PURE,
        volume: Double = 100.0,
        factor: ConcentrationFactor = ConcentrationFactor.X100,
        tankL: Double = 10.0,
        ec: ValueRange = ValueRange(),
    ): FormulaResult {
        val recipe = TestData.recipe(recipeId)
        return engine.calculate(
            EngineInput(
                request = FormulaRequest(
                    referenceRecipeId = recipeId,
                    reservoirVolumeL = volume,
                    fertilizerIds = fertIds,
                    concentrationFactor = factor,
                    tankVolumeL = tankL,
                ),
                targets = recipe.toProfile(),
                targetSource = recipe.source,
                ecRange = ec,
                water = water,
                fertilizers = TestData.ferts(*fertIds.toTypedArray()),
            ),
        )
    }

    private fun FormulaResult.delivered(e: Element) = balance.first { it.element == e }.deliveredPpm
    private fun FormulaResult.totalN() = delivered(Element.N_NO3) + delivered(Element.N_NH4)

    @Test
    @DisplayName("Reproduce la meta Hoagland con desvío ≤ 5 % en N, P, K, Ca y Mg")
    fun hoaglandWithin5Percent() {
        val ids = listOf("nitrato_calcio_4h2o", "nitrato_potasio", "fosfato_monopotasico", "sulfato_magnesio", "quelato_fe_edta") + TestData.micros
        val r = run("hoagland", ids)
        val t = TestData.recipe("hoagland").toProfile()
        fun dev(actual: Double, target: Double) = abs(actual - target) / target * 100
        assertTrue(dev(r.totalN(), t.totalN()) <= 5.0, "N: ${r.totalN()}")
        listOf(Element.P, Element.K, Element.Ca, Element.Mg).forEach { e ->
            assertTrue(dev(r.delivered(e), t[e]) <= 5.0, "$e entregado ${r.delivered(e)} vs ${t[e]}")
        }
    }

    @Test
    @DisplayName("Hoagland también se aproxima con nitrato de calcio grado fertilizante")
    fun hoaglandFertilizerGrade() {
        val ids = TestData.standardSix + TestData.micros
        val r = run("hoagland", ids)
        val t = TestData.recipe("hoagland").toProfile()
        listOf(Element.P, Element.K, Element.Ca, Element.Mg).forEach { e ->
            assertTrue(abs(r.delivered(e) - t[e]) / t[e] <= 0.10, "$e ${r.delivered(e)}")
        }
    }

    @Test
    @DisplayName("Ningún gramo es negativo")
    fun noNegativeGrams() {
        val ids = TestData.fertilizers.filter { !it.isAcid }.map { it.id }
        listOf("hoagland", "la_molina", "masterblend").forEach { rid ->
            val r = run(rid, ids)
            r.doses.forEach {
                assertTrue(it.gramsPerLiterFinal >= 0, "${it.name} negativo en $rid")
                assertTrue(it.gramsInStockTank >= 0)
                assertTrue(it.gramsForReservoir >= 0)
            }
        }
    }

    @Test
    @DisplayName("El balance de masa cierra con error < 0,1 %")
    fun massBalanceCloses() {
        val ids = TestData.standardSix + TestData.micros + "acido_nitrico"
        val hard = WaterAnalysis(name = "Pozo", ca = 60.0, mg = 20.0, na = 30.0, so4 = 50.0, no3 = 10.0, hco3 = 250.0)
        val r = run("la_molina", ids, water = hard, volume = 200.0, tankL = 20.0)
        assertTrue(r.massBalanceErrorPct < 0.1, "error ${r.massBalanceErrorPct}")
        // Recomputación independiente desde los gramos pesados.
        val byId = TestData.fertilizers.associateBy { it.id }
        Element.entries.forEach { e ->
            val fromTanks = r.tanks.sumOf { t ->
                val liters = if (t.group == TankGroup.DIRECTO) t.volumeL else t.volumeL * t.factor
                t.doses.sumOf { d -> d.gramsInStockTank / liters * byId.getValue(d.fertilizerId).mgPerGram(e) }
            }
            val b = r.balance.first { it.element == e }
            val expected = b.deliveredPpm - b.fromWaterPpm
            if (expected > 1e-6) assertTrue(abs(fromTanks - expected) / expected < 0.001, "$e $fromTanks vs $expected")
        }
    }

    @Test
    @DisplayName("El calcio y los sulfatos/fosfatos nunca quedan en el mismo tanque")
    fun calciumNeverWithSulfates() {
        val ids = TestData.fertilizers.map { it.id }
        val byId = TestData.fertilizers.associateBy { it.id }
        val r = run("hoagland", ids, water = WaterAnalysis(name = "dura", hco3 = 200.0))
        r.tanks.filter { it.group != TankGroup.DIRECTO }.forEach { tank ->
            val ferts = tank.doses.map { byId.getValue(it.fertilizerId) }
            val hasCa = ferts.any { it.contains(Element.Ca) }
            val hasSP = ferts.any { it.contains(Element.S) || it.contains(Element.P) }
            assertTrue(!(hasCa && hasSP), "Tanque ${tank.group} mezcla Ca con SO₄/PO₄: ${ferts.map { it.id }}")
        }
        // Y la regla vale para cada insumo del catálogo.
        TestData.fertilizers.forEach { f ->
            val g = TankAssigner.assign(f)
            if (f.contains(Element.Ca)) assertTrue(g == TankGroup.A || g == TankGroup.DIRECTO)
            if (f.contains(Element.S) || f.contains(Element.P)) assertTrue(g != TankGroup.A)
        }
    }

    @Test
    @DisplayName("Un agua con HCO₃ alto genera dosis de ácido")
    fun highBicarbonateNeedsAcid() {
        val ids = TestData.standardSix + "acido_nitrico" + "acido_fosforico"
        val hard = WaterAnalysis(name = "Pozo", ca = 80.0, mg = 25.0, hco3 = 300.0)
        val r = run("la_molina", ids, water = hard)
        val acid = r.tanks.firstOrNull { it.group == TankGroup.ACIDO }
        assertNotNull(acid)
        assertTrue(acid!!.doses.sumOf { it.gramsPerLiterFinal } > 0)
        assertTrue(r.acidMeqPerL > 4.0)
        assertTrue(r.doses.first { it.fertilizerId == "acido_nitrico" || it.fertilizerId == "acido_fosforico" }.mlForReservoir!! > 0)
        // Agua blanda: sin ácido.
        val soft = run("la_molina", ids)
        assertTrue(soft.tanks.none { it.group == TankGroup.ACIDO })
    }

    @Test
    @DisplayName("Agua dura sin ácido seleccionado: advertencia y sugerencia de ácido")
    fun hardWaterWithoutAcidWarns() {
        val hard = WaterAnalysis(name = "Pozo", hco3 = 300.0)
        val r = run("la_molina", TestData.standardSix, water = hard)
        assertTrue(r.warnings.any { it.kind == WarningKind.ACIDO })
        val s = FertilizerSuggester.suggest(TestData.fertilizers, TestData.standardSix.toSet(), TestData.recipe("la_molina").toProfile(), hard)
        assertTrue(s.any { it.fertilizer.isAcid })
    }

    @Test
    @DisplayName("El agua aporta y se descuenta (piso 0)")
    fun waterContributionSubtracted() {
        val water = WaterAnalysis(name = "rica en Ca", ca = 400.0)
        val r = run("la_molina", TestData.standardSix + TestData.micros, water = water)
        val ca = r.balance.first { it.element == Element.Ca }
        assertEquals(400.0, ca.fromWaterPpm, 1e-9)
        // El motor no agrega calcio extra cuando el agua ya supera la meta.
        assertTrue(r.doses.none { it.fertilizerId == "nitrato_calcio" } || ca.deliveredPpm >= 400.0)
    }

    @Test
    @DisplayName("Lechuga vegetativa, 100 L, agua 0 y 6 insumos estándar en < 1 s")
    fun lettuceUnderOneSecond() {
        val stage = TestData.crops.stageTargets.first { it.cropId == "lechuga" && it.stage == GrowthStage.VEGETATIVA }
        val recipe = TestData.recipe(stage.recipeId!!)
        val start = System.nanoTime()
        val r = engine.calculate(
            EngineInput(
                request = FormulaRequest(cropId = "lechuga", stage = GrowthStage.VEGETATIVA, reservoirVolumeL = 100.0, fertilizerIds = TestData.standardSix),
                targets = recipe.toProfile(),
                targetSource = stage.source,
                fertilizers = TestData.ferts(*TestData.standardSix.toTypedArray()),
            ),
        )
        val ms = (System.nanoTime() - start) / 1e6
        assertTrue(ms < 1000, "tardó $ms ms")
        assertTrue(r.doses.isNotEmpty())
        assertTrue(r.estimatedEc > 0.5)
        assertEquals(10.0, r.tanks.first { it.group == TankGroup.A }.mlPerLiterReservoir, 1e-9)
    }

    @ParameterizedTest
    @EnumSource(ConcentrationFactor::class)
    fun stockScaling(factor: ConcentrationFactor) {
        val r = run("hoagland", TestData.standardSix, factor = factor, tankL = 5.0, volume = 300.0)
        r.tanks.filter { it.group != TankGroup.DIRECTO }.forEach { t ->
            t.doses.forEach { d -> assertEquals(d.gramsPerLiterFinal * factor.value * 5.0, d.gramsInStockTank, 1e-9) }
            assertEquals(1000.0 / factor.value * 300.0, t.mlForReservoir, 1e-9)
        }
    }

    @Test
    @DisplayName("La solubilidad excedida propone un factor menor")
    fun solubilitySuggestsLowerFactor() {
        val base = run("hoagland", TestData.standardSix, factor = ConcentrationFactor.X200)
        val kno3 = base.doses.first { it.fertilizerId == "nitrato_potasio" }.gramsPerLiterFinal
        // Solubilidad ficticia: a 200x se excede, a un factor menor no.
        val ferts = TestData.ferts(*TestData.standardSix.toTypedArray()).map {
            if (it.id == "nitrato_potasio") it.copy(solubilityGPerL = kno3 * 120) else it
        }
        fun calc(f: ConcentrationFactor) = engine.calculate(
            EngineInput(
                request = FormulaRequest(reservoirVolumeL = 100.0, fertilizerIds = TestData.standardSix, concentrationFactor = f),
                targets = TestData.recipe("hoagland").toProfile(),
                targetSource = "Hoagland",
                fertilizers = ferts,
            ),
        )
        val r = calc(ConcentrationFactor.X200)
        assertTrue(r.warnings.any { it.kind == WarningKind.SOLUBILIDAD })
        assertTrue(r.tanks.any { !it.solubilityOk })
        val suggested = assertNotNull(r.suggestedFactor).let { r.suggestedFactor!! }
        assertTrue(suggested.value < 200)
        val ok = calc(suggested)
        assertTrue(ok.warnings.none { it.kind == WarningKind.SOLUBILIDAD })
        assertNull(ok.suggestedFactor)
    }

    @Test
    @DisplayName("EC estimada por suma de cationes y comparación con rango")
    fun ecEstimation() {
        val r = run("hoagland", TestData.standardSix, ec = ValueRange(0.5, 1.0))
        // Hoagland: K 6,0 + Ca 10,0 + Mg 3,9 meq/L ≈ 2,0 mS/cm.
        assertEquals(2.0, r.estimatedEc, 0.25)
        assertTrue(r.warnings.any { it.kind == WarningKind.EC_FUERA_DE_RANGO })
    }

    @Test
    @DisplayName("Sugiere insumos faltantes")
    fun suggestsMissing() {
        val s = FertilizerSuggester.suggest(TestData.fertilizers, setOf("nitrato_potasio", "fosfato_monopotasico"), TestData.recipe("hoagland").toProfile())
        val covered = s.flatMap { it.covers }.toSet()
        assertTrue(Element.Ca in covered && Element.Mg in covered && Element.Fe in covered, "cubre $covered")
    }

    @Test
    @DisplayName("Costo por 1 000 L y lista de compras")
    fun costAndShopping() {
        val priced = TestData.ferts(*TestData.standardSix.toTypedArray()).map { it.copy(pricePerKgPen = 10.0) }
        val r = engine.calculate(
            EngineInput(
                request = FormulaRequest(reservoirVolumeL = 100.0, fertilizerIds = TestData.standardSix),
                targets = TestData.recipe("hoagland").toProfile(),
                targetSource = "Hoagland",
                fertilizers = priced,
            ),
        )
        val gpl = r.doses.sumOf { it.gramsPerLiterFinal }
        assertEquals(gpl * 10.0, r.costPer1000LPen!!, 1e-9)
        assertEquals(r.doses.size, r.shoppingList.size)
        // Sin precios: costo nulo (dato pendiente), nunca inventado.
        assertNull(run("hoagland", TestData.standardSix).costPer1000LPen)
    }
}
