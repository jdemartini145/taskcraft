package pe.aphid.core.model

import kotlinx.serialization.Serializable

/** Factores de concentración admitidos para las soluciones madre. */
@Serializable
enum class ConcentrationFactor(val value: Int) { X50(50), X100(100), X200(200) }

/**
 * Solicitud de fórmula. Es también el objeto que la capa de IA puede producir desde
 * lenguaje natural (validado contra esquema): la IA nunca calcula gramos.
 */
@Serializable
data class FormulaRequest(
    val cropId: String? = null,
    val stage: GrowthStage? = null,
    /** Si no hay meta de cultivo completa, se puede usar una receta de referencia. */
    val referenceRecipeId: String? = null,
    val systemType: GrowSystemType? = null,
    val reservoirVolumeL: Double,
    /** Id del análisis de agua; null = agua de ósmosis/lluvia (0). */
    val waterAnalysisId: Long? = null,
    /** Texto libre para el agua (lo produce la IA, p. ej. "agua de pozo"); solo orientativo. */
    val waterHint: String? = null,
    val fertilizerIds: List<String> = emptyList(),
    val concentrationFactor: ConcentrationFactor = ConcentrationFactor.X100,
    val tankVolumeL: Double = 10.0,
)

/** Opciones del motor (configurables en Ajustes). */
@Serializable
data class EngineOptions(
    /** Si HCO₃ del agua supera este valor (mg/L) se calcula ácido. */
    val hco3LimitMgL: Double = 61.0,
    /** HCO₃ que se deja como reserva tampón (mg/L). */
    val hco3ResidualMgL: Double = 30.5,
    val weights: Map<Element, Double> = DEFAULT_WEIGHTS,
) {
    companion object {
        val DEFAULT_WEIGHTS: Map<Element, Double> = mapOf(
            Element.N_NO3 to 4.0, Element.N_NH4 to 1.0, Element.K to 4.0, Element.Ca to 4.0,
            Element.Mg to 4.0, Element.P to 2.0, Element.S to 1.0,
        )
    }
}

@Serializable
data class FertilizerDose(
    val fertilizerId: String,
    val name: String,
    val tank: TankGroup,
    /** g de insumo comercial por L de solución final. */
    val gramsPerLiterFinal: Double,
    /** g a pesar para el tanque de solución madre. */
    val gramsInStockTank: Double,
    /** g totales para el volumen del reservorio (dosificación directa). */
    val gramsForReservoir: Double,
    /** Solo ácidos: mL de producto comercial por volumen de reservorio. */
    val mlForReservoir: Double? = null,
    val costPen: Double? = null,
)

@Serializable
data class ElementBalance(
    val element: Element,
    val targetPpm: Double?,
    val fromWaterPpm: Double,
    val deliveredPpm: Double,
    /** (entregado - meta) / meta × 100; null si no hay meta. */
    val deviationPct: Double?,
)

@Serializable
data class StockTank(
    val group: TankGroup,
    val volumeL: Double,
    val factor: Int,
    /** mL de esta madre por litro de reservorio. */
    val mlPerLiterReservoir: Double,
    /** mL de esta madre para todo el reservorio. */
    val mlForReservoir: Double,
    /** Litros de solución final que rinde el tanque. */
    val yieldsLitersFinal: Double,
    val doses: List<FertilizerDose>,
    val solubilityOk: Boolean,
)

@Serializable
enum class WarningKind { SOLUBILIDAD, ELEMENTO_SIN_FUENTE, EC_FUERA_DE_RANGO, DATO_PENDIENTE, INCOMPATIBILIDAD, ACIDO, SODIO }

@Serializable
data class EngineWarning(val kind: WarningKind, val message: String)

@Serializable
data class ShoppingItem(val fertilizerId: String, val name: String, val grams: Double, val costPen: Double?)

@Serializable
data class FormulaResult(
    val request: FormulaRequest,
    val targetSource: String,
    val tanks: List<StockTank>,
    val balance: List<ElementBalance>,
    val estimatedEc: Double,
    val ecRange: ValueRange,
    val phTarget: Double?,
    val costPer1000LPen: Double?,
    val shoppingList: List<ShoppingItem>,
    val acidMeqPerL: Double,
    val warnings: List<EngineWarning>,
    /** Factor sugerido si la solubilidad no alcanza (null si todo está bien). */
    val suggestedFactor: ConcentrationFactor?,
    val massBalanceErrorPct: Double,
    val engineVersion: String,
    val computeMillis: Long = 0,
) {
    val doses: List<FertilizerDose> get() = tanks.flatMap { it.doses }
}
