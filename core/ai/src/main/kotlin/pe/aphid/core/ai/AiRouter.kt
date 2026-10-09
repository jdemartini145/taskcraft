package pe.aphid.core.ai

import pe.aphid.core.domain.ai.AiValidationException
import pe.aphid.core.domain.ai.RuleBasedRequestParser
import pe.aphid.core.model.Crop
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiOutcome<out T> {
    data class Ok<T>(val value: T, val providerId: String) : AiOutcome<T>
    data class Failed(val message: String) : AiOutcome<Nothing>
}

/**
 * Orquesta los proveedores por niveles. Siempre intenta primero lo local; la nube solo con
 * consentimiento explícito por foto/texto.
 */
@Singleton
class AiRouter @Inject constructor(
    val onDevice: OnDeviceProvider,
    val nano: GeminiNanoProvider,
    val cloud: ClaudeProxyProvider,
) {
    suspend fun parseRequest(text: String, crops: List<Crop>, allowCloud: Boolean): AiOutcome<FormulaRequest> {
        RuleBasedRequestParser(crops).parse(text)?.takeIf { it.missing.isEmpty() }?.let { return AiOutcome.Ok(it.request, onDevice.id) }
        val chain = buildList {
            add(nano)
            if (allowCloud) add(cloud)
        }
        return firstSuccess(chain) { it.parseRequest(text, crops) }
            ?: RuleBasedRequestParser(crops).parse(text)?.let { AiOutcome.Ok(it.request, onDevice.id) }
            ?: AiOutcome.Failed("No entendí el pedido. Indica cultivo, etapa y litros (ej.: lechuga vegetativa 100 L).")
    }

    suspend fun explain(result: FormulaResult, allowCloud: Boolean): AiOutcome<String> {
        val chain = buildList {
            add(nano)
            if (allowCloud) add(cloud)
            add(onDevice)
        }
        return firstSuccess(chain) { it.explain(result) } ?: AiOutcome.Ok(onDevice.explain(result), onDevice.id)
    }

    /**
     * Nivel 1 (clasificador local) → nivel 2 (Gemini Nano con el resultado local) →
     * nivel 3 (nube) solo si [cloudConsentForThisPhoto].
     */
    suspend fun diagnose(image: File, hint: DiagnosisHint, cloudConsentForThisPhoto: Boolean): AiOutcome<DiagnosisOutput> {
        var local: Pair<String, Double>? = null
        if (onDevice.hasClassifier()) {
            local = runCatching { onDevice.classify(image) }.onFailure { Timber.w(it) }.getOrNull()
        }
        val enriched = hint.copy(classifierLabel = local?.first, classifierConfidence = local?.second)
        if (cloudConsentForThisPhoto) {
            cloud.consentGranted = true
            try {
                return AiOutcome.Ok(cloud.diagnose(image, enriched), cloud.id)
            } catch (e: Exception) {
                Timber.w(e, "Diagnóstico en la nube falló")
                if (e is AiValidationException) return AiOutcome.Failed("La respuesta de la nube no fue válida y se descartó: ${e.message}")
            } finally {
                cloud.consentGranted = false
            }
        }
        if (nano.isAvailable()) {
            runCatching { return AiOutcome.Ok(nano.diagnose(image, enriched), nano.id) }.onFailure { Timber.w(it) }
        }
        local?.let { (label, conf) -> return AiOutcome.Ok(DiagnosisKnowledge.output(label, conf), onDevice.id) }
        return AiOutcome.Failed(
            "No hay un modelo de diagnóstico disponible sin conexión en este teléfono. Puedes aceptar el envío de esta foto a la nube.",
        )
    }

    private suspend fun <T> firstSuccess(chain: List<AiProvider>, block: suspend (AiProvider) -> T): AiOutcome.Ok<T>? {
        for (p in chain) {
            if (p == cloud) cloud.consentGranted = true
            try {
                if (p.isAvailable()) return AiOutcome.Ok(block(p), p.id)
            } catch (e: Exception) {
                Timber.w(e, "Proveedor %s falló", p.id)
            } finally {
                if (p == cloud) cloud.consentGranted = false
            }
        }
        return null
    }
}
