package pe.aphid.core.engine

import kotlin.math.abs
import kotlin.math.max
import pe.aphid.core.model.Element
import pe.aphid.core.model.ElementBalance
import pe.aphid.core.model.EngineOptions
import pe.aphid.core.model.EngineWarning
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FertilizerDose
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.ShoppingItem
import pe.aphid.core.model.StockTank
import pe.aphid.core.model.TankGroup
import pe.aphid.core.model.ValueRange
import pe.aphid.core.model.WarningKind
import pe.aphid.core.model.WaterAnalysis

/** Entrada completamente resuelta (sin acceso a base de datos) para el motor. */
data class EngineInput(
    val request: FormulaRequest,
    /** Meta bruta del cultivo (mg/L). */
    val targets: NutrientProfile,
    val targetSource: String,
    val ecRange: ValueRange = ValueRange(),
    val phRange: ValueRange = ValueRange(),
    val water: WaterAnalysis = WaterAnalysis.PURE,
    val fertilizers: List<Fertilizer>,
    val options: EngineOptions = EngineOptions(),
)

/**
 * Motor determinista de fórmulas. Kotlin/JVM puro, sin dependencias de Android.
 * Ver docs/MOTOR_FORMULAS.md para las ecuaciones.
 */
class FormulaEngine(private val clock: () -> Long = System::nanoTime) {

    fun calculate(input: EngineInput): FormulaResult {
        val start = clock()
        val req = input.request
        require(req.reservoirVolumeL > 0) { "El volumen del reservorio debe ser mayor que 0" }
        require(req.tankVolumeL > 0) { "El volumen del tanque debe ser mayor que 0" }
        val warnings = mutableListOf<EngineWarning>()
        val ferts = input.fertilizers.distinctBy { it.id }
        val water = input.water
        val waterProfile = water.toProfile()

        // Paso 1: meta neta = meta − agua (piso 0).
        val net = input.targets.minusFloorZero(waterProfile)

        // Paso 5 (antes de resolver): ácido para neutralizar bicarbonatos.
        val acidDoses = AcidCalculator.compute(water, net, ferts.filter { it.isAcid }, input.options, warnings)
        val acidProfile = profileOf(acidDoses, ferts)
        val netAfterAcid = net.minusFloorZero(acidProfile)

        // Paso 2–3: macronutrientes por NNLS ponderado.
        val macroFerts = ferts.filter { !it.isAcid && !it.isMicro }
        val macroDoses = solveBlock(
            elements = Element.macros,
            fertilizers = macroFerts,
            netTarget = netAfterAcid,
            grossTarget = input.targets,
            weights = input.options.weights,
            floorPpm = MACRO_SCALE_FLOOR,
        )

        // Paso 4: micronutrientes en un segundo paso, descontando lo que ya aportan las sales macro.
        val afterMacros = netAfterAcid.minusFloorZero(profileOf(macroDoses, ferts))
        val microFerts = ferts.filter { it.isMicro }
        val microDoses = solveBlock(
            elements = Element.micros,
            fertilizers = microFerts,
            netTarget = afterMacros,
            grossTarget = input.targets,
            weights = emptyMap(),
            floorPpm = MICRO_SCALE_FLOOR,
        )

        val byId = ferts.associateBy { it.id }
        val gramsPerL: Map<String, Double> = (acidDoses + macroDoses + microDoses)
            .filterValues { it > MIN_DOSE_G_PER_L }
            .filter { (id, g) -> id in acidDoses || !isNegligible(byId.getValue(id), g, input.targets) }
        val delivered = waterProfile + profileOf(gramsPerL, ferts)

        // Elementos con meta pero sin ningún insumo que los aporte.
        Element.entries.forEach { e ->
            if (net[e] > 0 && ferts.none { it.contains(e) }) {
                warnings += EngineWarning(
                    WarningKind.ELEMENTO_SIN_FUENTE,
                    "Ningún insumo seleccionado aporta ${e.symbol}. Revisa la lista de insumos sugeridos.",
                )
            }
        }

        // Paso 6–7: tanques y escalado.
        val tanks = buildTanks(req, gramsPerL, byId)
        if (tanks.any { it.group == TankGroup.DIRECTO }) {
            warnings += EngineWarning(
                WarningKind.INCOMPATIBILIDAD,
                "Hay insumos con calcio y sulfato/fosfato a la vez: disuélvelos directo en el reservorio, nunca en una madre.",
            )
        }

        // Paso 8: solubilidad.
        val suggested = SolubilityChecker.check(tanks, byId, req.concentrationFactor, warnings)

        // Paso 9: EC estimada por suma de cationes.
        val ec = EcEstimator.estimate(delivered, water)
        if (!input.ecRange.isEmpty && !input.ecRange.contains(ec)) {
            warnings += EngineWarning(
                WarningKind.EC_FUERA_DE_RANGO,
                "EC estimada %.2f mS/cm fuera del rango del cultivo (%s).".format(ec, input.ecRange.describe()),
            )
        }
        if (input.ecRange.isEmpty) {
            warnings += EngineWarning(WarningKind.DATO_PENDIENTE, "Rango de EC del cultivo: dato pendiente.")
        }

        // Paso 10: balance, costos, compras.
        val balance = Element.entries.map { e ->
            val target = input.targets.ppm[e]
            ElementBalance(
                element = e,
                targetPpm = target,
                fromWaterPpm = waterProfile[e],
                deliveredPpm = delivered[e],
                deviationPct = target?.takeIf { it > 0 }?.let { (delivered[e] - it) / it * 100.0 },
            )
        }
        val pricesKnown = gramsPerL.keys.all { byId.getValue(it).pricePerKgPen != null }
        val cost1000 = if (gramsPerL.isEmpty() || !pricesKnown) {
            null
        } else {
            gramsPerL.entries.sumOf { (id, g) -> g * byId.getValue(id).pricePerKgPen!! }
        }
        val shopping = tanks.flatMap { it.doses }.map { d ->
            ShoppingItem(d.fertilizerId, d.name, d.gramsInStockTank, byId.getValue(d.fertilizerId).pricePerKgPen?.let { it * d.gramsInStockTank / 1000.0 })
        }
        val massError = MassBalance.relativeErrorPct(tanks, waterProfile, delivered, byId)

        return FormulaResult(
            request = req,
            targetSource = input.targetSource,
            tanks = tanks,
            balance = balance,
            estimatedEc = ec,
            ecRange = input.ecRange,
            phTarget = input.phRange.midpoint,
            costPer1000LPen = cost1000,
            shoppingList = shopping,
            acidMeqPerL = AcidCalculator.requiredMeq(water, input.options),
            warnings = warnings.distinct(),
            suggestedFactor = suggested,
            massBalanceErrorPct = massError,
            engineVersion = VERSION,
            computeMillis = (clock() - start) / 1_000_000,
        )
    }

    /** Pasos 6–7: asigna cada insumo a su tanque y escala a solución madre. */
    private fun buildTanks(req: FormulaRequest, gramsPerL: Map<String, Double>, byId: Map<String, Fertilizer>): List<StockTank> {
        val factor = req.concentrationFactor.value
        val dosesByTank = gramsPerL.entries.groupBy { TankAssigner.assign(byId.getValue(it.key)) }
        return dosesByTank.toSortedMap().map { (group, entries) ->
            val direct = group == TankGroup.DIRECTO
            val f = if (direct) 1 else factor
            val vol = if (direct) req.reservoirVolumeL else req.tankVolumeL
            val doses = entries.sortedByDescending { it.value }.map { (id, gpl) ->
                val fert = byId.getValue(id)
                val forReservoir = gpl * req.reservoirVolumeL
                FertilizerDose(
                    fertilizerId = id,
                    name = fert.name,
                    tank = group,
                    gramsPerLiterFinal = gpl,
                    gramsInStockTank = if (direct) forReservoir else gpl * f * vol,
                    gramsForReservoir = forReservoir,
                    mlForReservoir = fert.acid?.let { forReservoir / it.densityGPerMl },
                    costPen = fert.pricePerKgPen?.let { it * forReservoir / 1000.0 },
                )
            }
            StockTank(
                group = group,
                volumeL = vol,
                factor = f,
                mlPerLiterReservoir = if (direct) 0.0 else 1000.0 / f,
                mlForReservoir = if (direct) 0.0 else 1000.0 / f * req.reservoirVolumeL,
                yieldsLitersFinal = if (direct) req.reservoirVolumeL else f * vol,
                doses = doses,
                solubilityOk = direct || SolubilityChecker.tankOk(doses, byId, f),
            )
        }
    }

    /** Resuelve un bloque (macro o micro) con NNLS ponderado y escalado relativo. */
    private fun solveBlock(
        elements: List<Element>,
        fertilizers: List<Fertilizer>,
        netTarget: NutrientProfile,
        grossTarget: NutrientProfile,
        weights: Map<Element, Double>,
        floorPpm: Double,
    ): Map<String, Double> {
        if (fertilizers.isEmpty()) return emptyMap()
        // Solo filas con meta o a las que algún insumo aporte (para penalizar excesos).
        val rows = elements.filter { e -> netTarget[e] > 0 || grossTarget[e] > 0 || fertilizers.any { it.contains(e) } }
            .filter { e -> grossTarget.ppm.containsKey(e) || fertilizers.any { it.contains(e) } }
        if (rows.isEmpty()) return emptyMap()
        val a = Array(rows.size) { i ->
            val e = rows[i]
            val s = scale(e, grossTarget, floorPpm, weights)
            DoubleArray(fertilizers.size) { j -> fertilizers[j].mgPerGram(e) * s }
        }
        val b = DoubleArray(rows.size) { i ->
            val e = rows[i]
            netTarget[e] * scale(e, grossTarget, floorPpm, weights)
        }
        val sol = Nnls.solve(a, b)
        return fertilizers.indices.associate { fertilizers[it].id to max(0.0, sol.x[it]) }
    }

    /** Una dosis es despreciable si aporta < 0,5 % de la meta de todos los elementos que contiene. */
    private fun isNegligible(f: Fertilizer, gramsPerL: Double, targets: NutrientProfile): Boolean =
        f.composition.keys.all { e ->
            val t = targets[e]
            t > 0 && gramsPerL * f.mgPerGram(e) < NEGLIGIBLE_FRACTION * t
        }

    private fun scale(e: Element, gross: NutrientProfile, floor: Double, weights: Map<Element, Double>): Double {
        val w = weights[e] ?: 1.0
        return w / max(gross[e], floor)
    }

    companion object {
        const val VERSION = "1.0.0"
        const val MACRO_SCALE_FLOOR = 10.0
        const val MICRO_SCALE_FLOOR = 0.01
        const val MIN_DOSE_G_PER_L = 1e-7
        const val NEGLIGIBLE_FRACTION = 0.005

        internal fun profileOf(gramsPerL: Map<String, Double>, ferts: List<Fertilizer>): NutrientProfile {
            val byId = ferts.associateBy { it.id }
            val ppm = Element.entries.associateWith { e ->
                gramsPerL.entries.sumOf { (id, g) -> g * (byId[id]?.mgPerGram(e) ?: 0.0) }
            }.filterValues { abs(it) > 0.0 }
            return NutrientProfile(ppm)
        }
    }
}

internal fun ValueRange.describe(): String = when {
    min != null && max != null -> "%.1f–%.1f".format(min, max)
    min != null -> "mín %.1f".format(min)
    max != null -> "máx %.1f".format(max)
    else -> "dato pendiente"
}

/** Paso 6: asignación de tanques. El calcio nunca va con sulfatos ni fosfatos en la madre. */
object TankAssigner {
    fun assign(f: Fertilizer): TankGroup {
        if (f.isAcid) return TankGroup.ACIDO
        val hasCa = f.contains(Element.Ca)
        val hasSorP = f.contains(Element.S) || f.contains(Element.P)
        return when {
            hasCa && hasSorP -> TankGroup.DIRECTO
            hasCa -> TankGroup.A
            hasSorP -> TankGroup.B
            f.defaultTank == TankGroup.A || f.defaultTank == TankGroup.B -> f.defaultTank
            else -> TankGroup.B
        }
    }
}

/** Paso 9: EC ≈ 0,1 × Σ cationes (meq/L) — regla empírica de Sonneveld. */
object EcEstimator {
    const val EC_PER_MEQ = 0.1
    fun estimate(delivered: NutrientProfile, water: WaterAnalysis): Double {
        val cations = Element.entries.sumOf { it.toMeq(delivered[it]) } + water.naMeq
        return cations * EC_PER_MEQ
    }
}
