package pe.aphid.core.domain.calc

import pe.aphid.core.domain.repository.TdsScale
import pe.aphid.core.engine.AcidCalculator
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.WaterAnalysis

/** Calculadoras de la bitácora. Todas son funciones puras. */
object Calculators {

    /** EC (mS/cm) → TDS (ppm) según la escala del medidor. */
    fun ecToPpm(ecMsCm: Double, scale: TdsScale): Double = ecMsCm * scale.factor

    fun ppmToEc(ppm: Double, scale: TdsScale): Double = ppm / scale.factor

    fun convertPpm(ppm: Double, from: TdsScale, to: TdsScale): Double = ppm / from.factor * to.factor

    /**
     * Corrección de pH por prueba de calibración: si [testMl] mL en [testLiters] L movieron el pH
     * [testDeltaPh], estima la dosis para mover [neededDeltaPh] en [volumeLiters]. Lineal; se
     * recomienda aplicar la mitad, esperar 15 min y volver a medir.
     */
    fun phCorrectionByTest(
        volumeLiters: Double,
        neededDeltaPh: Double,
        testMl: Double,
        testLiters: Double,
        testDeltaPh: Double,
    ): PhDose {
        require(testLiters > 0 && testDeltaPh > 0) { "La prueba necesita litros y cambio de pH mayores que 0" }
        val total = testMl / testLiters * volumeLiters * (neededDeltaPh / testDeltaPh)
        return PhDose(totalMl = total, firstStepMl = total / 2)
    }

    /** Ácido necesario para neutralizar el bicarbonato del agua en un volumen. */
    fun acidForAlkalinity(water: WaterAnalysis, acid: Fertilizer, volumeLiters: Double, residualHco3: Double = 30.5): PhDose {
        val meqPerL = maxOf(0.0, (water.hco3 - residualHco3) / WaterAnalysis.HCO3_MOLAR_MASS)
        val grams = meqPerL * AcidCalculator.gramsPerMeq(acid) * volumeLiters
        val ml = grams / (acid.acid?.densityGPerMl ?: 1.0)
        return PhDose(totalMl = ml, firstStepMl = ml / 2)
    }

    /**
     * Reposición: para volver a [fullLiters] con EC [targetEc] partiendo de [remainingLiters] a
     * [currentEc], calcula la EC que debe tener lo que se agrega (mezcla lineal) y la fracción de
     * dosis completa de la fórmula ([fullStrengthEc]).
     */
    fun replenish(
        fullLiters: Double,
        remainingLiters: Double,
        currentEc: Double,
        targetEc: Double,
        fullStrengthEc: Double,
        waterEc: Double = 0.0,
    ): Replenishment {
        require(fullLiters > 0 && remainingLiters >= 0 && remainingLiters <= fullLiters)
        val add = fullLiters - remainingLiters
        if (add <= 0.0) {
            val advice = if (currentEc > targetEc) "El reservorio está lleno y la EC alta: retira solución y repón con agua." else "Nada que reponer."
            return Replenishment(0.0, 0.0, 0.0, advice)
        }
        val ecAdd = (fullLiters * targetEc - remainingLiters * currentEc) / add
        return when {
            ecAdd <= waterEc -> {
                val finalEc = (remainingLiters * currentEc + add * waterEc) / fullLiters
                Replenishment(add, 0.0, finalEc, "Repón solo con agua. EC final estimada %.2f mS/cm.".format(finalEc))
            }
            else -> {
                val fraction = ((ecAdd - waterEc) / (fullStrengthEc - waterEc)).coerceAtLeast(0.0)
                Replenishment(add, fraction, targetEc, "Agrega %.0f L de solución al %.0f %% de la dosis completa.".format(add, fraction * 100))
            }
        }
    }
}

data class PhDose(val totalMl: Double, val firstStepMl: Double)

data class Replenishment(val litersToAdd: Double, val doseFraction: Double, val finalEc: Double, val advice: String)
