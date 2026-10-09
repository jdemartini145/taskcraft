# Motor de fórmulas (`:core:formula-engine`)

Kotlin/JVM puro, sin Android. Entrada: `EngineInput` (meta en mg/L, análisis de agua, insumos,
factor de concentración, volúmenes). Salida: `FormulaResult`. Versión actual: **1.0.0**.

## Notación

- `e` ∈ {N-NO₃, N-NH₄, P, K, Ca, Mg, S, Fe, Mn, Zn, Cu, B, Mo}; concentraciones en mg/L.
- `x_j` = g de insumo comercial `j` por litro de solución final.
- `A[e][j]` = mg del elemento `e` que aporta 1 g/L del insumo `j`:

```
A[e][j] = %p/p(e, j) × 10 × pureza_j / 100
```

## Paso 1 · Meta neta

```
b_e = max(0, meta_e − agua_e)
agua_N-NO3 = NO₃ × 14,007 / 62,004        agua_S = SO₄ × 32,06 / 96,06
```

## Paso 5 · Ácido para bicarbonatos (se resuelve antes del NNLS)

Si `HCO₃ > límite` (61 mg/L ≈ 1 meq/L por defecto, configurable):

```
meq_ácido = (HCO₃ − HCO₃_residual) / 61,017          residual por defecto 30,5 mg/L (0,5 meq/L)
g/L de ácido comercial = meq × M_ácido / eq_por_mol / 1000 / (pureza / 100)
```

Primero se usa ácido fosfórico hasta cubrir la meta de P; el resto, ácido nítrico.
El N y P del ácido se descuentan de la meta (`b' = max(0, b − aporte_ácido)`).

## Pasos 2–3 · NNLS ponderado (macronutrientes)

```
min ‖ W (A x − b') ‖²   sujeto a   x ≥ 0
W_e = w_e / max(meta_e, 10 mg/L)
w = {N-NO₃: 4, K: 4, Ca: 4, Mg: 4, P: 2, S: 1, N-NH₄: 1}
```

El escalado por la meta convierte el residuo en error relativo; el piso de 10 mg/L evita
dividir por cero en elementos sin meta (como N-NH₄), que así se penalizan si aparecen.
Se resuelve con **Lawson–Hanson** (`Nnls.kt`); los subproblemas usan ecuaciones normales
con un término de Tikhonov de 10⁻¹² para tolerar insumos colineales.

## Paso 4 · Micronutrientes

Segundo NNLS sobre Fe, Mn, Zn, Cu, B y Mo con los insumos "micro", descontando lo que ya
aportaron las sales macro (p. ej. una mezcla NPK con micros). Piso de escala: 0,01 mg/L.
Las dosis que aportan < 0,5 % de la meta de todos sus elementos se descartan.

## Paso 6 · Tanques

| Condición del insumo | Tanque |
|---|---|
| Es ácido | Ácido |
| Contiene Ca **y** SO₄/PO₄ | Directo al reservorio (nunca en una madre) |
| Contiene Ca | A |
| Contiene S o P | B |
| Otro | Su tanque preferido (A o B) |

Regla fija: **el calcio nunca comparte madre con sulfatos ni fosfatos** (precipitan como yeso
o fosfato cálcico).

## Paso 7 · Escalado a solución madre

```
g en tanque = x_j × factor × V_tanque
mL de madre por L de reservorio = 1000 / factor
mL para el reservorio = 1000 / factor × V_reservorio
```

## Paso 8 · Solubilidad

Para cada tanque: `x_j × factor ≤ solubilidad_j(20 °C)` y `Σ (x_j × factor / sol_j) ≤ 1`
(criterio conservador por efecto de ion común). Si falla, se propone el mayor factor
(100x, 50x) que cumple. Solubilidad desconocida → advertencia "dato pendiente".

## Paso 9 · EC estimada (regla de Sonneveld)

```
EC (mS/cm) ≈ 0,1 × Σ cationes (meq/L)
meq = mg/L × carga / masa molar     (K⁺, Ca²⁺, Mg²⁺, NH₄⁺ y Na⁺ del agua)
```

## Paso 10 · Salida y verificaciones

Gramos por insumo y tanque, mL por litro, ppm entregados vs. meta, desvío %
`(entregado − meta)/meta × 100`, EC, pH objetivo (punto medio del rango), costo
`S/ por 1000 L = Σ x_j × precio_j(S/ kg)` (nulo si falta algún precio) y lista de compras.

**Balance de masa:** se recalculan los mg/L desde los gramos pesados en cada tanque
(`g_tanque / (V_tanque × factor) × A`) y se comparan con los ppm reportados.

## Ejemplo 1 · Hoagland con reactivos

Meta (Hoagland y Arnon, 1950): N 210, P 31, K 235, Ca 200, Mg 48, S 64 mg/L.
Insumos: Ca(NO₃)₂·4H₂O, KNO₃, KH₂PO₄, MgSO₄·7H₂O, quelato Fe y micros.

| Insumo | g/L | Aporta |
|---|---|---|
| Ca(NO₃)₂·4H₂O | 1,178 | Ca 200, N 139,8 |
| KNO₃ | 0,507 | K 195,9, N 70,2 |
| KH₂PO₄ | 0,136 | P 31, K 39,1 |
| MgSO₄·7H₂O | 0,487 | Mg 48, S 63,3 |

El NNLS reproduce la meta con desvío < 1 % (prueba `hoaglandWithin5Percent`).

## Ejemplo 2 · La Molina, agua de pozo con 244 mg/L de HCO₃

`meq = (244 − 30,5)/61,017 = 3,50 meq/L`. Con ácido fosfórico 85 % y nítrico 60 %, el
motor usa ~0,115 g/L de H₃PO₄ comercial (cubre el P) y ~0,262 g/L de HNO₃ 60 %
(≈ 19 mL para 100 L) y descuenta su N y P antes de resolver el resto.

## Pruebas

`./gradlew :core:formula-engine:test` — Hoagland ≤ 5 %, sin gramos negativos, balance de
masa < 0,1 %, Ca nunca con sulfatos, HCO₃ alto genera ácido, lechuga 100 L en < 1 s,
escalado por factor, solubilidad, EC, sugerencias, costos y pruebas KKT del NNLS.
