package pe.aphid.feature.log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox
import pe.aphid.core.designsystem.theme.OptimalBand
import pe.aphid.core.model.ValueRange

/**
 * Serie temporal con banda de zona óptima (Vico 2.x, `HorizontalBox`).
 * @param points pares (horas desde la primera lectura, valor).
 */
@Composable
fun RangeChart(points: List<Pair<Double, Double>>, band: ValueRange, description: String, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        if (points.size >= 2) {
            producer.runTransaction { lineSeries { series(points.map { it.first }, points.map { it.second }) } }
        }
    }
    val box = rememberShapeComponent(fill = fill(OptimalBand))
    val lo = band.min
    val hi = band.max
    val decorations = remember(lo, hi, box) {
        if (lo != null || hi != null) {
            val min = lo ?: (points.minOfOrNull { it.second } ?: 0.0).coerceAtMost(hi ?: 0.0)
            val max = hi ?: (points.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(lo ?: 0.0)
            listOf(HorizontalBox(y = { min..max }, box = box))
        } else {
            emptyList()
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
            decorations = decorations,
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description },
    )
}
