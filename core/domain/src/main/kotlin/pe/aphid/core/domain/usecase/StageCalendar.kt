package pe.aphid.core.domain.usecase

import pe.aphid.core.model.Crop
import pe.aphid.core.model.GrowthStage

data class StageWindow(val stage: GrowthStage, val startEpochDay: Long, val endEpochDay: Long)

/** Calendario de etapas a partir de la siembra. Si faltan días por etapa (dato pendiente) devuelve null. */
object StageCalendar {
    fun windows(crop: Crop, sowingEpochDay: Long): List<StageWindow>? {
        var start = sowingEpochDay
        val out = mutableListOf<StageWindow>()
        for (stage in GrowthStage.entries) {
            val days = crop.daysPerStage[stage] ?: return null
            out += StageWindow(stage, start, start + days)
            start += days
        }
        return out
    }

    fun stageAt(crop: Crop, sowingEpochDay: Long, todayEpochDay: Long): GrowthStage? =
        windows(crop, sowingEpochDay)?.firstOrNull { todayEpochDay < it.endEpochDay }?.stage

    fun harvestEpochDay(crop: Crop, sowingEpochDay: Long): Long? = windows(crop, sowingEpochDay)?.last()?.endEpochDay
}
