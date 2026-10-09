package pe.aphid.core.domain

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pe.aphid.core.domain.ai.AiSchemaValidator
import pe.aphid.core.domain.ai.AiValidationException
import pe.aphid.core.domain.ai.FormulaExplainer
import pe.aphid.core.domain.ai.RuleBasedRequestParser
import pe.aphid.core.engine.EngineInput
import pe.aphid.core.engine.FormulaEngine
import pe.aphid.core.model.Crop
import pe.aphid.core.model.Element
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.GrowthStage
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.SeedParser
import pe.aphid.core.model.Urgency

class AiTest {
    private val seedDir = File(System.getProperty("aphid.seedDir") ?: "../database/src/main/assets")
    private val crops = SeedParser.crops(File(seedDir, "crops_v2.json").readText()).crops
    private val parser = RuleBasedRequestParser(crops)

    @Test
    fun parsesSpecExample() {
        val p = parser.parse("Fórmula para fresa en fructificación, 200 L, agua de pozo")!!
        assertEquals("fresa", p.request.cropId)
        assertEquals(GrowthStage.FRUCTIFICACION, p.request.stage)
        assertEquals(200.0, p.request.reservoirVolumeL, 1e-9)
        assertEquals("agua de pozo", p.request.waterHint)
        assertTrue(p.missing.isEmpty())
    }

    @Test
    fun parsesVariants() {
        val p = parser.parse("lechuga vegetativa 1,5 m3 a 200x en NFT")!!
        assertEquals("lechuga", p.request.cropId)
        assertEquals(1500.0, p.request.reservoirVolumeL, 1e-9)
        assertEquals(200, p.request.concentrationFactor.value)
        assertEquals("cilantro", "cilantro")
        assertEquals("culantro", parser.parse("cilantro 20 litros")!!.request.cropId)
        assertEquals(listOf("etapa"), parser.parse("cilantro 20 litros")!!.missing)
        assertEquals("cebolla_china", parser.parse("cebolla china en plántula 50 L")!!.request.cropId)
        assertNull(parser.parse("hola"))
    }

    @Test
    fun validatesFormulaRequestJson() {
        val ok = AiSchemaValidator.validateFormulaRequest(
            "```json\n{\"cropId\":\"fresa\",\"stage\":\"FRUCTIFICACION\",\"reservoirVolumeL\":200,\"waterHint\":\"pozo\"}\n```",
            crops.map { it.id }.toSet(),
        )
        assertEquals(200.0, ok.reservoirVolumeL, 1e-9)
        val ids = crops.map { it.id }.toSet()
        assertThrows<AiValidationException> { AiSchemaValidator.validateFormulaRequest("{\"cropId\":\"marihuana\",\"reservoirVolumeL\":10}", ids) }
        assertThrows<AiValidationException> { AiSchemaValidator.validateFormulaRequest("{\"cropId\":\"fresa\",\"reservoirVolumeL\":-5}", ids) }
        assertThrows<AiValidationException> { AiSchemaValidator.validateFormulaRequest("{\"reservoirVolumeL\":10,\"gramos\":{\"x\":1}}", ids) }
        assertThrows<AiValidationException> { AiSchemaValidator.validateFormulaRequest("no es json", ids) }
        assertThrows<AiValidationException> { AiSchemaValidator.validateFormulaRequest("{\"reservoirVolumeL\":\"diez\"}", ids) }
    }

    @Test
    fun validatesDiagnosisJson() {
        val d = AiSchemaValidator.validateDiagnosis(
            """{"problema":"Deficiencia de Fe","confianza":0.7,"evidencias":["clorosis internerval en hojas nuevas"],"acciones":["Revisar pH"],"urgencia":"media","reconsultar_en_dias":5}""",
        )
        assertEquals(Urgency.MEDIA, d.urgencia)
        assertThrows<AiValidationException> {
            AiSchemaValidator.validateDiagnosis("""{"problema":"x","confianza":1.7,"evidencias":[],"acciones":["a"],"urgencia":"ALTA","reconsultar_en_dias":1}""")
        }
        assertThrows<AiValidationException> {
            AiSchemaValidator.validateDiagnosis("""{"problema":"x","confianza":0.5,"evidencias":[],"acciones":[],"urgencia":"ALTA","reconsultar_en_dias":1}""")
        }
    }

    @Test
    fun explainerMentionsTanks() {
        val ferts = SeedParser.fertilizers(File(seedDir, "fertilizers_v1.json").readText()).fertilizers
        val ids = listOf("nitrato_calcio", "nitrato_potasio", "fosfato_monopotasico", "sulfato_magnesio")
        val r = FormulaEngine().calculate(
            EngineInput(
                FormulaRequest(reservoirVolumeL = 100.0, fertilizerIds = ids),
                NutrientProfile.of(Element.N_NO3 to 150.0, Element.K to 200.0, Element.Ca to 150.0, Element.Mg to 40.0, Element.P to 40.0),
                "test",
                fertilizers = ferts.filter { it.id in ids },
            ),
        )
        val text = FormulaExplainer.explain(r)
        assertTrue(text.contains("Tanque A") && text.contains("calcio"), text)
        assertTrue(Crop("a", "A", "a", "f").id == "a")
    }
}
