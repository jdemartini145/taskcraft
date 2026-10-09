package pe.aphid.core.domain.ai

import java.util.Locale
import kotlin.math.abs
import pe.aphid.core.model.Element
import pe.aphid.core.model.FormulaResult
import pe.aphid.core.model.TankGroup

/**
 * Explicación en lenguaje simple generada sin IA (plantillas). Sirve offline y como
 * respaldo cuando no hay Gemini Nano ni consentimiento para la nube.
 */
object FormulaExplainer {
    fun explain(r: FormulaResult, locale: Locale = Locale.forLanguageTag("es-PE")): String = buildString {
        fun f(v: Double, d: Int = 1) = String.format(locale, "%.${d}f", v)
        val vol = r.request.reservoirVolumeL
        appendLine("Para ${f(vol, 0)} L de solución:")
        r.tanks.forEach { t ->
            when (t.group) {
                TankGroup.DIRECTO -> appendLine("• Disuelve directo en el reservorio: ${t.doses.joinToString { it.name }}.")
                TankGroup.ACIDO -> appendLine("• Ácido: agrega ${f(t.mlForReservoir)} mL de la madre ácida, poco a poco, midiendo el pH.")
                else -> appendLine("• ${t.group.label}: agrega ${f(t.mlForReservoir)} mL (${f(t.mlPerLiterReservoir)} mL por litro).")
            }
        }
        appendLine("Mezcla primero el tanque A en el agua, revuelve, y recién después el tanque B: así el calcio no precipita con sulfatos ni fosfatos.")
        append("La EC esperada es ${f(r.estimatedEc, 2)} mS/cm")
        if (r.phTarget != null) append(" y el pH objetivo ${f(r.phTarget!!, 1)}")
        appendLine(". Mide ambos después de mezclar.")
        val off = r.balance.filter { (it.deviationPct ?: 0.0).let { d -> abs(d) > DEVIATION_NOTE_PCT } }
        if (off.isNotEmpty()) {
            val list = off.joinToString { b -> "${b.element.symbol} (${f(b.deviationPct!!, 0)} %)" }
            appendLine("Con tus insumos no se logra exactamente la meta en: $list.")
        }
        if (r.balance.any { it.element == Element.Ca && it.fromWaterPpm > 0 }) appendLine("Ya se descontó el calcio y magnesio que trae tu agua.")
        r.warnings.take(3).forEach { appendLine("Atención: ${it.message}") }
    }.trim()

    private const val DEVIATION_NOTE_PCT = 10.0
}
