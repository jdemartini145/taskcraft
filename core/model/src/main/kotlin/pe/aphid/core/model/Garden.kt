package pe.aphid.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GrowSystemType(val label: String) {
    DWC("DWC (raíz profunda)"),
    NFT("NFT"),
    KRATKY("Kratky"),
    TORRE_VERTICAL("Torre vertical"),
    GOTEO("Goteo"),
    FLUJO_REFLUJO("Flujo y reflujo"),
    RAIZ_FLOTANTE("Raíz flotante"),
}

@Serializable
data class GrowSystem(
    val id: Long = 0,
    val name: String,
    val type: GrowSystemType,
    val volumeL: Double,
    val location: String = "",
    val photoUri: String? = null,
)

@Serializable
data class Planting(
    val id: Long = 0,
    val systemId: Long,
    val cropId: String,
    val sowingEpochDay: Long,
    val currentStage: GrowthStage = GrowthStage.PLANTULA,
    val estimatedHarvestEpochDay: Long? = null,
)

@Serializable
enum class ReadingOrigin { MANUAL, SENSOR }

@Serializable
data class Reading(
    val id: Long = 0,
    val systemId: Long,
    val timestampMillis: Long,
    val ph: Double? = null,
    val ec: Double? = null,
    val solutionTempC: Double? = null,
    val airTempC: Double? = null,
    val relativeHumidity: Double? = null,
    val volumeRemainingL: Double? = null,
    val origin: ReadingOrigin = ReadingOrigin.MANUAL,
)

@Serializable
enum class TaskKind(val label: String) {
    CAMBIO_SOLUCION("Cambio de solución"),
    LIMPIEZA("Limpieza del sistema"),
    CALIBRACION("Calibración de medidores"),
    INSPECCION_RAICES("Inspección de raíces"),
    REVISION_PLAGAS("Revisión de plagas"),
    OTRA("Otra"),
}

@Serializable
data class CareTask(
    val id: Long = 0,
    val systemId: Long?,
    val kind: TaskKind,
    val title: String,
    val dueMillis: Long,
    val repeatDays: Int? = null,
    val done: Boolean = false,
)

@Serializable
enum class AlertSeverity { INFO, ADVERTENCIA, CRITICA }

@Serializable
enum class AlertKind { TEMPERATURA_ALTA, PH_FUERA, EC_FUERA, PH_TENDENCIA, EC_TENDENCIA, LATIDO_PERDIDO, RECORDATORIO }

@Serializable
data class Alert(
    val id: Long = 0,
    val systemId: Long?,
    val kind: AlertKind,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val createdMillis: Long,
    val read: Boolean = false,
)

@Serializable
enum class Urgency { BAJA, MEDIA, ALTA }

/** Salida de diagnóstico; esquema validado igual para todos los proveedores de IA. */
@Serializable
data class DiagnosisOutput(
    val problema: String,
    val confianza: Double,
    val evidencias: List<String>,
    val acciones: List<String>,
    val urgencia: Urgency,
    val reconsultar_en_dias: Int,
)

@Serializable
data class Diagnosis(
    val id: Long = 0,
    val systemId: Long?,
    val photoId: Long?,
    val createdMillis: Long,
    val provider: String,
    val output: DiagnosisOutput,
)

@Serializable
data class Photo(val id: Long = 0, val systemId: Long?, val uri: String, val takenMillis: Long, val note: String = "")

@Serializable
data class PestRecord(
    val id: Long = 0,
    val systemId: Long?,
    val pestId: String,
    val observedMillis: Long,
    val countPerLeaf: Double? = null,
    val note: String = "",
    val nextReviewMillis: Long? = null,
)

@Serializable
data class SavedFormula(val id: Long = 0, val createdMillis: Long, val label: String, val result: FormulaResult)
