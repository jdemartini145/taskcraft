package pe.aphid.core.ai

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import pe.aphid.core.domain.ai.AiSchemaValidator
import pe.aphid.core.model.Crop
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult

@Serializable
data class ProxyRequest(val task: String, val prompt: String, val imageBase64: String? = null, val locale: String = "es-PE")

@Serializable
data class ProxyResponse(val text: String)

/**
 * Nivel 3: Claude mediante un backend proxy propio (Cloudflare Worker / Cloud Functions).
 * - El APK no contiene ninguna clave: el proxy guarda la clave y aplica límite de uso.
 * - Cada instalación se identifica con un token aleatorio (no es un secreto).
 * - Solo se llama con consentimiento explícito (Ley 29733); el llamador debe pasar `consent = true`.
 */
@Singleton
class ClaudeProxyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
) : AiProvider {
    override val id = "claude_proxy"
    override val usesCloud = true

    @Volatile var consentGranted: Boolean = false

    private val baseUrl: String get() = BuildConfig.AI_PROXY_URL.trimEnd('/')

    override suspend fun isAvailable(): Boolean = baseUrl.startsWith("https://")

    private fun installationToken(): String {
        val prefs = context.getSharedPreferences("aphid_install", Context.MODE_PRIVATE)
        return prefs.getString("token", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("token", it).apply() }
    }

    private suspend fun call(task: String, prompt: String, image: File? = null): String {
        if (!consentGranted) throw AiUnavailableException("Necesitas aceptar el envío a la nube para usar esta opción.")
        if (!isAvailable()) throw AiUnavailableException("El servicio en la nube no está configurado.")
        val body = ProxyRequest(
            task = task,
            prompt = prompt,
            imageBase64 = image?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) },
        )
        val resp = http.post("$baseUrl/v1/$task") {
            contentType(ContentType.Application.Json)
            header("X-Install-Token", installationToken())
            setBody(body)
        }
        if (!resp.status.isSuccess()) throw AiUnavailableException("El servicio en la nube respondió ${resp.status.value}.")
        return resp.body<ProxyResponse>().text
    }

    override suspend fun parseRequest(text: String, crops: List<Crop>): FormulaRequest =
        AiSchemaValidator.validateFormulaRequest(call("parse", Prompts.parse(text, crops)), crops.map { it.id }.toSet())

    override suspend fun explain(result: FormulaResult): String = call("explain", Prompts.explain(result)).trim()

    override suspend fun diagnose(image: File, hint: DiagnosisHint): DiagnosisOutput =
        AiSchemaValidator.validateDiagnosis(call("diagnose", Prompts.diagnose(hint), image))
}
