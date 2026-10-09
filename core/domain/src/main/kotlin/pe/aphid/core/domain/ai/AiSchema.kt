package pe.aphid.core.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import pe.aphid.core.model.ConcentrationFactor
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.GrowSystemType
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.Urgency

/** Error de validación: el dato de la IA no se usa y la UI muestra un mensaje claro. */
class AiValidationException(message: String) : Exception(message)

/**
 * Validación estricta de las respuestas de IA contra un esquema JSON fijo.
 * Si algo no cumple, se rechaza todo el objeto: la IA nunca aporta gramos.
 */
object AiSchemaValidator {

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    /** Extrae el primer objeto JSON de un texto (tolera bloques ```json). */
    fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiValidationException("La respuesta no contiene un objeto JSON.")
        return text.substring(start, end + 1)
    }

    private fun parseObject(text: String): JsonObject {
        val el: JsonElement = try {
            json.parseToJsonElement(extractJsonObject(text))
        } catch (e: AiValidationException) {
            throw e
        } catch (e: Exception) {
            throw AiValidationException("JSON inválido: ${e.message}")
        }
        return el as? JsonObject ?: throw AiValidationException("Se esperaba un objeto JSON.")
    }

    private fun JsonObject.onlyKeys(allowed: Set<String>) {
        val extra = keys - allowed
        if (extra.isNotEmpty()) throw AiValidationException("Campos no permitidos: ${extra.joinToString()}")
    }

    private fun JsonObject.str(key: String, required: Boolean): String? {
        val v = this[key] ?: if (required) throw AiValidationException("Falta el campo '$key'.") else return null
        if (v is JsonPrimitive && v.isString) return v.content
        if (!required && v.toString() == "null") return null
        throw AiValidationException("'$key' debe ser texto.")
    }

    private fun JsonObject.num(key: String, required: Boolean): Double? {
        val v = this[key] ?: if (required) throw AiValidationException("Falta el campo '$key'.") else return null
        if (!required && v.toString() == "null") return null
        val p = v as? JsonPrimitive ?: throw AiValidationException("'$key' debe ser número.")
        if (p.isString || p.booleanOrNull != null) throw AiValidationException("'$key' debe ser número.")
        return p.doubleOrNull ?: throw AiValidationException("'$key' debe ser número.")
    }

    private fun JsonObject.strList(key: String): List<String> {
        val v = this[key] as? JsonArray ?: throw AiValidationException("'$key' debe ser una lista.")
        return v.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw AiValidationException("'$key' solo admite textos.") }
    }

    /**
     * Esquema de [FormulaRequest] producido por IA:
     * `{cropId?, stage?, systemType?, reservoirVolumeL, waterHint?, concentrationFactor?, tankVolumeL?}`.
     * El cultivo debe existir en [knownCropIds].
     */
    fun validateFormulaRequest(text: String, knownCropIds: Set<String>): FormulaRequest {
        val o = parseObject(text)
        o.onlyKeys(setOf("cropId", "stage", "systemType", "reservoirVolumeL", "waterHint", "concentrationFactor", "tankVolumeL"))
        val crop = o.str("cropId", false)
        if (crop != null && crop !in knownCropIds) throw AiValidationException("Cultivo desconocido: $crop")
        val stage = o.str("stage", false)?.let { s ->
            GrowthStage.entries.firstOrNull { it.name == s } ?: throw AiValidationException("Etapa inválida: $s")
        }
        val system = o.str("systemType", false)?.let { s ->
            GrowSystemType.entries.firstOrNull { it.name == s } ?: throw AiValidationException("Sistema inválido: $s")
        }
        val volume = o.num("reservoirVolumeL", true)!!
        if (volume <= 0 || volume > MAX_VOLUME_L) throw AiValidationException("Volumen fuera de rango: $volume")
        val factor = o.num("concentrationFactor", false)?.let { f ->
            ConcentrationFactor.entries.firstOrNull { it.value.toDouble() == f } ?: throw AiValidationException("Factor inválido: $f")
        } ?: ConcentrationFactor.X100
        val tank = o.num("tankVolumeL", false) ?: DEFAULT_TANK_L
        if (tank <= 0 || tank > MAX_VOLUME_L) throw AiValidationException("Volumen de tanque fuera de rango: $tank")
        val water = o.str("waterHint", false)?.take(MAX_TEXT)
        return FormulaRequest(
            cropId = crop, stage = stage, systemType = system, reservoirVolumeL = volume,
            waterHint = water, concentrationFactor = factor, tankVolumeL = tank,
        )
    }

    /** Esquema `{problema, confianza, evidencias[], acciones[], urgencia, reconsultar_en_dias}`. */
    fun validateDiagnosis(text: String): DiagnosisOutput {
        val o = parseObject(text)
        o.onlyKeys(setOf("problema", "confianza", "evidencias", "acciones", "urgencia", "reconsultar_en_dias"))
        val problem = o.str("problema", true)!!.trim()
        if (problem.isEmpty() || problem.length > MAX_TEXT) throw AiValidationException("'problema' inválido.")
        val conf = o.num("confianza", true)!!
        if (conf !in 0.0..1.0) throw AiValidationException("'confianza' debe estar entre 0 y 1.")
        val evid = o.strList("evidencias").map { it.take(MAX_TEXT) }
        val actions = o.strList("acciones").map { it.take(MAX_TEXT) }
        if (actions.isEmpty()) throw AiValidationException("'acciones' no puede estar vacío.")
        val urg = o.str("urgencia", true)!!.uppercase().let { u ->
            Urgency.entries.firstOrNull { it.name == u } ?: throw AiValidationException("'urgencia' debe ser BAJA, MEDIA o ALTA.")
        }
        val daysEl = o["reconsultar_en_dias"] as? JsonPrimitive ?: throw AiValidationException("Falta 'reconsultar_en_dias'.")
        val days = daysEl.intOrNull ?: throw AiValidationException("'reconsultar_en_dias' debe ser entero.")
        if (days !in 0..MAX_DAYS) throw AiValidationException("'reconsultar_en_dias' fuera de rango.")
        return DiagnosisOutput(problem, conf, evid, actions, urg, days)
    }

    const val MAX_VOLUME_L = 100_000.0
    const val DEFAULT_TANK_L = 10.0
    const val MAX_TEXT = 400
    const val MAX_DAYS = 60
}
