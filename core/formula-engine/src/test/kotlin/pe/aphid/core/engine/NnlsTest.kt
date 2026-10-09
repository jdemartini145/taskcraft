package pe.aphid.core.engine

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NnlsTest {
    @Test
    fun exactSolutionRecovered() {
        val a = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 2.0), doubleArrayOf(1.0, 1.0))
        val b = doubleArrayOf(1.0, 4.0, 3.0)
        val r = Nnls.solve(a, b)
        assertArrayEquals(doubleArrayOf(1.0, 2.0), r.x, 1e-9)
        assertEquals(0.0, r.residualNorm, 1e-9)
    }

    @Test
    fun negativeUnconstrainedSolutionIsClamped() {
        // Sin restricción la solución sería x2 < 0.
        val a = arrayOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 2.0))
        val b = doubleArrayOf(1.0, 0.0)
        val r = Nnls.solve(a, b)
        assertTrue(r.x.all { it >= 0 })
        assertEquals(0.0, r.x[1], 1e-12)
        assertEquals(0.5, r.x[0], 1e-9)
    }

    @Test
    fun randomProblemsSatisfyKkt() {
        val rnd = Random(42)
        repeat(200) {
            val m = rnd.nextInt(3, 13)
            val n = rnd.nextInt(2, 15)
            val a = Array(m) { DoubleArray(n) { if (rnd.nextDouble() < 0.4) 0.0 else rnd.nextDouble() * 100 } }
            val b = DoubleArray(m) { rnd.nextDouble() * 200 - 20 }
            val r = Nnls.solve(a, b)
            assertTrue(r.x.all { it >= 0 })
            // KKT: gradiente w = Aᵀ(b − Ax) ≤ tol, y w_j ≈ 0 donde x_j > 0.
            val res = DoubleArray(m) { i -> b[i] - (0 until n).sumOf { j -> a[i][j] * r.x[j] } }
            val scale = 1e-6 * (1 + a.sumOf { row -> row.sumOf { it * it } })
            for (j in 0 until n) {
                val w = (0 until m).sumOf { i -> a[i][j] * res[i] }
                assertTrue(w <= scale, "w[$j]=$w")
                if (r.x[j] > 1e-9) assertEquals(0.0, w, scale)
            }
        }
    }
}
