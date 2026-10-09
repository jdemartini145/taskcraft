package pe.aphid.core.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import pe.aphid.core.domain.ai.AiSchemaValidator
import pe.aphid.core.model.Crop
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nivel 2: Gemini Nano mediante ML Kit GenAI Prompt API. Se consulta la disponibilidad
 * del dispositivo antes de usarlo; todo corre en el teléfono (sin red).
 */
@Singleton
class GeminiNanoProvider @Inject constructor() : AiProvider {
    override val id = "gemini_nano"
    override val usesCloud = false

    private var model: GenerativeModel? = null

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    override suspend fun isAvailable(): Boolean = try {
        client().checkStatus() == FeatureStatus.AVAILABLE
    } catch (t: Throwable) {
        Timber.d(t, "Gemini Nano no disponible")
        false
    }

    private suspend fun generate(prompt: String): String {
        if (!isAvailable()) throw AiUnavailableException("Gemini Nano no está disponible en este dispositivo.")
        val response = client().generateContent(prompt)
        return response.candidates.firstOrNull()?.text ?: throw AiUnavailableException("Gemini Nano no devolvió texto.")
    }

    override suspend fun parseRequest(text: String, crops: List<Crop>): FormulaRequest =
        AiSchemaValidator.validateFormulaRequest(generate(Prompts.parse(text, crops)), crops.map { it.id }.toSet())

    override suspend fun explain(result: FormulaResult): String = generate(Prompts.explain(result)).trim().take(MAX_EXPLANATION)

    /** Nano trabaja sobre el resultado del clasificador local y las notas del usuario (texto). */
    override suspend fun diagnose(image: File, hint: DiagnosisHint): DiagnosisOutput =
        AiSchemaValidator.validateDiagnosis(generate(Prompts.diagnose(hint)))

    private companion object { const val MAX_EXPLANATION = 1500 }
}
