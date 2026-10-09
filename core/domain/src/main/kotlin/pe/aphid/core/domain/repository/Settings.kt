package pe.aphid.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class TdsScale(val factor: Int) { PPM_500(500), PPM_700(700) }

@Serializable
data class UserSettings(
    val onboardingDone: Boolean = false,
    val tdsScale: TdsScale = TdsScale.PPM_500,
    /** Umbral de temperatura de solución (°C) para alerta de pudrición de raíz. */
    val solutionTempMaxC: Double = 22.0,
    val hco3LimitMgL: Double = 61.0,
    val hco3ResidualMgL: Double = 30.5,
    val alertsEnabled: Boolean = true,
    val telemetryEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    /** Consentimiento general (Ley 29733) para usar IA en la nube; además se pide por foto. */
    val cloudAiConsent: Boolean = false,
    val currencyCode: String = "PEN",
    val sensorsEnabled: Boolean = false,
    val sensorHeartbeatMinutes: Int = 5,
    val sensorTimeoutMinutes: Int = 15,
    val proActive: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
