package pe.aphid.core.ai

import pe.aphid.core.model.DiagnosisOutput
import pe.aphid.core.model.Urgency

/**
 * Textos cualitativos por clase del clasificador local (sin dosis numéricas).
 * El diagnóstico es orientativo: se confirma con un especialista.
 */
object DiagnosisKnowledge {
    private data class Entry(val name: String, val evidence: List<String>, val actions: List<String>, val urgency: Urgency, val recheckDays: Int)

    private val entries = mapOf(
        "sano" to Entry("Planta sin síntomas evidentes", listOf("Color y turgencia normales"), listOf("Sigue registrando pH, EC y temperatura"), Urgency.BAJA, 7),
        "pudricion_raiz" to Entry(
            "Pudrición de raíz",
            listOf("Raíces marrones, blandas o con mal olor", "Marchitez aunque haya agua"),
            listOf(
                "Baja la temperatura de la solución y mejora la aireación",
                "Retira raíces dañadas y limpia el sistema",
                "Renueva la solución y evita la luz en el reservorio",
            ),
            Urgency.ALTA, 2,
        ),
        "algas" to Entry(
            "Algas en el sistema",
            listOf("Película verde o marrón en tubos, reservorio o sustrato"),
            listOf("Cubre el reservorio y los canales de la luz", "Limpia y desinfecta entre ciclos"),
            Urgency.MEDIA, 7,
        ),
        "tip_burn" to Entry(
            "Tip burn (quemado de puntas)",
            listOf("Bordes de hojas jóvenes necrosados"),
            listOf("Mejora la circulación de aire y evita humedad muy alta", "Revisa el aporte de calcio de tu fórmula"),
            Urgency.MEDIA, 5,
        ),
        "deficiencia_ca" to Entry(
            "Posible deficiencia de calcio",
            listOf("Daño en brotes y hojas nuevas", "Pudrición apical en frutos"),
            listOf("Verifica el calcio de la fórmula y del agua", "Revisa transpiración y ventilación"),
            Urgency.MEDIA, 5,
        ),
        "deficiencia_fe" to Entry(
            "Posible deficiencia de hierro",
            listOf("Clorosis entre nervaduras en hojas nuevas"),
            listOf("Revisa que el pH no esté alto", "Verifica el quelato de hierro de tu fórmula"),
            Urgency.MEDIA, 5,
        ),
        "deficiencia_mg" to Entry(
            "Posible deficiencia de magnesio",
            listOf("Clorosis entre nervaduras en hojas viejas"),
            listOf("Verifica el magnesio de la fórmula y del agua"),
            Urgency.MEDIA, 7,
        ),
        "quemadura_sales" to Entry(
            "Quemadura por sales",
            listOf("Bordes secos en hojas viejas", "EC alta en la bitácora"),
            listOf("Baja la EC con agua sola y vuelve a medir", "Revisa la dosis de las soluciones madre"),
            Urgency.ALTA, 2,
        ),
    )

    val labels: Set<String> get() = entries.keys

    fun output(label: String, confidence: Double): DiagnosisOutput {
        val e = entries[label] ?: Entry(label, emptyList(), listOf("Consulta con un especialista"), Urgency.MEDIA, 3)
        return DiagnosisOutput(e.name, confidence.coerceIn(0.0, 1.0), e.evidence, e.actions, e.urgency, e.recheckDays)
    }
}
