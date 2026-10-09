package pe.aphid.core.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * Mínimos cuadrados no negativos (Lawson & Hanson, 1974, cap. 23):
 * minimiza ||A·x − b||² sujeto a x ≥ 0.
 *
 * Pensado para matrices pequeñas (≤ 20 × 20). Los subproblemas sin restricción se
 * resuelven por ecuaciones normales con un término de Tikhonov despreciable
 * para tolerar columnas colineales (dos insumos casi iguales).
 */
object Nnls {

    data class Result(val x: DoubleArray, val residualNorm: Double, val iterations: Int)

    fun solve(a: Array<DoubleArray>, b: DoubleArray, tol: Double = 1e-10, maxIter: Int = 0): Result {
        val m = a.size
        require(m == b.size) { "Dimensiones incompatibles" }
        val n = if (m == 0) 0 else a[0].size
        if (n == 0) return Result(DoubleArray(0), norm(b), 0)
        val limit = if (maxIter > 0) maxIter else 30 * n
        val x = DoubleArray(n)
        val passive = BooleanArray(n)
        var iter = 0
        var w = gradient(a, b, x)
        while (iter < limit) {
            // Columna con mayor gradiente positivo fuera del conjunto pasivo.
            var t = -1
            var best = tol
            for (j in 0 until n) if (!passive[j] && w[j] > best) { best = w[j]; t = j }
            if (t < 0) break
            passive[t] = true
            var z = solvePassive(a, b, passive)
            // Bucle interno: mantener factibilidad.
            while (iter < limit && (0 until n).any { passive[it] && z[it] <= tol }) {
                iter++
                var alpha = Double.MAX_VALUE
                for (j in 0 until n) {
                    if (passive[j] && z[j] <= tol) {
                        val denom = x[j] - z[j]
                        if (denom > 0) alpha = minOf(alpha, x[j] / denom)
                    }
                }
                if (alpha == Double.MAX_VALUE) alpha = 0.0
                for (j in 0 until n) x[j] += alpha * (z[j] - x[j])
                for (j in 0 until n) if (passive[j] && abs(x[j]) <= tol) { passive[j] = false; x[j] = 0.0 }
                z = solvePassive(a, b, passive)
            }
            for (j in 0 until n) x[j] = if (passive[j]) max(0.0, z[j]) else 0.0
            w = gradient(a, b, x)
            iter++
        }
        val r = residual(a, b, x)
        return Result(x, norm(r), iter)
    }

    private fun gradient(a: Array<DoubleArray>, b: DoubleArray, x: DoubleArray): DoubleArray {
        val r = residual(a, b, x)
        val n = x.size
        return DoubleArray(n) { j -> a.indices.sumOf { i -> a[i][j] * r[i] } }
    }

    private fun residual(a: Array<DoubleArray>, b: DoubleArray, x: DoubleArray): DoubleArray =
        DoubleArray(b.size) { i -> b[i] - x.indices.sumOf { j -> a[i][j] * x[j] } }

    private fun norm(v: DoubleArray) = kotlin.math.sqrt(v.sumOf { it * it })

    /** Resuelve min ||A_P z − b|| sin restricciones; z_j = 0 fuera de P. */
    private fun solvePassive(a: Array<DoubleArray>, b: DoubleArray, passive: BooleanArray): DoubleArray {
        val n = passive.size
        val idx = (0 until n).filter { passive[it] }
        val k = idx.size
        val z = DoubleArray(n)
        if (k == 0) return z
        val ata = Array(k) { DoubleArray(k) }
        val atb = DoubleArray(k)
        for (p in 0 until k) {
            for (q in 0 until k) ata[p][q] = a.indices.sumOf { i -> a[i][idx[p]] * a[i][idx[q]] }
            atb[p] = a.indices.sumOf { i -> a[i][idx[p]] * b[i] }
        }
        val trace = (0 until k).sumOf { ata[it][it] }
        val ridge = 1e-12 * max(trace / k, 1e-300)
        for (p in 0 until k) ata[p][p] += ridge
        val sol = gaussSolve(ata, atb)
        for (p in 0 until k) z[idx[p]] = sol[p]
        return z
    }

    /** Eliminación gaussiana con pivoteo parcial. */
    internal fun gaussSolve(m: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val n = v.size
        val a = Array(n) { m[it].copyOf() }
        val b = v.copyOf()
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-300) continue
            if (piv != col) {
                val tmp = a[piv]; a[piv] = a[col]; a[col] = tmp
                val tb = b[piv]; b[piv] = b[col]; b[col] = tb
            }
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                if (f == 0.0) continue
                for (c in col until n) a[r][c] -= f * a[col][c]
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            if (abs(a[r][r]) < 1e-300) { x[r] = 0.0; continue }
            var s = b[r]
            for (c in r + 1 until n) s -= a[r][c] * x[c]
            x[r] = s / a[r][r]
        }
        return x
    }
}
