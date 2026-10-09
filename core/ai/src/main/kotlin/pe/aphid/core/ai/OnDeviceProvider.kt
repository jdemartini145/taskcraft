package pe.aphid.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import pe.aphid.core.domain.ai.FormulaExplainer
import pe.aphid.core.domain.ai.RuleBasedRequestParser
import pe.aphid.core.model.Crop
import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.FormulaResult
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * Nivel 1, 100 % offline: parser por reglas, explicación por plantillas y clasificador
 * LiteRT con el modelo intercambiable de `assets/diagnosis_model.tflite`.
 */
@Singleton
class OnDeviceProvider @Inject constructor(@ApplicationContext private val context: Context) : AiProvider {
    override val id = "on_device"
    override val usesCloud = false

    override suspend fun isAvailable(): Boolean = true

    fun hasClassifier(): Boolean = runCatching { context.assets.open(MODEL).close() }.isSuccess

    override suspend fun parseRequest(text: String, crops: List<Crop>): FormulaRequest {
        val parsed = RuleBasedRequestParser(crops).parse(text) ?: throw AiUnavailableException("No entendí el pedido. Indica cultivo, etapa y litros.")
        return parsed.request
    }

    override suspend fun explain(result: FormulaResult): String = FormulaExplainer.explain(result)

    override suspend fun diagnose(image: File, hint: DiagnosisHint): DiagnosisOutput = withContext(Dispatchers.Default) {
        val (label, conf) = classify(image)
        DiagnosisKnowledge.output(label, conf)
    }

    /** Devuelve (etiqueta, confianza). */
    fun classify(image: File): Pair<String, Double> {
        val modelBytes = try {
            context.assets.open(MODEL).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            throw AiUnavailableException("El modelo de diagnóstico local no está instalado.")
        }
        val labels = context.assets.open(LABELS).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).put(modelBytes)
        modelBuffer.rewind()
        Interpreter(modelBuffer).use { interpreter ->
            val input = interpreter.getInputTensor(0)
            val shape = input.shape() // [1, h, w, 3]
            val h = shape[1]
            val w = shape[2]
            val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: throw AiUnavailableException("No se pudo leer la foto.")
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val isFloat = input.dataType() == DataType.FLOAT32
            val buffer = ByteBuffer.allocateDirect(h * w * 3 * if (isFloat) 4 else 1).order(ByteOrder.nativeOrder())
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (isFloat) {
                    buffer.putFloat(r / 255f); buffer.putFloat(g / 255f); buffer.putFloat(b / 255f)
                } else {
                    buffer.put(r.toByte()); buffer.put(g.toByte()); buffer.put(b.toByte())
                }
            }
            buffer.rewind()
            val outSize = interpreter.getOutputTensor(0).shape().last()
            val output = Array(1) { FloatArray(outSize) }
            interpreter.run(buffer, output)
            val probs = softmaxIfNeeded(output[0])
            val best = probs.indices.maxByOrNull { probs[it] } ?: 0
            return (labels.getOrNull(best) ?: "desconocido") to probs[best].toDouble()
        }
    }

    private fun softmaxIfNeeded(v: FloatArray): FloatArray {
        val sum = v.sum()
        if (v.all { it in 0f..1f } && sum in 0.98f..1.02f) return v
        val max = v.max()
        val e = v.map { exp((it - max).toDouble()) }
        val s = e.sum()
        return FloatArray(v.size) { (e[it] / s).toFloat() }
    }

    companion object {
        const val MODEL = "diagnosis_model.tflite"
        const val LABELS = "diagnosis_labels.txt"
    }
}
