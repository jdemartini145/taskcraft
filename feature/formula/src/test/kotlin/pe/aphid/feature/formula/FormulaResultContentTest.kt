package pe.aphid.feature.formula

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.aphid.core.designsystem.theme.AphidTheme
import pe.aphid.core.engine.EngineInput
import pe.aphid.core.engine.FormulaEngine
import pe.aphid.core.model.Element
import pe.aphid.core.model.Fertilizer
import pe.aphid.core.model.FormulaRequest
import pe.aphid.core.model.NutrientProfile
import pe.aphid.core.model.TankGroup

/** Flujo crítico: la pantalla 2 muestra gramos por tanque y permite exportar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-rPE")
class FormulaResultContentTest {
    @get:Rule val compose = createComposeRule()

    private val ferts = listOf(
        Fertilizer("cn", "Nitrato de calcio", "Ca(NO3)2", composition = mapOf(Element.N_NO3 to 14.4, Element.N_NH4 to 1.1, Element.Ca to 19.0), defaultTank = TankGroup.A),
        Fertilizer("kn", "Nitrato de potasio", "KNO3", composition = mapOf(Element.N_NO3 to 13.85, Element.K to 38.67), defaultTank = TankGroup.B),
        Fertilizer("mkp", "Fosfato monopotásico", "KH2PO4", composition = mapOf(Element.P to 22.76, Element.K to 28.73), defaultTank = TankGroup.B),
        Fertilizer("ms", "Sulfato de magnesio", "MgSO4·7H2O", composition = mapOf(Element.Mg to 9.86, Element.S to 13.0), defaultTank = TankGroup.B),
    )

    @Test
    fun showsTanksAndExports() {
        val result = FormulaEngine().calculate(
            EngineInput(
                FormulaRequest(cropId = "lechuga", reservoirVolumeL = 100.0, fertilizerIds = ferts.map { it.id }),
                NutrientProfile.of(Element.N_NO3 to 190.0, Element.P to 35.0, Element.K to 210.0, Element.Ca to 150.0, Element.Mg to 45.0, Element.S to 70.0),
                "La Molina",
                fertilizers = ferts,
            ),
        )
        var pdf = false
        compose.setContent {
            AphidTheme {
                FormulaResultContent(
                    result = result, title = "Lechuga", explanation = null, explaining = false, saved = false,
                    onExplain = {}, onSave = {}, onPdf = { pdf = true }, onCsv = {},
                )
            }
        }
        compose.onNodeWithText("Tanque A (calcio y hierro)").assertIsDisplayed()
        compose.onNodeWithText("Nitrato de calcio", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Exportar PDF").performScrollTo().performClick()
        assertTrue(pdf)
    }
}
