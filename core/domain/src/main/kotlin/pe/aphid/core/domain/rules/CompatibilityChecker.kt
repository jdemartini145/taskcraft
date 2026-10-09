package pe.aphid.core.domain.rules

import pe.aphid.core.model.CropStageTarget
import pe.aphid.core.model.ValueRange

data class CompatibilityReport(
    val compatible: Boolean,
    val phRange: ValueRange?,
    val ecRange: ValueRange?,
    val issues: List<String>,
    val pending: List<String>,
)

/** Verifica si varios cultivos pueden compartir un reservorio (rangos de pH y EC). */
object CompatibilityChecker {
    fun check(targets: List<Pair<String, CropStageTarget>>): CompatibilityReport {
        val issues = mutableListOf<String>()
        val pending = mutableListOf<String>()
        var ph: ValueRange? = ValueRange()
        var ec: ValueRange? = ValueRange()
        for ((name, t) in targets) {
            if (t.ph.isEmpty) pending += "pH de $name: dato pendiente"
            if (t.ec.isEmpty) pending += "EC de $name: dato pendiente"
            val newPh = ph?.intersect(t.ph)
            if (ph != null && newPh == null) issues += "El pH de $name (${t.ph.text()}) no se superpone con el de los demás cultivos."
            ph = newPh
            val newEc = ec?.intersect(t.ec)
            if (ec != null && newEc == null) issues += "La EC de $name (${t.ec.text()}) no se superpone con la de los demás cultivos."
            ec = newEc
        }
        return CompatibilityReport(issues.isEmpty(), ph, ec, issues, pending)
    }
}
