package pe.aphid.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pe.aphid.core.domain.rules.AlertEvaluator
import pe.aphid.core.domain.rules.SystemAlertContext
import pe.aphid.core.domain.rules.Trend
import pe.aphid.core.model.AlertKind
import pe.aphid.core.model.CareTask
import pe.aphid.core.model.Reading
import pe.aphid.core.model.ReadingOrigin
import pe.aphid.core.model.TaskKind
import pe.aphid.core.model.ValueRange

class AlertEvaluatorTest {
    private val h = 3_600_000L
    private val now = 1_000 * h
    private fun ctx(vararg rs: Reading, ph: ValueRange = ValueRange(5.5, 6.5), ec: ValueRange = ValueRange(1.0, 2.0), sensor: Boolean = false) =
        SystemAlertContext(1, "DWC", rs.toList(), ph, ec, sensor)

    @Test
    fun hotSolutionTriggersRootRotAlert() {
        val a = AlertEvaluator().evaluate(ctx(Reading(systemId = 1, timestampMillis = now, solutionTempC = 24.0)), now)
        assertTrue(a.any { it.kind == AlertKind.TEMPERATURA_ALTA && it.message.contains("pudrición") })
        val custom = AlertEvaluator(solutionTempMaxC = 25.0).evaluate(ctx(Reading(systemId = 1, timestampMillis = now, solutionTempC = 24.0)), now)
        assertTrue(custom.none { it.kind == AlertKind.TEMPERATURA_ALTA })
    }

    @Test
    fun outOfRangeAndTrend() {
        val out = AlertEvaluator().evaluate(ctx(Reading(systemId = 1, timestampMillis = now, ph = 7.0)), now)
        assertEquals(AlertKind.PH_FUERA, out.single().kind)
        // pH sube 0,02/h: hoy 6,3, en 24 h ≈ 6,78 > 6,5.
        val rising = (0..10).map { Reading(systemId = 1, timestampMillis = now - (10 - it) * h, ph = 6.1 + it * 0.02) }
        val trend = AlertEvaluator().evaluate(ctx(*rising.toTypedArray()), now)
        assertTrue(trend.any { it.kind == AlertKind.PH_TENDENCIA }, "$trend")
    }

    @Test
    fun missingHeartbeat() {
        val r = Reading(systemId = 1, timestampMillis = now - 20 * 60_000, ph = 6.0, origin = ReadingOrigin.SENSOR)
        val out = AlertEvaluator().evaluate(ctx(r, sensor = true), now)
        assertTrue(out.any { it.kind == AlertKind.LATIDO_PERDIDO && it.message.contains("corte de energía o bomba") })
        val fresh = r.copy(timestampMillis = now - 4 * 60_000)
        assertTrue(AlertEvaluator().evaluate(ctx(fresh, sensor = true), now).none { it.kind == AlertKind.LATIDO_PERDIDO })
    }

    @Test
    fun dueTasks() {
        val t = CareTask(id = 1, systemId = 1, kind = TaskKind.CALIBRACION, title = "Calibrar pH", dueMillis = now - 1)
        assertEquals(1, AlertEvaluator().dueTaskAlerts(listOf(t, t.copy(dueMillis = now + h)), now).size)
    }

    @Test
    fun trendSlope() {
        val pts = (0..5).map { it * h to 1.0 + it * 0.1 }
        assertEquals(0.1, Trend.slopePerHour(pts)!!, 1e-9)
        assertEquals(2.0, Trend.predict(pts, 10 * h)!!, 1e-9)
    }
}
