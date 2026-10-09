package pe.aphid.core.ai

import pe.aphid.core.model.Crop
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import java.io.File

/** El proveedor no está disponible en este dispositivo o sin consentimiento. */
class AiUnavailableException(message: String) : Exception(message)

/** Contexto opcional para el diagnóstico: resultado del clasificador y notas del usuario. */
data class DiagnosisHint(
    val classifierLabel: String? = null,
    val classifierConfidence: Double? = null,
    val userNotes: String = "",
    val cropName: String? = null,
)

/**
 * Proveedor de IA. Reglas comunes:
 * - Toda respuesta se valida con [pe.aphid.core.domain.ai.AiSchemaValidator]; si falla se lanza
 *   [pe.aphid.core.domain.ai.AiValidationException] y el dato no se usa.
 * - La IA nunca calcula gramos: solo convierte texto a [FormulaRequest] y explica resultados.
 */
interface AiProvider {
    val id: String
    val usesCloud: Boolean

    suspend fun isAvailable(): Boolean

    suspend fun parseRequest(text: String, crops: List<Crop>): FormulaRequest

    suspend fun explain(result: FormulaResult): String

    suspend fun diagnose(image: File, hint: DiagnosisHint): DiagnosisOutput
}

internal object Prompts {
    fun parse(text: String, crops: List<Crop>): String = """
        Convierte el pedido del usuario en JSON con este esquema exacto y nada más:
        {"cropId": string|null, "stage": "PLANTULA"|"VEGETATIVA"|"FLORACION"|"FRUCTIFICACION"|null,
         "systemType": "DWC"|"NFT"|"KRATKY"|"TORRE_VERTICAL"|"GOTEO"|"FLUJO_REFLUJO"|"RAIZ_FLOTANTE"|null,
         "reservoirVolumeL": number, "waterHint": string|null, "concentrationFactor": 50|100|200|null, "tankVolumeL": number|null}
        cropId debe ser uno de: ${crops.joinToString { it.id }}.
        No calcules cantidades de fertilizante. Responde solo el JSON.
        Pedido: "$text"
    """.trimIndent()

    fun explain(result: FormulaResult): String = buildString {
        appendLine("Explica en español sencillo (máximo 6 frases) cómo preparar esta solución nutritiva.")
        appendLine("No cambies ni recalcules ninguna cantidad; usa solo los números dados.")
        appendLine("Volumen: ${result.request.reservoirVolumeL} L. EC estimada: ${"%.2f".format(result.estimatedEc)} mS/cm.")
        result.tanks.forEach { t ->
            appendLine("${t.group.label}: ${"%.1f".format(t.mlForReservoir)} mL; " + t.doses.joinToString { "${it.name} ${"%.1f".format(it.gramsInStockTank)} g" })
        }
        result.warnings.forEach { appendLine("Advertencia: ${it.message}") }
    }

    fun diagnose(hint: DiagnosisHint): String = """
        Eres un asistente de hidroponía. Devuelve SOLO JSON con el esquema:
        {"problema": string, "confianza": number 0..1, "evidencias": [string], "acciones": [string],
         "urgencia": "BAJA"|"MEDIA"|"ALTA", "reconsultar_en_dias": integer}
        Problemas posibles: pudrición de raíz, algas, tip burn, deficiencia de Ca, deficiencia de Fe,
        deficiencia de Mg, quemadura por sales, planta sana.
        ${hint.cropName?.let { "Cultivo: $it." } ?: ""}
        ${hint.classifierLabel?.let { "Clasificador local: $it (confianza ${hint.classifierConfidence})." } ?: ""}
        Notas del usuario: ${hint.userNotes.ifBlank { "sin notas" }}
        No recomiendes dosis numéricas de fertilizantes.
    """.trimIndent()
}
